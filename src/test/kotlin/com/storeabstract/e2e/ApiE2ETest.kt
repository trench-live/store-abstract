package com.storeabstract.e2e

import com.storeabstract.TestDbCleaner
import com.storeabstract.TestPostgres
import com.storeabstract.config.AppEnvironment
import com.storeabstract.config.DatabaseConfig
import com.storeabstract.config.InMemoryOrderCache
import com.storeabstract.config.RabbitMqConfig
import com.storeabstract.config.RedisConfig
import com.storeabstract.domain.UserRole
import com.storeabstract.dto.AuthResponse
import com.storeabstract.dto.CreateOrderRequest
import com.storeabstract.dto.OrderItemRequest
import com.storeabstract.dto.ProductRequest
import com.storeabstract.dto.RegisterRequest
import com.storeabstract.module
import com.storeabstract.repository.UserRepository
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiE2ETest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @BeforeEach
    fun clean() {
        TestDbCleaner.cleanAll()
    }

    @Test
    fun `user can register create order and view own orders`() = testApplication {
        val postgres = TestPostgres.container
        val env = AppEnvironment(
            appMode = "api",
            port = 18080,
            db = DatabaseConfig(
                host = postgres.host,
                port = postgres.getMappedPort(5432),
                name = postgres.databaseName,
                user = postgres.username,
                password = postgres.password,
            ),
            jwtSecret = "test-secret",
            redis = RedisConfig("localhost", 6379),
            rabbit = RabbitMqConfig("localhost", 5672, "guest", "guest"),
        )

        application {
            module(
                env = env,
                orderCacheOverride = InMemoryOrderCache(),
            )
        }

        val apiClient = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val userRepository = UserRepository()
        val admin = userRepository.create("admin@example.com", "hash", UserRole.ADMIN)

        val adminToken = com.storeabstract.config.JwtService("test-secret").generate(admin)

        val createProductResponse = apiClient.post("/products") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody(
                ProductRequest(
                    name = "Book",
                    description = "Kotlin book",
                    price = BigDecimal("15.00"),
                    stock = 10,
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createProductResponse.status)

        val productsResponse = apiClient.get("/products")
        assertEquals(HttpStatusCode.OK, productsResponse.status)
        val productsBody = productsResponse.bodyAsText()
        assertTrue(productsBody.contains("Book"))

        val registerResponse = apiClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "user@example.com", password = "password123"))
        }
        assertEquals(HttpStatusCode.OK, registerResponse.status)
        val userToken = registerResponse.body<AuthResponse>().token

        val productId = Regex("\"id\":\"([^\"]+)\"").find(productsBody)?.groupValues?.get(1)
            ?: error("Product id not found in response")

        val createOrderResponse = apiClient.post("/orders") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $userToken")
            setBody(CreateOrderRequest(items = listOf(OrderItemRequest(productId = productId, quantity = 1))))
        }
        assertEquals(HttpStatusCode.Created, createOrderResponse.status)

        val ownOrdersResponse = apiClient.get("/orders") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, ownOrdersResponse.status)
        assertTrue(ownOrdersResponse.bodyAsText().contains("CREATED"))
    }

    @Test
    fun `order response keeps product name after admin deletes product`() = testApplication {
        val postgres = TestPostgres.container
        val env = AppEnvironment(
            appMode = "api",
            port = 18080,
            db = DatabaseConfig(
                host = postgres.host,
                port = postgres.getMappedPort(5432),
                name = postgres.databaseName,
                user = postgres.username,
                password = postgres.password,
            ),
            jwtSecret = "test-secret",
            redis = RedisConfig("localhost", 6379),
            rabbit = RabbitMqConfig("localhost", 5672, "guest", "guest"),
        )

        application {
            module(
                env = env,
                orderCacheOverride = InMemoryOrderCache(),
            )
        }

        val apiClient = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val userRepository = UserRepository()
        val admin = userRepository.create("admin@example.com", "hash", UserRole.ADMIN)
        val adminToken = com.storeabstract.config.JwtService("test-secret").generate(admin)

        val createProductResponse = apiClient.post("/products") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody(
                ProductRequest(
                    name = "Book",
                    description = "Kotlin book",
                    price = BigDecimal("15.00"),
                    stock = 10,
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, createProductResponse.status)

        val productsResponse = apiClient.get("/products")
        assertEquals(HttpStatusCode.OK, productsResponse.status)
        val productsBody = productsResponse.bodyAsText()
        val productId = Regex("\"id\":\"([^\"]+)\"").find(productsBody)?.groupValues?.get(1)
            ?: error("Product id not found in response")

        val registerResponse = apiClient.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "user@example.com", password = "password123"))
        }
        assertEquals(HttpStatusCode.OK, registerResponse.status)
        val userToken = registerResponse.body<AuthResponse>().token

        val createOrderResponse = apiClient.post("/orders") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $userToken")
            setBody(CreateOrderRequest(items = listOf(OrderItemRequest(productId = productId, quantity = 1))))
        }
        assertEquals(HttpStatusCode.Created, createOrderResponse.status)
        assertTrue(createOrderResponse.bodyAsText().contains("\"productName\":\"Book\""))

        val deleteProductResponse = apiClient.delete("/products/$productId") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.NoContent, deleteProductResponse.status)

        val ownOrdersResponse = apiClient.get("/orders") {
            header(HttpHeaders.Authorization, "Bearer $userToken")
        }
        assertEquals(HttpStatusCode.OK, ownOrdersResponse.status)
        assertTrue(ownOrdersResponse.bodyAsText().contains("\"productName\":\"Book\""))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            TestPostgres.initDatabase()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            TestPostgres.stopDatabase()
        }
    }
}
