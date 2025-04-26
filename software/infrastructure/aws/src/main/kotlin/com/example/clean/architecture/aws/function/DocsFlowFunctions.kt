package com.example.clean.architecture.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.service.HandleDocsFlowRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import java.util.*
import java.util.function.Function

private val logger = KotlinLogging.logger {}


@Configuration
class MockNestFunctions(
    private val handleDocsFlowRequest: HandleDocsFlowRequest,
) {
    @Bean
    fun uploadDocument(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "Request: $httpMethod $path $headers" }
                handleDocsFlowRequest(createHttpRequest())
            }.let {
                APIGatewayProxyResponseEvent()
                    .withStatusCode(it.httpStatusCode.value())
                    .withHeaders(it.headers?.toSingleValueMap())
                    .withBody(it.body?.toString().orEmpty())
            }
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
