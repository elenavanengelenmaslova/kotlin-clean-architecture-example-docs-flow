package com.example.clean.architecture.aws

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.service.HandleDocsFlowRequest
import com.example.clean.architecture.service.ReviewAndNotifyDocument
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.*
import java.util.function.Function

private val logger = KotlinLogging.logger {}


@Configuration
class DocsFlowFunctions(
    // TODO: inject handleDocsFlowRequest and reviewAndNotifyDocument
) {
    @Bean
    fun uploadDocument(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "Request: $httpMethod $path $headers" }
                val request = createHttpRequest()
                // TODO: call handle flow
                HttpResponse(
                    HttpStatus.OK,
                    body = "Hello, world!"
                )
            }.let {
                APIGatewayProxyResponseEvent()
                    .withStatusCode(it.httpStatusCode.value())
                    .withHeaders(it.headers?.toSingleValueMap())
                    .withBody(it.body?.toString().orEmpty())
            }
        }
    }

    @Bean
    fun processDocument(): Function<S3Event, String> {
        return Function { event ->
            logger.info { "S3 Event received: Processing document" }
            event.records.map { record ->
                val bucket = record.s3.bucket.name
                val key = record.s3.`object`.key
                logger.info { "Document uploaded to bucket: $bucket, key: $key" }
                //TODO: auto review and notify document
                "Hello, world!"
            }.joinToString("\n") { it }
        }
    }

    private fun APIGatewayProxyRequestEvent.createHttpRequest(): HttpRequest {
        val request = HttpRequest(
            method = HttpMethod.valueOf(httpMethod),
            headers = headers,
            path = path,
            queryParameters = queryStringParameters.orEmpty(),
            body = bodyAsByteArray()
        )
        return request
    }
}

fun APIGatewayProxyRequestEvent.bodyAsByteArray(): ByteArray? =
    if (isBase64Encoded == true && body != null) Base64.getDecoder().decode(body) else null
