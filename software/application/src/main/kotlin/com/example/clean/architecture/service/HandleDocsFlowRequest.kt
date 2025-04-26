package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse

/**
 * Interface for handling document flow requests.
 * This interface is responsible for processing Word documents
 * submitted via the docs-flow endpoint and storing them in the
 * appropriate storage.
 */
fun interface HandleDocsFlowRequest {
    /**
     * Process a document flow request.
     * 
     * @param httpRequest The HTTP request containing the Word document
     * @return An HTTP response indicating success or failure
     */
    operator fun invoke(httpRequest: HttpRequest): HttpResponse
}