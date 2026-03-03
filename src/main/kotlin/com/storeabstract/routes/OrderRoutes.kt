package com.storeabstract.routes

import com.storeabstract.config.ApiException
import com.storeabstract.dto.CreateOrderRequest
import com.storeabstract.dto.OrderResponse
import com.storeabstract.dto.toUuidOrNull
import com.storeabstract.repository.CreateOrderLine
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.orderRoutes(services: RouteServices) {
    authenticate("auth-jwt") {
        post("/orders") {
            val auth = currentAuth()
            val userId = auth.userId.toUuidOrNull()
                ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid user id in token")
            val request = call.receive<CreateOrderRequest>()
            val lines = request.items.map {
                val productId = it.productId.toUuidOrNull()
                    ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid product id: ${it.productId}")
                CreateOrderLine(productId = productId, quantity = it.quantity)
            }

            val order = services.orderService.createOrder(userId, lines)
            call.respond(HttpStatusCode.Created, OrderResponse.fromDomain(order))
        }

        get("/orders") {
            val userId = currentAuth().userId.toUuidOrNull()
                ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid user id in token")
            val orders = services.orderService.getOwnOrders(userId).map(OrderResponse::fromDomain)
            call.respond(orders)
        }

        delete("/orders/{id}") {
            val auth = currentAuth()
            val userId = auth.userId.toUuidOrNull()
                ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid user id in token")
            val orderId = call.parameters["id"]?.toUuidOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid order id")

            val order = services.orderService.cancelOrder(userId = userId, orderId = orderId)
            call.respond(OrderResponse.fromDomain(order))
        }
    }
}
