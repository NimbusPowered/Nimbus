package dev.nimbuspowered.nimbus.cli

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Mirror of the controller-side `DoctorReport` DTO — kept minimal and resilient. */
@Serializable
private data class CliDoctorReport(
    val sections: List<CliDoctorSection> = emptyList(),
    val warnCount: Int = 0,
    val failCount: Int = 0,
    val status: String = "ok",
)

@Serializable
private data class CliDoctorSection(
    val name: String,
    val findings: List<CliDoctorFinding> = emptyList(),
)

@Serializable
private data class CliDoctorFinding(
    val level: String,
    val message: String,
    val hint: String? = null,
)

private const val RESET = "\u001B[0m"
private const val RED = "\u001B[31m"
private const val GREEN = "\u001B[32m"
private const val YELLOW = "\u001B[33m"
private const val DIM = "\u001B[2m"
private const val BOLD = "\u001B[1m"
private const val BRIGHT_CYAN = "\u001B[96m"

/**
 * Hits `GET /api/doctor` on the controller and renders the result.
 *
 * Exit-code contract (used as `System.exit(code)` by the caller — CI-friendly):
 *   0 — all checks OK
 *   1 — warnings only (deployment functional, reviewable)
 *   2 — at least one failure (operator action required)
 *   3 — could not reach controller / non-200 response
 *
 * @param asJson if true, prints the raw JSON body verbatim and skips pretty rendering.
 */
suspend fun runDoctor(
    httpClient: HttpClient,
    baseUrl: String,
    token: String,
    asJson: Boolean,
): Int {
    val json = Json { ignoreUnknownKeys = true }
    val raw: String = try {
        val response = httpClient.get("$baseUrl/api/doctor") {
            if (token.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (response.status != HttpStatusCode.OK) {
            System.err.println("${RED}doctor: controller returned ${response.status}${RESET}")
            return 3
        }
        response.bodyAsText()
    } catch (e: Exception) {
        System.err.println("${RED}doctor: could not reach $baseUrl — ${e.message}${RESET}")
        return 3
    }

    if (asJson) {
        println(raw)
    }

    val report = try {
        json.decodeFromString<CliDoctorReport>(raw)
    } catch (e: Exception) {
        System.err.println("${RED}doctor: malformed response — ${e.message}${RESET}")
        return 3
    }

    if (!asJson) render(report)

    return when (report.status) {
        "ok"   -> 0
        "warn" -> 1
        "fail" -> 2
        else   -> 0
    }
}

private fun render(report: CliDoctorReport) {
    println()
    println("${BOLD}${BRIGHT_CYAN}Nimbus Doctor${RESET}")
    for (section in report.sections) {
        println()
        println("${BOLD}── ${section.name} ──${RESET}")
        for (f in section.findings) {
            val (icon, color) = when (f.level) {
                "OK"   -> "✓" to GREEN
                "WARN" -> "!" to YELLOW
                "FAIL" -> "✗" to RED
                else   -> "·" to DIM
            }
            println("  $color$icon${RESET} ${f.message}")
            if (f.level != "OK" && f.hint != null) {
                println("    ${DIM}→ ${f.hint}${RESET}")
            }
        }
    }
    println()
    val summary = when {
        report.failCount > 0 -> "${RED}${report.failCount} problem(s), ${report.warnCount} warning(s) — address the failures first${RESET}"
        report.warnCount > 0 -> "${YELLOW}${report.warnCount} warning(s) — deployment is functional, but consider reviewing${RESET}"
        else                 -> "${GREEN}All checks passed${RESET}"
    }
    println(summary)
}
