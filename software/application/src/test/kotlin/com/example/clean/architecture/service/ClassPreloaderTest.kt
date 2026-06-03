package com.example.clean.architecture.service

import com.example.clean.architecture.model.HttpRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.IOException
import kotlin.random.Random

/**
 * Unit + property tests for [ClassPreloader].
 *
 * **Validates: Requirements 2.4, 2.5**
 *
 * Property 1: Priming safety and failure isolation — the Phase 1 side of the property:
 * `primeClasses()` completes without throwing regardless of dependency-graph state and
 * round-trips the sample request/response through an in-memory serializer (no network/filesystem I/O).
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
    fun `Given a real ObjectMapper When primeClasses is invoked Then it round-trips the Spring HTTP wrapper types successfully`() {
        // Regression guard for the InvalidDefinitionException on org.springframework.http.HttpMethod /
        // HttpStatusCode: ClassPreloader builds a dedicated mapper copy with (de)serializers for those
        // Spring types, so the sample HttpRequest (carrying HttpMethod) is serialized AND deserialized,
        // and the sample HttpResponse (carrying HttpStatusCode) is serialized — i.e. the round-trip
        // completes past HttpMethod serialization rather than failing and being swallowed.

        // Given a strict mock whose copy() yields a real, spyable mapper that the preloader configures.
        val injected = mockk<ObjectMapper>()
        val primingMapper = spyk(jacksonObjectMapper())
        every { injected.copy() } returns primingMapper

        // When
        ClassPreloader(injected).primeClasses()

        // Then both the request and response were serialized (2 writes) and the request was read back
        // (1 read) — proving the Spring HTTP types serialize successfully (Requirement 2.5).
        verify(exactly = 2) { primingMapper.writeValueAsString(any()) }
        verify(exactly = 1) { primingMapper.readValue(any<String>(), HttpRequest::class.java) }

        // And the shared/injected mapper bean is never used directly for serialization (it is only copied),
        // so priming cannot mutate the application's ObjectMapper.
        verify(exactly = 0) { injected.writeValueAsString(any()) }

        clearMocks(injected, primingMapper)
    }

    /**
     * **Validates: Requirements 2.4, 2.5**
     *
     * For any state of the application's dependency graph, `primeClasses()` SHALL complete without
     * throwing an exception that propagates to the caller. We vary the dependency-graph state by
     * randomizing the behaviour of the priming serializer (healthy, write-failure, read-failure)
     * across 100 iterations.
     */
    // Feature: deployment-upgrades-and-healthcheck, Property 1: Priming safety and failure isolation
    @RepeatedTest(100)
    fun `Given any dependency-graph state When primeClasses is invoked Then it never throws`(repetitionInfo: RepetitionInfo) {
        val random = Random(repetitionInfo.currentRepetition)
        val injected = mockk<ObjectMapper>()
        val primingMapper = spyk(jacksonObjectMapper())
        every { injected.copy() } returns primingMapper

        when (random.nextInt(3)) {
            // Broken serialization (write path) — must still be swallowed by runCatching.
            1 -> every { primingMapper.writeValueAsString(any()) } throws IOException("write failure")
            // Broken deserialization (read path).
            2 -> every { primingMapper.readValue(any<String>(), HttpRequest::class.java) } throws
                IllegalStateException("read failure")
            // else: healthy round-trip.
        }

        assertDoesNotThrow { ClassPreloader(injected).primeClasses() }

        clearMocks(injected, primingMapper)
    }
}
