package com.storeabstract.config

import io.ktor.http.HttpStatusCode

class ApiException(
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)
