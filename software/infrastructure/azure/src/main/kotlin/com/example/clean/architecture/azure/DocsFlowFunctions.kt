package com.example.clean.architecture.azure

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.service.HandleDocsFlowRequest
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
class DocsFlowFunctions(
    private val handleDocsFlowRequest: HandleDocsFlowRequest,
) {

    @FunctionName("UploadDocument")
    fun uploadDocument(
        @HttpTrigger(
            name = "request",
            methods = [HttpMethod.POST],
            authLevel = AuthorizationLevel.FUNCTION,
            route = "docs-flow"
        ) request: HttpRequestMessage<String>,
        context: ExecutionContext,
    ): HttpResponseMessage {
        logger.info { "Processing docs-flow request" }
        val bodyBytes = Base64.getDecoder().decode(request.body)

        val response = handleDocsFlowRequest(
            HttpRequest(
                org.springframework.http.HttpMethod.valueOf(request.httpMethod.name),
                request.headers,
                "",
                request.queryParameters,
                bodyBytes
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
