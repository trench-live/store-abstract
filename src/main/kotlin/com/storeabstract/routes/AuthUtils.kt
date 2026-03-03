package com.storeabstract.routes

import com.storeabstract.config.ApiException
import com.storeabstract.config.UserPrincipal
import com.storeabstract.domain.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.util.pipeline.PipelineContext

fun PipelineContext<*, io.ktor.server.application.ApplicationCall>.currentAuth(): AuthContext {
    val principal = call.principal<UserPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Missing auth principal")
    return AuthContext(principal.userId, principal.role)
}

fun AuthContext.requireAdmin() {
    if (role != UserRole.ADMIN) {
        throw ApiException(HttpStatusCode.Forbidden, "Admin access required")
    }
}
