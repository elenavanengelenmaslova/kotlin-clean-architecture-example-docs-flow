package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.example.clean.architecture.model.HttpResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.http.HttpMethod
import java.io.IOException
import kotlin.random.Random
import kotlin.test.assertTrue

/**
 * Unit + property tests for [ClassPreloader].
 *
 * **Validates: Requirements 2.4, 2.5**
 *
 * Property 1: Priming safety and failure isolation — the Phase 1 side of the property:
 * `primeClasses()` completes without throwing regardless of dependency-graph state and
 * performs no network/filesystem I/O while round-tripping the sample request/response.
 */
class ClassPreloaderTest {

    @Test
    fun `Given a real ObjectMapper When primeClasses is invoked Then it completes without throwing`() {
        // Given a fully wired (real) serialization dependency.
        val preloader = ClassPreloader(jacksonObjectMapper())

        // When / Then — Phase 1 must never prevent startup (Requirement 2.4).
        assertDoesNotThrow { preloader.primeClasses() }
    }

    @Test
    fun `Given a mocked ObjectMapper When primeClasses is invoked Then it round-trips the sample HttpRequest and HttpResponse`() {
        // Given a mocked serializer that records what is serialized/deserialized.
        val objectMapper = mockk<ObjectMapper>()
        val serialized = mutableListOf<Any>()
        val sampleRoundTripped = HttpRequest(
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            path = "/docs-flow",
            queryParameters = emptyMap(),
            body = null,
        )
        every { objectMapper.writeValueAsString(capture(serialized)) } returns "{}"
        every { objectMapper.readValue(any<String>(), HttpRequest::class.java) } returns sampleRoundTripped

        // When
        ClassPreloader(objectMapper).primeClasses()

        // Then the request is serialized then deserialized (round-trip) and the response is serialized
        // (Requirement 2.5 — serialization caches warmed for both request and response).
        verify(exactly = 1) { objectMapper.readValue(any<String>(), HttpRequest::class.java) }
        verify(exactly = 2) { objectMapper.writeValueAsString(any()) }
        assertTrue(serialized.any { it is HttpRequest }, "expected the sample HttpRequest to be serialized")
        assertTrue(serialized.any { it is HttpResponse }, "expected the sample HttpResponse to be serialized")

        clearMocks(objectMapper)
    }

    @Test
    fun `Given a mocked ObjectMapper When primeClasses is invoked Then only serialization methods are called (no network or filesystem IO)`() {
        // Given — ClassPreloader's ONLY collaborator is the ObjectMapper, so confirming that the only
        // invocations on it are in-memory serialization calls proves Phase 1 performs no I/O of its own
        // (Requirement 2.4 — network-free priming).
        val objectMapper = mockk<ObjectMapper>()
        every { objectMapper.writeValueAsString(any()) } returns "{}"
        every { objectMapper.readValue(any<String>(), HttpRequest::class.java) } returns mockk(relaxed = true)

        // When
        ClassPreloader(objectMapper).primeClasses()

        // Then — exactly the serialization calls happened and nothing else (no I/O collaborators exist).
        verify(exactly = 2) { objectMapper.writeValueAsString(any()) }
        verify(exactly = 1) { objectMapper.readValue(any<String>(), HttpRequest::class.java) }
        confirmVerified(objectMapper)

        clearMocks(objectMapper)
    }

    /**
     * **Validates: Requirements 2.4, 2.5**
     *
     * For any state of the application's dependency graph, `primeClasses()` SHALL complete without
     * throwing an exception that propagates to the caller. We vary the dependency-graph state by
     * randomizing the behaviour of the injected serializer (healthy, write-failure, read-failure,
     * relaxed) across 100 iterations.
     */
    // Feature: deployment-upgrades-and-healthcheck, Property 1: Priming safety and failure isolation
    @RepeatedTest(100)
    fun `Given any dependency-graph state When primeClasses is invoked Then it never throws`(repetitionInfo: RepetitionInfo) {
        val random = Random(repetitionInfo.currentRepetition)
        val objectMapper = buildRandomObjectMapper(random)

        assertDoesNotThrow { ClassPreloader(objectMapper).primeClasses() }

        clearMocks(objectMapper)
    }

    private fun buildRandomObjectMapper(random: Random): ObjectMapper {
        val objectMapper = mockk<ObjectMapper>(relaxed = true)
        when (random.nextInt(4)) {
            0 -> {
                // Healthy dependency graph: serialization round-trips successfully.
                val writeSlot = slot<Any>()
                every { objectMapper.writeValueAsString(capture(writeSlot)) } returns "{}"
                every { objectMapper.readValue(any<String>(), HttpRequest::class.java) } answers {
                    HttpRequest(HttpMethod.POST, emptyMap(), "/docs-flow", emptyMap(), null)
                }
            }
            1 -> // Broken serialization (write path) — must still be swallowed by runCatching.
                every { objectMapper.writeValueAsString(any()) } throws IOException("write failure")
            2 -> // Broken deserialization (read path).
                every { objectMapper.readValue(any<String>(), HttpRequest::class.java) } throws
                    IllegalStateException("read failure")
            else -> {
                // Fully relaxed mock — default no-op behaviour.
            }
        }
        return objectMapper
    }
}
