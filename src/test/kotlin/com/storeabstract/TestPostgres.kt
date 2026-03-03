package com.storeabstract

import com.storeabstract.config.DatabaseConfig
import com.storeabstract.config.DatabaseFactory
import org.testcontainers.containers.PostgreSQLContainer

class KPostgresContainer(imageName: String) : PostgreSQLContainer<KPostgresContainer>(imageName)

object TestPostgres {
    private var started = false
    val container: KPostgresContainer = KPostgresContainer("postgres:16-alpine").apply {
        withDatabaseName("store_abstract_test")
        withUsername("test")
        withPassword("test")
    }

    fun initDatabase() {
        if (!started) {
            container.start()
            started = true
        }
        val postgres = container
        DatabaseFactory.init(
            DatabaseConfig(
                host = postgres.host,
                port = postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                name = postgres.databaseName,
                user = postgres.username,
                password = postgres.password,
            ),
            migrate = true,
        )
    }

    fun stopDatabase() {
        DatabaseFactory.close()
        if (started) {
            container.stop()
            started = false
        }
    }
}
