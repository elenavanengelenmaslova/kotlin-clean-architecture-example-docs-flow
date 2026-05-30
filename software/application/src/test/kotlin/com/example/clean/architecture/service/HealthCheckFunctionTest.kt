package com.example.clean.architecture.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.RepeatedTest

/**
 * Validates: Requirements 2.1, 3.1
 * Property 1: Health check idempotency
 */
class HealthCheckFunctionTest {

    private val config = HealthCheckConfig()
    private val healthCheckSupplier = config.healthCheck()

    @Test
    fun `Given a healthy application When healthCheck is invoked Then it returns HealthStatus UP`() {
        val result = healthCheckSupplier.get()

        assertEquals(HealthStatus(status = "UP"), result)
    }

    @Test
    fun `Given a healthy application When healthCheck is invoked Then status field is UP`() {
        val result = healthCheckSupplier.get()

        assertEquals("UP", result.status)
    }

    /**
     * **Validates: Requirements 2.1, 3.1**
     * **Property 1: Health check idempotency**
     *
     * For any number of sequential invocations of the health check,
     * the response SHALL always be HealthStatus("UP") — invoking the
     * health check N times produces the same result each time.
     */
    @RepeatedTest(100)
    fun `Given a healthy application When healthCheck is invoked multiple times Then it always returns the same result`() {
        val result = healthCheckSupplier.get()

        assertEquals(HealthStatus(status = "UP"), result)
    }

    @Test
    fun `Given a healthy application When healthCheck is invoked sequentially Then all results are identical`() {
        val results = (1..50).map { healthCheckSupplier.get() }

        val allIdentical = results.all { it == HealthStatus(status = "UP") }
        assertEquals(true, allIdentical)
        assertEquals(1, results.distinct().size)
    }
}
