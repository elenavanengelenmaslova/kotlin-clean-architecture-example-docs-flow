package com.example.clean.architecture

import com.amazonaws.services.lambda.runtime.Context
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.clearMocks
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.cloud.function.adapter.aws.FunctionInvoker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HealthCheckHandlerTest {

    private val objectMapper = ObjectMapper()
    private val mockContext: Context = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearMocks(mockContext)
    }

    @Test
    fun `Given a healthy application When health check is invoked Then returns HTTP 200 with status UP`() {
        // Given
        System.setProperty("MAIN_CLASS", "com.example.clean.architecture.Application")
        System.setProperty("spring.cloud.function.definition", "healthCheck")

        val apiGatewayRequest = objectMapper.writeValueAsString(
            mapOf(
                "httpMethod" to "GET",
                "path" to "/health",
                "headers" to mapOf("Content-Type" to "application/json"),
                "requestContext" to mapOf("stage" to "demo")
            )
        )
        val inputStream = ByteArrayInputStream(apiGatewayRequest.toByteArray())
        val outputStream = ByteArrayOutputStream()

        // When
        val invoker = FunctionInvoker()
        invoker.handleRequest(inputStream, outputStream, mockContext)

        // Then
        val response = objectMapper.readTree(outputStream.toByteArray())
        assertEquals(200, response.get("statusCode").asInt())
        val body = objectMapper.readTree(response.get("body").asText())
        assertEquals("UP", body.get("status").asText())
    }

    @Test
    fun `Given an internal error When health check is invoked Then invocation fails so the platform returns a 5xx`() {
        // Given - configure a non-existent function to simulate an internal error.
        // The Spring Cloud Function AWS adapter does not convert a routing failure into a
        // 5xx response body itself; it throws, and API Gateway surfaces that as a 5xx to
        // the client (see design: "the invocation itself will fail, producing a 5xx from the platform").
        System.setProperty("MAIN_CLASS", "com.example.clean.architecture.Application")
        System.setProperty("spring.cloud.function.definition", "nonExistentFunction")

        val apiGatewayRequest = objectMapper.writeValueAsString(
            mapOf(
                "httpMethod" to "GET",
                "path" to "/health",
                "headers" to mapOf("Content-Type" to "application/json"),
                "requestContext" to mapOf("stage" to "demo")
            )
        )
        val inputStream = ByteArrayInputStream(apiGatewayRequest.toByteArray())
        val outputStream = ByteArrayOutputStream()

        // When / Then - the invocation fails fast, which the platform maps to a 5xx response
        val invoker = FunctionInvoker()
        assertThrows(Exception::class.java) {
            invoker.handleRequest(inputStream, outputStream, mockContext)
        }
    }
}
