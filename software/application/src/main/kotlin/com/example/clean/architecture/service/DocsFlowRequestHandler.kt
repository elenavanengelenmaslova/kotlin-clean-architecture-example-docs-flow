package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Implementation of HandleDocsFlowRequest that saves Word documents to storage.
 */
@Component
class DocsFlowRequestHandler(
    private val objectStorage: ObjectStorageInterface,
) : HandleDocsFlowRequest {

    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        logger.info { "Processing docs-flow request: ${httpRequest.method} ${httpRequest.path}" }

        // Check if body is present
        val body = httpRequest.body
        if (body == null || !isDocumentValid()) {
            val errorMessage = "No document provided in request body or invalid document"
            logger.warn { errorMessage }
            return HttpResponse(
                HttpStatusCode.valueOf(400),
                body = errorMessage
            )
        }

        return runCatching {
            // Generate a unique ID for the document
            val documentId = "${UUID.randomUUID()}.docx"

            // Save the document to storage
            val documentUrl = objectStorage.save(id = documentId, content = body)

            logger.info { "Document saved successfully with ID: $documentId at URL: $documentUrl" }

            // Return success response
            HttpResponse(
                HttpStatusCode.valueOf(201),
                HttpHeaders().apply {
                    add("Content-Type", "application/json")
                    add("Location", documentUrl)
                },
                body = """{"id":"$documentId","url":"$documentUrl"}"""
            )
        }.getOrElse {
            logger.error(it) { "Failed to save document: ${it.message}" }
            HttpResponse(
                HttpStatusCode.valueOf(500),
                body = "Failed to save document: ${it.message}"
            )
        }
    }

    /**
     * This is business logic, like validation of the submitted document.
     */
    private fun isDocumentValid(): Boolean = true
}
