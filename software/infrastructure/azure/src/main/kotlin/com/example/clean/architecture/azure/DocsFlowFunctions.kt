package com.example.clean.architecture.azure

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.service.HandleDocsFlowRequest
import com.example.clean.architecture.service.ReviewAndNotifyDocument
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.BindingName
import com.microsoft.azure.functions.annotation.BlobTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class DocsFlowFunctions(
    val handleDocsFlowRequest: HandleDocsFlowRequest,
    val reviewAndNotifyDocument: ReviewAndNotifyDocument,
) {

    @FunctionName("UploadDocument")
    fun uploadDocument(
        @HttpTrigger(
            name = "request",
            methods = [HttpMethod.POST],
            authLevel = AuthorizationLevel.FUNCTION,
            route = "docs-flow"
        ) originalRequest: HttpRequestMessage<ByteArray>,
        context: ExecutionContext,
    ): HttpResponseMessage {
        logger.info { "Processing docs-flow request" }

        val request = HttpRequest(
            org.springframework.http.HttpMethod.valueOf(originalRequest.httpMethod.name),
            originalRequest.headers,
            "",
            originalRequest.queryParameters,
            originalRequest.body
        )
        val response = handleDocsFlowRequest(request)
        return buildResponse(originalRequest, response)
    }

    private fun buildResponse(
        request: HttpRequestMessage<ByteArray>,
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

    @FunctionName("ProcessDocument")
    fun processDocument(
        @BlobTrigger(
            name = "content",
            path = "docs-flow/{name}",
            connection = "TriggerBlobStorage"
        ) content: ByteArray,
        @BindingName("name") name: String,
        context: ExecutionContext
    ) {
        logger.info { "Document name: $name, size: ${content.size}" }
        val result = reviewAndNotifyDocument(name)
        logger.info { "Processed document from blob storage: ${result.getOrNull()}" }
    }

}
