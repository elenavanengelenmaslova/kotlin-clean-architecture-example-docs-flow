package com.example.clean.architecture.model

import org.springframework.http.HttpMethod

data class HttpRequest(
    val method: HttpMethod,
    val headers: Map<String, String>,
    val path: String?,
    val queryParameters: Map<String, String>,
    val body: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpRequest

        if (method != other.method) return false
        if (headers != other.headers) return false
        if (path != other.path) return false
        if (queryParameters != other.queryParameters) return false
        if (!body.contentEquals(other.body)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (path?.hashCode() ?: 0)
        result = 31 * result + queryParameters.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}
