package com.storeabstract.config

import java.io.File

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String,
) {
    val jdbcUrl: String = "jdbc:postgresql://$host:$port/$name"
}

data class RedisConfig(
    val host: String,
    val port: Int,
)

data class RabbitMqConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

data class AppEnvironment(
    val appMode: String,
    val port: Int,
    val db: DatabaseConfig,
    val jwtSecret: String,
    val redis: RedisConfig,
    val rabbit: RabbitMqConfig,
    val adminEmail: String? = null,
    val adminPassword: String? = null,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = mergedEnvironment()): AppEnvironment {
            fun required(name: String): String = env[name]?.takeIf { it.isNotBlank() }
                ?: error("Environment variable $name is required")

            return AppEnvironment(
                appMode = env["APP_MODE"]?.ifBlank { "api" } ?: "api",
                port = env["PORT"]?.toIntOrNull() ?: 18080,
                db = DatabaseConfig(
                    host = required("DB_HOST"),
                    port = required("DB_PORT").toInt(),
                    name = required("DB_NAME"),
                    user = required("DB_USER"),
                    password = required("DB_PASSWORD"),
                ),
                jwtSecret = required("JWT_SECRET"),
                redis = RedisConfig(
                    host = required("REDIS_HOST"),
                    port = required("REDIS_PORT").toInt(),
                ),
                rabbit = RabbitMqConfig(
                    host = required("RABBITMQ_HOST"),
                    port = required("RABBITMQ_PORT").toInt(),
                    user = required("RABBITMQ_USER"),
                    password = required("RABBITMQ_PASSWORD"),
                ),
                adminEmail = env["ADMIN_EMAIL"]?.trim()?.ifBlank { null },
                adminPassword = env["ADMIN_PASSWORD"]?.trim()?.ifBlank { null },
            )
        }

        private fun mergedEnvironment(): Map<String, String> {
            val fromDotEnv = loadDotEnv(".env")
            val fromSystem = System.getenv()
            return fromDotEnv + fromSystem
        }

        private fun loadDotEnv(path: String): Map<String, String> {
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                return emptyMap()
            }

            return file.readLines()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) return@mapNotNull null

                    val key = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim().trim('"', '\'')
                    if (key.isBlank()) null else key to value
                }
                .toMap()
        }
    }
}
