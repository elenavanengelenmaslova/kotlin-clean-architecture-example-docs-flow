package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Phase 1 priming: pre-loads application classes and warms the Jackson serialization caches by
 * round-tripping a sample [HttpRequest] and [HttpResponse].
 *
 * Performs NO network or filesystem I/O — it only touches the JVM (class loading) and in-memory
 * serializers, so it is safe to run inside a SnapStart snapshot (`beforeCheckpoint`), after restore
 * (`afterRestore`), and from the Azure `@WarmupTrigger`.
 *
 * The models carry the Spring HTTP wrapper types [HttpMethod] and [HttpStatusCode], which a vanilla
 * Jackson [ObjectMapper] cannot (de)serialize on its own (no bean properties → `InvalidDefinitionException`).
 * Priming therefore uses a dedicated [ObjectMapper] copy with small (de)serializers registered for those
 * two types. The shared application [ObjectMapper] bean is left untouched, and the copy is built once at
 * construction time (which, on AWS Lambda, runs during INIT before the SnapStart checkpoint).
 */
@Component
class ClassPreloader(
    objectMapper: ObjectMapper,
) {
    private val primingMapper: ObjectMapper = objectMapper.copy().registerModule(springHttpTypesModule())

    fun primeClasses() {
        logger.info { "Phase 1 priming: pre-loading classes and warming serialization caches" }
        runCatching {
            // Round-trip a sample request/response through Jackson. This forces class loading +
            // serializer cache population without any I/O.
            val sampleRequest = HttpRequest(
                method = HttpMethod.POST,
                headers = mapOf("Content-Type" to "application/json"),
                path = "/docs-flow",
                queryParameters = emptyMap(),
                body = null,
            )
            val requestJson = primingMapper.writeValueAsString(sampleRequest)
            primingMapper.readValue(requestJson, HttpRequest::class.java)

            val sampleResponse = HttpResponse(
                httpStatusCode = HttpStatusCode.valueOf(200),
                body = """{"status":"ok"}""",
            )
            primingMapper.writeValueAsString(sampleResponse)

            logger.info { "Phase 1 priming completed successfully" }
        }.onFailure { e ->
            logger.warn(e) { "Phase 1 priming encountered non-fatal error" }
        }
    }

    private companion object {
        /**
         * Minimal Jackson (de)serializers for the Spring HTTP wrapper types used by the domain models.
         * [HttpMethod] is rendered as its method name (e.g. `"POST"`) and [HttpStatusCode] as its numeric
         * value (e.g. `200`) — enough to make the priming round-trip succeed and warm the caches.
         */
        fun springHttpTypesModule(): SimpleModule =
            SimpleModule().apply {
                addSerializer(
                    HttpMethod::class.java,
                    object : JsonSerializer<HttpMethod>() {
                        override fun serialize(value: HttpMethod, gen: JsonGenerator, serializers: SerializerProvider) {
                            gen.writeString(value.name())
                        }
                    },
                )
                addDeserializer(
                    HttpMethod::class.java,
                    object : JsonDeserializer<HttpMethod>() {
                        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HttpMethod =
                            HttpMethod.valueOf(p.valueAsString)
                    },
                )
                addSerializer(
                    HttpStatusCode::class.java,
                    object : JsonSerializer<HttpStatusCode>() {
                        override fun serialize(
                            value: HttpStatusCode,
                            gen: JsonGenerator,
                            serializers: SerializerProvider,
                        ) {
                            gen.writeNumber(value.value())
                        }
                    },
                )
                addDeserializer(
                    HttpStatusCode::class.java,
                    object : JsonDeserializer<HttpStatusCode>() {
                        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): HttpStatusCode =
                            HttpStatusCode.valueOf(p.intValue)
                    },
                )
            }
    }
}
