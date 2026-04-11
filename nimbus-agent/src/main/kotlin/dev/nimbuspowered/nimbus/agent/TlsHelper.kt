package dev.nimbuspowered.nimbus.agent

import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object TlsHelper {

    private val logger = LoggerFactory.getLogger(TlsHelper::class.java)

    /**
     * Computes the SHA-256 fingerprint of a certificate as uppercase hex with colons
     * (e.g. "AA:BB:CC:..."). Matches the format logged by the controller.
     */
    fun fingerprintOf(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
    }

    /**
     * Returns a TrustManager that trusts only certificates whose SHA-256 fingerprint
     * matches [expectedFingerprint]. Skips hostname verification and CA path validation
     * entirely — fingerprint pinning is the sole trust anchor.
     *
     * Logs the actually-seen fingerprint on every check (succeed or fail) so users can
     * copy it into agent.toml when setting up trust.
     */
    fun pinnedTrustManager(expectedFingerprint: String): X509TrustManager {
        val normalizedExpected = expectedFingerprint.trim().uppercase()
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw CertificateException("Server presented no certificate chain")
                }
                val leaf = chain[0]
                val actual = fingerprintOf(leaf).uppercase()
                logger.info("Server certificate fingerprint: {}", actual)
                if (actual != normalizedExpected) {
                    logger.error("Fingerprint mismatch!")
                    logger.error("  Expected: {}", normalizedExpected)
                    logger.error("  Got:      {}", actual)
                    logger.error("  If the controller cert was rotated, re-run the agent setup wizard:")
                    logger.error("    java -jar nimbus-agent.jar --setup")
                    throw CertificateException(
                        "Controller cert fingerprint mismatch — expected $normalizedExpected but got $actual"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }


    /**
     * Loads a trust store from disk, or returns null to use the system default.
     */
    fun loadTrustStore(path: String, password: String): KeyStore? {
        if (path.isBlank()) return null
        val type = when {
            path.endsWith(".p12", ignoreCase = true) ||
            path.endsWith(".pfx", ignoreCase = true) -> "PKCS12"
            else -> "JKS"
        }
        val ks = KeyStore.getInstance(type)
        FileInputStream(path).use { fis ->
            ks.load(fis, password.toCharArray())
        }
        return ks
    }

    /**
     * Creates a TrustManager that trusts all certificates.
     * Only for development/testing — NOT for production use.
     */
    fun trustAllManager(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * Creates a TrustManager from the given trust store, or falls back to system default.
     */
    fun trustManagerFor(trustStore: KeyStore?): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(trustStore) // null = system default
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
