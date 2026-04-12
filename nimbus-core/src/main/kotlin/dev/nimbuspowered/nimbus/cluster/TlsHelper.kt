package dev.nimbuspowered.nimbus.cluster

import io.ktor.network.tls.certificates.*
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.net.InetAddress
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.io.path.exists

object TlsHelper {

    private val logger = LoggerFactory.getLogger(TlsHelper::class.java)

    private const val DEFAULT_ALIAS = "nimbus-cluster"

    /**
     * Loads a JKS or PKCS12 keystore from disk.
     * Auto-detects format from file extension (.p12/.pfx -> PKCS12, else JKS).
     */
    fun loadKeyStore(path: Path, password: String): KeyStore {
        val type = when {
            path.toString().endsWith(".p12", ignoreCase = true) ||
            path.toString().endsWith(".pfx", ignoreCase = true) -> "PKCS12"
            else -> "JKS"
        }
        val ks = KeyStore.getInstance(type)
        FileInputStream(path.toFile()).use { fis ->
            ks.load(fis, password.toCharArray())
        }
        return ks
    }

    /**
     * Ensures a keystore exists at [path]. If not, generates a self-signed certificate.
     * Returns a pair of (KeyStore, password).
     */
    fun ensureKeyStore(
        path: Path,
        configuredPassword: String,
        bind: String,
        extraSans: List<String> = emptyList()
    ): Pair<KeyStore, String> {
        if (path.exists()) {
            val password = configuredPassword.ifBlank {
                logger.warn("Keystore exists but no password configured — use cluster.keystore_password or NIMBUS_CLUSTER_KEYSTORE_PASSWORD")
                generateSecurePassword()
            }
            val ks = loadKeyStore(path, password)
            logCertificateFingerprint(ks)
            return ks to password
        }

        // Auto-generate self-signed certificate
        logger.info("No keystore found at {} — generating self-signed certificate...", path)

        // H7 fix: auto-generate a secure random password instead of using a hardcoded default
        val password = configuredPassword.ifBlank { generateSecurePassword().also {
            logger.info("Generated random keystore password — set cluster.keystore_password in nimbus.toml or NIMBUS_CLUSTER_KEYSTORE_PASSWORD env var to persist it")
        }}

        val ipRegex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
        val extraDomains = extraSans.filter { !ipRegex.matches(it) }
        val extraIps = extraSans.filter { ipRegex.matches(it) }

        val keyStore = buildKeyStore {
            certificate(DEFAULT_ALIAS) {
                this.password = password
                daysValid = 3650 // 10 years
                keySizeInBits = 4096
                subject = X500Principal("CN=nimbus-cluster, O=Nimbus, L=Cloud")
                domains = buildList {
                    add("localhost")
                    // Add the local machine's hostname as a DNS SAN so agents can
                    // connect by hostname without hostname-verification failures.
                    try {
                        val localHost = InetAddress.getLocalHost().hostName
                        if (localHost.isNotBlank() && localHost != "localhost" && !ipRegex.matches(localHost)) {
                            add(localHost)
                        }
                    } catch (_: Exception) {}
                    if (bind != "0.0.0.0" && bind != "127.0.0.1" && bind != "localhost" && !ipRegex.matches(bind)) {
                        add(bind)
                    }
                    extraDomains.forEach { add(it) }
                }
                ipAddresses = buildList {
                    add(InetAddress.getByName("127.0.0.1"))
                    if (bind != "127.0.0.1" && bind != "localhost" && bind != "0.0.0.0") {
                        try {
                            add(InetAddress.getByName(bind))
                        } catch (_: Exception) {}
                    }
                    try {
                        add(InetAddress.getLocalHost())
                    } catch (_: Exception) {}
                    extraIps.forEach {
                        try { add(InetAddress.getByName(it)) } catch (_: Exception) {}
                    }
                }
            }
        }

        // Save to disk
        path.parent?.toFile()?.mkdirs()
        keyStore.saveToFile(path.toFile(), password)
        logger.info("Self-signed keystore saved to {}", path)
        logCertificateFingerprint(keyStore)

        return keyStore to password
    }

    /**
     * Logs the SHA-256 fingerprint of the first certificate in the keystore.
     * Agents can use this to verify the server identity.
     */
    private fun logCertificateFingerprint(keyStore: KeyStore) {
        try {
            val info = getCertInfo(keyStore) ?: return
            logger.info("Cluster TLS certificate fingerprint (SHA-256): {}", info.fingerprint)
            logger.info("Certificate valid until: {}", info.validUntil)
        } catch (e: Exception) {
            logger.debug("Could not read certificate fingerprint: {}", e.message)
        }
    }

    /**
     * Extracts fingerprint + PEM + SANs + validity from the keystore's leaf certificate.
     * Returns null if the keystore has no usable certificate.
     */
    fun getCertInfo(keyStore: KeyStore): CertInfo? {
        val alias = keyStore.aliases().toList().firstOrNull() ?: return null
        val cert = keyStore.getCertificate(alias) as? X509Certificate ?: return null

        val sha256 = MessageDigest.getInstance("SHA-256")
        val fingerprint = sha256.digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

        val pem = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            val encoder = java.util.Base64.getEncoder()
            val b64 = encoder.encodeToString(cert.encoded)
            // wrap at 64 chars/line per PEM convention
            b64.chunked(64).forEach { append(it).append('\n') }
            append("-----END CERTIFICATE-----\n")
        }

        val sans: List<String> = try {
            cert.subjectAlternativeNames?.mapNotNull { entry ->
                // entry is a list: [type, value]. type 2 = DNS name, type 7 = IP address.
                val type = entry?.getOrNull(0) as? Int
                val value = entry?.getOrNull(1)?.toString()
                when (type) {
                    2 -> "DNS:$value"
                    7 -> "IP:$value"
                    else -> value
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return CertInfo(
            fingerprint = fingerprint,
            pemEncoded = pem,
            validUntil = cert.notAfter.toInstant().toString(),
            sans = sans
        )
    }

    data class CertInfo(
        val fingerprint: String,
        val pemEncoded: String,
        val validUntil: String,
        val sans: List<String>
    )

    /** Generates a cryptographically secure random password for keystore protection. */
    private fun generateSecurePassword(length: Int = 32): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = java.security.SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
