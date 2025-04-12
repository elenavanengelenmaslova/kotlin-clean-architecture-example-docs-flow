package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.InvalidInputException
import com.github.tomakehurst.wiremock.common.Json
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import wiremock.org.apache.hc.core5.http.ContentType
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
class AdminForwarder(
    private val wireMockServer: WireMockServer,
    private val objectStorage: ObjectStorageInterface,
) : HandleAdminRequest {

    override fun invoke(
        path: String,
        httpRequest: HttpRequest,
    ): HttpResponse {
        val contentType = ContentType.APPLICATION_JSON.toString()
        return when {
            path == "requests/unmatched/near-misses" && httpRequest.method == HttpMethod.GET -> {
                logger.info { "Retrieving near misses" }
                wireMockServer.runCatching {
                    val mappings = Json.getObjectMapper().writeValueAsString(findNearMissesForUnmatchedRequests())
                    HttpResponse(
                        HttpStatusCode.valueOf(200),
                        HttpHeaders().apply { add(HttpHeaders.CONTENT_TYPE, contentType) },
                        body = mappings
                    )
                }.getOrElse {
                    handleAdminException(it)
                }
            }

            path == "mappings/reset" && httpRequest.method == HttpMethod.POST -> {
                logger.info { "Resetting MockNest mappings" }
                wireMockServer.runCatching {
                    resetToDefaultMappings()
                    // Delete all mappings from storage
                    val storedMappings = objectStorage.list()
                    storedMappings.forEach { mappingId ->
                        logger.info { "Deleting stored MockNest mapping with ID: $mappingId" }
                        objectStorage.delete(mappingId)
                    }

                    HttpResponse(HttpStatusCode.valueOf(200), body = "Mappings reset successfully")
                }.getOrElse { handleAdminException(it) }
            }

            path == "mappings" && httpRequest.method == HttpMethod.POST -> {
                logger.info { "Creating new MockNest stub mapping" }
                wireMockServer.runCatching {
                    val addedMapping = httpRequest.body?.let { body ->
                        // Convert body to string once
                        val bodyString = body.toString()

                        // Parse the mapping
                        val mapping = bodyString.toStubMapping()
                        // Add the mapping to MockNest
                        addStubMapping(mapping)
                        // Check if mapping is persistent and save it
                        saveStubMapping(mapping, bodyString)
                    }

                    // Return the response
                    HttpResponse(httpStatusCode = HttpStatusCode.valueOf(201), body = addedMapping)
                }.getOrElse {
                    handleAdminException(it)
                }

            }

            path.startsWith("mappings/") -> {
                val mappingIdAsString = path.removePrefix("mappings/")
                val mappingId = UUID.fromString(mappingIdAsString)
                when (httpRequest.method) {
                    HttpMethod.GET -> {
                        logger.info { "Retrieving MockNest mapping with ID: $mappingId" }
                        HttpResponse(HttpStatusCode.valueOf(200), body = wireMockServer.getStubMapping(mappingId))
                    }

                    HttpMethod.PUT -> {
                        logger.info { "Updating MockNest mapping with ID: $mappingId" }
                        val updatedMapping = httpRequest.body?.let { body ->
                            // Convert body to string once
                            val bodyString = body.toString()

                            // Parse the mapping
                            val mapping = bodyString.toStubMapping()

                            // Update the mapping in MockNest
                            wireMockServer.editStubMapping(mapping)

                            // Check if mapping is persistent and save it
                            saveStubMapping(mapping, bodyString)

                        }
                        HttpResponse(HttpStatusCode.valueOf(200), body = updatedMapping)
                    }

                    HttpMethod.DELETE -> {
                        logger.info { "Deleting MockNest mapping with ID: $mappingId" }
                        wireMockServer.removeStubMapping(mappingId)
                        // Remove from persistent storage
                        objectStorage.delete(mappingIdAsString)
                        HttpResponse(HttpStatusCode.valueOf(200), body = "Stub mapping deleted successfully")
                    }

                    else -> {
                        logger.warn { "Unsupported method for admin request: ${httpRequest.method}" }
                        HttpResponse(HttpStatusCode.valueOf(405), body = "Method not allowed")
                    }
                }
            }

            else -> {
                logger.warn { "Unknown MockNest admin API request: $path" }
                HttpResponse(
                    HttpStatusCode.valueOf(404), body = "Unknown admin request: $path"
                )
            }
        }
    }

    private fun handleAdminException(exception: Throwable) = when (exception) {
        is InvalidInputException ->
            HttpResponse(
                HttpStatusCode.valueOf(400),
                body = exception.message
            ).also { logger.info { "MockNest error: $exception" } }

        else -> HttpResponse(
            HttpStatusCode.valueOf(500),
            body = exception.message
        ).also { logger.error(exception) { "MockNest error: $exception" } }
    }

    private fun String.toStubMapping(): StubMapping = Json.getObjectMapper().readValue(this, StubMapping::class.java)

    private fun saveStubMapping(mapping: StubMapping, bodyString: String): String {
        return mapping.runCatching {
            if (isPersistent) {
                logger.info { "Saving persistent mapping with ID: $id" }
                objectStorage.save(id.toString(), bodyString).let { "Saved mapping $it" }
            } else "Mapping ${mapping.id} not persistent"
        }.onFailure {
            logger.error(it) { "Failed to check or save persistent mapping: ${it.message}" }
        }.getOrThrow()
    }
}
