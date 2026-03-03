package com.storeabstract.routes

import com.storeabstract.domain.UserRole
import com.storeabstract.service.AuthService
import com.storeabstract.service.OrderService
import com.storeabstract.service.ProductService
import com.storeabstract.service.StatsService

data class RouteServices(
    val authService: AuthService,
    val productService: ProductService,
    val orderService: OrderService,
    val statsService: StatsService,
)

data class AuthContext(
    val userId: String,
    val role: UserRole,
)
