package com.example.clean.architecture.aws.function

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.service.HandleAdminRequest
import com.example.clean.architecture.service.HandleClientRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import java.util.function.Function

private val logger = KotlinLogging.logger {}
private const val ADMIN_PREFIX = "/__admin/"
private const val MOCKNEST_PREFIX = "/mocknest/"

@Configuration
class MockNestFunctions(
    private val handleClientRequest: HandleClientRequest,
    private val handleAdminRequest: HandleAdminRequest,
) {
    @Bean
    fun router(): Function<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
        return Function { event ->
            with(event) {
                logger.info { "MockNest request: $httpMethod $path $headers" }
                if (path.startsWith(ADMIN_PREFIX)) {
                    val adminPath = path.removePrefix(ADMIN_PREFIX)
                    handleAdminRequest(adminPath, createHttpRequest(adminPath))
                } else {
                    handleClientRequest(createHttpRequest(path.removePrefix(MOCKNEST_PREFIX)))
                }
            }.let {
                APIGatewayProxyResponseEvent()
                    .withStatusCode(it.httpStatusCode.value())
                    .withHeaders(it.headers?.toSingleValueMap())
                    .withBody(it.body?.toString().orEmpty())
            }

        }
    }

    private fun APIGatewayProxyRequestEvent.createHttpRequest(path: String): HttpRequest {
        val request = HttpRequest(
            method = HttpMethod.valueOf(httpMethod),
            headers = headers,
            path = path,
            queryParameters = queryStringParameters.orEmpty(),
            body = body
        )
        return request
    }
}


