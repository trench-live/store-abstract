package com.storeabstract.routes

import com.storeabstract.dto.ProductRequest
import com.storeabstract.dto.ProductResponse
import com.storeabstract.dto.toUuidOrNull
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.productRoutes(services: RouteServices) {
    get("/products") {
        val products = services.productService.listProducts().map(ProductResponse::fromDomain)
        call.respond(products)
    }

    get("/products/{id}") {
        val id = call.parameters["id"]?.toUuidOrNull()
            ?: throw com.storeabstract.config.ApiException(HttpStatusCode.BadRequest, "Invalid product id")
        val product = services.productService.getProduct(id)
        call.respond(ProductResponse.fromDomain(product))
    }

    authenticate("auth-jwt") {
        post("/products") {
            currentAuth().requireAdmin()
            val request = call.receive<ProductRequest>()
            val created = services.productService.createProduct(
                name = request.name,
                description = request.description,
                price = request.price,
                stock = request.stock,
            )
            call.respond(HttpStatusCode.Created, ProductResponse.fromDomain(created))
        }

        put("/products/{id}") {
            currentAuth().requireAdmin()
            val id = call.parameters["id"]?.toUuidOrNull()
                ?: throw com.storeabstract.config.ApiException(HttpStatusCode.BadRequest, "Invalid product id")
            val request = call.receive<ProductRequest>()
            val updated = services.productService.updateProduct(
                id = id,
                name = request.name,
                description = request.description,
                price = request.price,
                stock = request.stock,
            )
            call.respond(ProductResponse.fromDomain(updated))
        }

        delete("/products/{id}") {
            currentAuth().requireAdmin()
            val id = call.parameters["id"]?.toUuidOrNull()
                ?: throw com.storeabstract.config.ApiException(HttpStatusCode.BadRequest, "Invalid product id")
            services.productService.deleteProduct(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
