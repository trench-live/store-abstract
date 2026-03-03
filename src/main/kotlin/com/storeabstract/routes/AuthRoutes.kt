package com.storeabstract.routes

import com.storeabstract.dto.AuthResponse
import com.storeabstract.dto.LoginRequest
import com.storeabstract.dto.RegisterRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(services: RouteServices) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            val token = services.authService.register(request.email, request.password)
            call.respond(AuthResponse(token))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val token = services.authService.login(request.email, request.password)
            call.respond(AuthResponse(token))
        }
    }
}
