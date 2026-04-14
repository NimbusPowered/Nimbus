package dev.nimbuspowered.nimbus.api.routes

import dev.nimbuspowered.nimbus.doctor.DoctorRunner
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * `GET /api/doctor` — runs all doctor checks and returns the structured report.
 *
 * Response mirrors [dev.nimbuspowered.nimbus.doctor.DoctorReport]:
 * ```
 * {
 *   "sections": [{ "name": "Environment", "findings": [{"level": "OK", "message": "...", "hint": null}] }],
 *   "warnCount": 0,
 *   "failCount": 0,
 *   "status": "ok"
 * }
 * ```
 *
 * HTTP status is always 200 — callers decide action based on `failCount` / `status`.
 * This is admin-only because findings may expose paths, token state and cluster topology.
 */
fun Route.doctorRoutes(runner: DoctorRunner) {
    get("/api/doctor") {
        val report = runner.run()
        call.respond(HttpStatusCode.OK, report)
    }
}
