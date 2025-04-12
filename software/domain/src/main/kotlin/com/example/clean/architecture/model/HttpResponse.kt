package com.example.clean.architecture.model

import org.springframework.http.HttpStatusCode
import org.springframework.util.MultiValueMap

data class HttpResponse(
    val httpStatusCode: HttpStatusCode,
    val headers: MultiValueMap<String, String>? = null,
    val body: Any? = null
)