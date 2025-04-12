package com.example.clean.architecture.model

import org.springframework.http.HttpMethod

data class HttpRequest(
    val method: HttpMethod,
    val headers: Map<String, String>,
    val path: String?,
    val queryParameters: Map<String, String>,
    val body: Any?
)
