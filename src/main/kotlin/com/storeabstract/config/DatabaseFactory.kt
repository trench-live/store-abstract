package com.storeabstract.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun init(config: DatabaseConfig, migrate: Boolean = true) {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        dataSource?.close()
        dataSource = HikariDataSource(hikari)

        Database.connect(dataSource!!)

        if (migrate) {
            Flyway.configure()
                .dataSource(config.jdbcUrl, config.user, config.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }

    fun <T> dbQuery(block: Transaction.() -> T): T = transaction { block() }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}
