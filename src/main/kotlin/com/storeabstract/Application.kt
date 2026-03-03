package com.storeabstract

import com.storeabstract.config.ApiException
import com.storeabstract.config.AppEnvironment
import com.storeabstract.config.DatabaseFactory
import com.storeabstract.config.JwtService
import com.storeabstract.config.OrderEventsWorker
import com.storeabstract.config.PasswordService
import com.storeabstract.config.RedisOrderCache
import com.storeabstract.config.UserPrincipal
import com.storeabstract.domain.UserRole
import com.storeabstract.dto.ErrorResponse
import com.storeabstract.repository.OrderRepository
import com.storeabstract.repository.ProductRepository
import com.storeabstract.repository.UserRepository
import com.storeabstract.routes.RouteServices
import com.storeabstract.routes.adminRoutes
import com.storeabstract.routes.authRoutes
import com.storeabstract.routes.miscRoutes
import com.storeabstract.routes.orderRoutes
import com.storeabstract.routes.productRoutes
import com.storeabstract.service.AuthService
import com.storeabstract.service.OrderCache
import com.storeabstract.service.OrderService
import com.storeabstract.service.ProductService
import com.storeabstract.service.StatsService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun main() {
    val env = AppEnvironment.fromEnv()

    if (env.appMode.lowercase() == "worker") {
        OrderEventsWorker(env.db, env.rabbit).start()
        return
    }

    embeddedServer(Netty, host = "0.0.0.0", port = env.port) {
        module(env)
    }.start(wait = true)
}

fun Application.module(
    env: AppEnvironment = AppEnvironment.fromEnv(),
    orderCacheOverride: OrderCache? = null,
    closeResourcesOnStop: Boolean = orderCacheOverride == null,
) {
    val json = Json {
        prettyPrint = false
        isLenient = false
        ignoreUnknownKeys = false
    }

    DatabaseFactory.init(env.db, migrate = true)

    val jwtService = JwtService(env.jwtSecret)
    val passwordService = PasswordService()

    val userRepository = UserRepository()
    val productRepository = ProductRepository()
    val orderRepository = OrderRepository()

    bootstrapAdminIfConfigured(env, userRepository, passwordService)

    val orderCache = orderCacheOverride ?: RedisOrderCache(env.redis)

    val authService = AuthService(userRepository, passwordService, jwtService)
    val productService = ProductService(productRepository)
    val orderService = OrderService(orderRepository, orderCache, json)
    val statsService = StatsService(orderRepository)

    install(CallLogging)

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<Throwable> { call, cause ->
            this@module.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(jwtService.verifier())
            validate { credentials ->
                val userId = credentials.payload.subject ?: return@validate null
                val role = credentials.payload.getClaim("role").asString()?.let { roleName ->
                    runCatching { UserRole.valueOf(roleName) }.getOrNull()
                } ?: return@validate null
                UserPrincipal(userId = userId, role = role)
            }
        }
    }

    routing {
        val services = RouteServices(
            authService = authService,
            productService = productService,
            orderService = orderService,
            statsService = statsService,
        )

        authRoutes(services)
        productRoutes(services)
        orderRoutes(services)
        adminRoutes(services)
        miscRoutes()
    }

    if (closeResourcesOnStop) {
        environment.monitor.subscribe(ApplicationStopped) {
            runCatching { orderCache.close() }
            runCatching { DatabaseFactory.close() }
        }
    }
}

private fun Application.bootstrapAdminIfConfigured(
    env: AppEnvironment,
    userRepository: UserRepository,
    passwordService: PasswordService,
) {
    val email = env.adminEmail
    val password = env.adminPassword

    if (email.isNullOrBlank() && password.isNullOrBlank()) {
        return
    }

    if (email.isNullOrBlank() || password.isNullOrBlank()) {
        log.warn("Admin bootstrap skipped: both ADMIN_EMAIL and ADMIN_PASSWORD must be set")
        return
    }

    if (password.length < 6) {
        error("ADMIN_PASSWORD must be at least 6 characters")
    }

    val normalizedEmail = email.trim().lowercase()
    userRepository.createOrUpdateAdmin(
        email = normalizedEmail,
        passwordHash = passwordService.hash(password),
    )
    log.info("Bootstrap admin account ensured for {}", normalizedEmail)
}
