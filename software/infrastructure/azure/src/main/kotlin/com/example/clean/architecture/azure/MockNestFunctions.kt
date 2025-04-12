package com.example.clean.architecture.azure

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.service.HandleAdminRequest
import com.example.clean.architecture.service.HandleClientRequest
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import com.microsoft.azure.functions.HttpStatus
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.BindingName
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import org.springframework.stereotype.Component

@Component
class MockNestFunctions(
    private val handleClientRequest: HandleClientRequest,
    private val handleAdminRequest: HandleAdminRequest,
) {

    @FunctionName("RequestForwarder")
    fun forwardClientRequest(
        @HttpTrigger(
            name = "request",
            methods = [HttpMethod.POST, HttpMethod.GET, HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE],
            authLevel = AuthorizationLevel.FUNCTION,
            route = "mocknest/{*route}"
        ) request: HttpRequestMessage<String>,
        @BindingName("route") route: String?,
        context: ExecutionContext,
    ): HttpResponseMessage {
        val response = handleClientRequest(
            HttpRequest(
                org.springframework.http.HttpMethod.valueOf(request.httpMethod.name),
                request.headers,
                route,
                request.queryParameters,
                request.body
            )
        )

        return buildResponse(request, response)
    }

    @FunctionName("Admin")
    fun forwardAdminRequest(
        @HttpTrigger(
            name = "request",
            methods = [HttpMethod.POST, HttpMethod.GET, HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE],
            authLevel = AuthorizationLevel.FUNCTION,
            route = "__admin/{*route}"
        ) request: HttpRequestMessage<String>,
        @BindingName("route") route: String?,
        context: ExecutionContext,
    ): HttpResponseMessage {
        val response = handleAdminRequest(
            route ?: "",
            HttpRequest(
                org.springframework.http.HttpMethod.valueOf(request.httpMethod.name),
                request.headers,
                route,
                request.queryParameters,
                request.body
            )
        )
        return buildResponse(request, response)
    }

    private fun buildResponse(
        request: HttpRequestMessage<String>,
        response: HttpResponse,
    ): HttpResponseMessage {
        return request
            .createResponseBuilder(HttpStatus.valueOf(response.httpStatusCode.value()))
            .let { responseBuilder ->
                var builder = responseBuilder
                response.headers?.forEach { header ->
                    header.value.forEach {
                        builder = builder.header(header.key, it)
                    }
                }
                builder
            }
            .body(response.body)
            .build()
    }

}