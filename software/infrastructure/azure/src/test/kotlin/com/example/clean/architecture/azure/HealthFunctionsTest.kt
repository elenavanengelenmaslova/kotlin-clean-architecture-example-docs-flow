package com.example.clean.architecture.azure

import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import com.microsoft.azure.functions.HttpStatus
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthFunctionsTest {

    private val request: HttpRequestMessage<Void> = mockk(relaxed = true)
    private val context: ExecutionContext = mockk(relaxed = true)
    private val healthFunctions = HealthFunctions()

    @AfterEach
    fun tearDown() {
        clearMocks(request, context)
    }

    @Test
    fun `Given a healthy application When health endpoint is called Then returns HTTP 200 with status UP`() {
        // Given
        val responseBuilder: HttpResponseMessage.Builder = mockk(relaxed = true)
        val expectedResponse: HttpResponseMessage = mockk(relaxed = true)

        every { request.createResponseBuilder(HttpStatus.OK) } returns responseBuilder
        every { responseBuilder.header(any(), any()) } returns responseBuilder
        every { responseBuilder.body(any()) } returns responseBuilder
        every { responseBuilder.build() } returns expectedResponse
        every { expectedResponse.status } returns HttpStatus.OK
        every { expectedResponse.getHeader("Content-Type") } returns "application/json"
        every { expectedResponse.body } returns """{"status":"UP"}"""

        // When
        val response = healthFunctions.health(request, context)

        // Then
        assertEquals(HttpStatus.OK, response.status)
        assertEquals("application/json", response.getHeader("Content-Type"))
        assertEquals("""{"status":"UP"}""", response.body)
    }

    @Test
    fun `Given an internal error When health endpoint is called Then returns HTTP 503 with status DOWN`() {
        // Given
        val errorResponseBuilder: HttpResponseMessage.Builder = mockk(relaxed = true)
        val errorResponse: HttpResponseMessage = mockk(relaxed = true)

        every { request.createResponseBuilder(HttpStatus.OK) } throws RuntimeException("Internal error")
        every { request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE) } returns errorResponseBuilder
        every { errorResponseBuilder.header(any(), any()) } returns errorResponseBuilder
        every { errorResponseBuilder.body(any()) } returns errorResponseBuilder
        every { errorResponseBuilder.build() } returns errorResponse
        every { errorResponse.status } returns HttpStatus.SERVICE_UNAVAILABLE
        every { errorResponse.getHeader("Content-Type") } returns "application/json"
        every { errorResponse.body } returns """{"status":"DOWN"}"""

        // When
        val response = healthFunctions.health(request, context)

        // Then
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status)
        assertEquals("application/json", response.getHeader("Content-Type"))
        assertEquals("""{"status":"DOWN"}""", response.body)
    }
}
