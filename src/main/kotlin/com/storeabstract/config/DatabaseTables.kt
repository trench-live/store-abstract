package com.storeabstract.config

import com.storeabstract.domain.OrderStatus
import com.storeabstract.domain.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object UsersTable : UUIDTable("users") {
    val email = varchar("email", 320).uniqueIndex("users_email_uq")
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 16, UserRole::class)
    val createdAt = datetime("created_at")
}

object ProductsTable : UUIDTable("products") {
    val name = varchar("name", 255)
    val description = text("description")
    val price = decimal("price", precision = 12, scale = 2)
    val stock = integer("stock")
    val isActive = bool("is_active")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object OrdersTable : UUIDTable("orders") {
    val userId = reference("user_id", UsersTable)
    val status = enumerationByName("status", 16, OrderStatus::class)
    val total = decimal("total", precision = 12, scale = 2)
    val createdAt = datetime("created_at")
}

object OrderItemsTable : UUIDTable("order_items") {
    val orderId = reference("order_id", OrdersTable)
    val productId = reference("product_id", ProductsTable)
    val quantity = integer("quantity")
    val priceAtPurchase = decimal("price_at_purchase", precision = 12, scale = 2)
}

object AuditLogsTable : UUIDTable("audit_logs") {
    val actorUserId = reference("actor_user_id", UsersTable).nullable()
    val action = varchar("action", 255)
    val payload = text("payload")
    val createdAt = datetime("created_at")
}

object OutboxEventsTable : UUIDTable("outbox_events") {
    val eventType = varchar("event_type", 100)
    val payload = text("payload")
    val attempts = integer("attempts")
    val nextAttemptAt = datetime("next_attempt_at")
    val publishedAt = datetime("published_at").nullable()
    val lastError = text("last_error").nullable()
    val createdAt = datetime("created_at")
}
