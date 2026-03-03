package com.storeabstract

import com.storeabstract.config.AuditLogsTable
import com.storeabstract.config.DatabaseFactory
import com.storeabstract.config.OrderItemsTable
import com.storeabstract.config.OrdersTable
import com.storeabstract.config.OutboxEventsTable
import com.storeabstract.config.ProductsTable
import com.storeabstract.config.UsersTable
import org.jetbrains.exposed.sql.deleteAll

object TestDbCleaner {
    fun cleanAll() {
        DatabaseFactory.dbQuery {
            OutboxEventsTable.deleteAll()
            OrderItemsTable.deleteAll()
            OrdersTable.deleteAll()
            AuditLogsTable.deleteAll()
            ProductsTable.deleteAll()
            UsersTable.deleteAll()
        }
    }
}
