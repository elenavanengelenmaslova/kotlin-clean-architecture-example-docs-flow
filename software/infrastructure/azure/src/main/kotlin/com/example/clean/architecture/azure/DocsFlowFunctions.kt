package com.example.clean.architecture.azure

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.service.HandleDocsFlowRequest
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.BlobTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

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
        ) request: HttpRequestMessage<ByteArray>,
        context: ExecutionContext,
    ): HttpResponseMessage {
        logger.info { "Processing docs-flow request" }

        val response = handleDocsFlowRequest(
            HttpRequest(
                org.springframework.http.HttpMethod.valueOf(request.httpMethod.name),
                request.headers,
                "",
                request.queryParameters,
                request.body
            )
        )
        return buildResponse(request, response)
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

//    @FunctionName("ProcessDocument")
//    fun processDocument(
//        @BlobTrigger(
//            name = "content",
//            path = "docs-flow/{name}",
//            connection = "TriggerBlobStorage"
//        ) content: ByteArray,
//        name: String,
//        context: ExecutionContext
//    ) {
//        logger.info { "Blob trigger function processed blob: $name" }
//        logger.info { "Processing document from blob storage" }
//        logger.info { "Document name: $name, size: ${content.size}" }
//        // Additional processing logic can be added here
//    }

}
