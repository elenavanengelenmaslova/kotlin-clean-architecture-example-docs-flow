package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Phase 1 priming: pre-loads application classes and warms the Jackson / Spring Cloud Function
 * serialization caches by round-tripping a sample [HttpRequest] and [HttpResponse].
 *
 * Performs NO network or filesystem I/O — it only touches the JVM (class loading) and in-memory
 * serializers, so it is safe to run inside a SnapStart snapshot (`beforeCheckpoint`), after restore
 * (`afterRestore`), and from the Azure `@WarmupTrigger`.
 */
@Component
class ClassPreloader(
    private val objectMapper: ObjectMapper,
) {
    fun primeClasses() {
        logger.info { "Phase 1 priming: pre-loading classes and warming serialization caches" }
        runCatching {
            // Round-trip a sample request/response through the Jackson converters used by
            // Spring Cloud Function. This forces class loading + serializer cache population
            // without any I/O.
            val sampleRequest = HttpRequest(
                method = HttpMethod.POST,
                headers = mapOf("Content-Type" to "application/json"),
                path = "/docs-flow",
                queryParameters = emptyMap(),
                body = null,
            )
            val requestJson = objectMapper.writeValueAsString(sampleRequest)
            objectMapper.readValue(requestJson, HttpRequest::class.java)

            val sampleResponse = HttpResponse(
                httpStatusCode = HttpStatusCode.valueOf(200),
                body = """{"status":"ok"}""",
            )
            objectMapper.writeValueAsString(sampleResponse)

            logger.info { "Phase 1 priming completed successfully" }
        }.onFailure { e ->
            logger.warn(e) { "Phase 1 priming encountered non-fatal error" }
        }
    }
}
