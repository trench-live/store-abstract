package com.storeabstract.routes

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.http.ContentType

fun Route.miscRoutes() {
    get("/health") {
        call.respondText("OK", ContentType.Text.Plain)
    }

    get("/swagger") {
        call.respondText(
            """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>Store Abstract API</title>
              <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
            </head>
            <body>
              <div id="swagger-ui"></div>
              <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
              <script>
                SwaggerUIBundle({
                  url: '/openapi.yaml',
                  dom_id: '#swagger-ui'
                });
              </script>
            </body>
            </html>
            """.trimIndent(),
            ContentType.Text.Html,
        )
    }

    get("/openapi.yaml") {
        val spec = this::class.java.classLoader.getResource("openapi.yaml")
            ?.readText()
            ?: error("openapi.yaml not found in resources")
        call.respondText(spec, ContentType.parse("application/yaml"))
    }
}
