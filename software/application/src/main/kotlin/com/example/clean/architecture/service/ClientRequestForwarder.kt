package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.ImmutableRequest
import com.github.tomakehurst.wiremock.http.RequestMethod
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import com.github.tomakehurst.wiremock.http.HttpHeaders as WireMockHttpHeaders

private val logger = KotlinLogging.logger {}
private const val BASE_URL = "http://mocknest.internal"

@Component
class ClientRequestForwarder(private val directCallHttpServer: DirectCallHttpServer) :
    HandleClientRequest {
    override fun invoke(httpRequest: HttpRequest): HttpResponse {
        logger.info { "Forwarding request body: ${httpRequest.body} to path: ${httpRequest.path}" }
        return runCatching {
            val queryString =
                httpRequest.queryParameters.entries
                    .joinToString("&") { (key, value) -> "$key=$value" }
                    .takeIf { it.isNotEmpty() }
                    ?.let { "?$it" }
                    .orEmpty()
            val path = httpRequest.path
            val absoluteURL = "$BASE_URL/$path$queryString"

            logger.info { "Posting to absolute url: $absoluteURL" }
            // Create a MockNest request
            val mockNestRequest =
                ImmutableRequest.create()
                    .withAbsoluteUrl(absoluteURL)
                    .withMethod(
                        RequestMethod.fromString(
                            httpRequest.method.name()
                        )
                    )
                    .withHeaders(
                        WireMockHttpHeaders(
                            httpRequest.headers.map {
                                HttpHeader(it.key, it.value)
                            }
                        ))
                    .withBody(httpRequest.body?.toString().orEmpty().toByteArray())
                    .build()

            logger.debug { "Calling MockNest with request: ${httpRequest.method} ${httpRequest.path}" }

            // Call stubRequest on the DirectCallHttpServer
            val response = directCallHttpServer.stubRequest(mockNestRequest)

            logger.trace { "MockNest response: ${response.bodyAsString}, code: ${response.status}" }

            // Convert the MockNest Response to an HttpResponse
            val responseHeaders = HttpHeaders()
            response.headers.all().forEach { header ->
                responseHeaders.add(header.key(), header.firstValue())
            }

            HttpResponse(
                HttpStatusCode.valueOf(response.status),
                responseHeaders,
                response.bodyAsString
            )
        }.getOrElse { exception ->
            // Handle exceptions
            logger.error(exception) { "Failed to call MockNest: ${exception.message}" }
            HttpResponse(
                HttpStatusCode.valueOf(500),
                body = exception.message
            )
        }
    }

}
