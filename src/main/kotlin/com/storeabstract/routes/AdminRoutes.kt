package com.storeabstract.routes

import com.storeabstract.dto.OrderStatsResponse
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.adminRoutes(services: RouteServices) {
    authenticate("auth-jwt") {
        get("/stats/orders") {
            currentAuth().requireAdmin()
            val stats = services.statsService.getOrderStats()
            call.respond(OrderStatsResponse.fromDomain(stats))
        }
    }
}
