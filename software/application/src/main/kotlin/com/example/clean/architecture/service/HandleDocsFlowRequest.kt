package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse

/**
 * Interface for handling document flow requests.
 * This interface is responsible for processing Word documents
 * submitted via the docs-flow endpoint and storing them in the
 * appropriate storage.
 */
fun interface HandleDocsFlowRequest: (HttpRequest) -> HttpResponse

/**
 * Interface for processing a stored document.
 * Accepts a document name and returns the processing result.
 */
fun interface ProcessDocument : (String) -> Result<String>