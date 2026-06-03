package com.example.clean.architecture.warmup

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * Unit + property tests for [WarmupCoordinator].
 *
 * **Validates: Requirements 2.8, 2.10**
 *
 * Property 1: Priming safety and failure isolation — the Phase 2 side of the property:
 * `warmUpConnections()` calls `warmUp()` on every registered [Warmable], and a failure in one
 * adapter is isolated (others still warmed, no exception propagates to the caller).
 */
class WarmupCoordinatorTest {

    @Test
    fun `Given no registered Warmables When warmUpConnections is invoked Then it completes without throwing`() {
        // Given an empty adapter set — Phase 2 is a no-op (Requirement 2.8).
        val coordinator = WarmupCoordinator(emptyList())

        // When / Then
        assertDoesNotThrow { coordinator.warmUpConnections() }
    }

    @Test
    fun `Given several registered Warmables When warmUpConnections is invoked Then warmUp is called on every one`() {
        // Given
        val first = mockk<Warmable>(relaxed = true)
        val second = mockk<Warmable>(relaxed = true)
        val third = mockk<Warmable>(relaxed = true)

        // When
        WarmupCoordinator(listOf(first, second, third)).warmUpConnections()

        // Then every registered adapter is warmed exactly once (Requirement 2.8).
        verify(exactly = 1) { first.warmUp() }
        verify(exactly = 1) { second.warmUp() }
        verify(exactly = 1) { third.warmUp() }
    }

    @Test
    fun `Given one failing Warmable When warmUpConnections is invoked Then the failure is isolated and the others are still warmed`() {
        // Given a healthy adapter, a failing adapter, and another healthy adapter.
        val healthyBefore = mockk<Warmable>(relaxed = true)
        val failing = Warmable { throw RuntimeException("connection refused") }
        val healthyAfter = mockk<Warmable>(relaxed = true)

        // When / Then — the failure must NOT propagate (Requirement 2.10).
        assertDoesNotThrow {
            WarmupCoordinator(listOf(healthyBefore, failing, healthyAfter)).warmUpConnections()
        }

        // And the adapters surrounding the failing one are still warmed.
        verify(exactly = 1) { healthyBefore.warmUp() }
        verify(exactly = 1) { healthyAfter.warmUp() }
    }

    /**
     * **Validates: Requirements 2.8, 2.10**
     *
     * For any set of registered `Warmable` adapters — including any subset that throws — every
     * `warmUp()` is invoked exactly once and no exception propagates to the caller. Each iteration
     * builds a randomly sized adapter set with a random subset configured to throw.
     */
    // Feature: deployment-upgrades-and-healthcheck, Property 1: Priming safety and failure isolation
    @RepeatedTest(100)
    fun `Given any set of Warmables When warmUpConnections is invoked Then every warmUp runs once and nothing propagates`(
        repetitionInfo: RepetitionInfo,
    ) {
        val random = Random(repetitionInfo.currentRepetition)
        val size = random.nextInt(0, 12)

        val invocationCounts = ArrayList<AtomicInteger>(size)
        val warmables = (0 until size).map {
            val counter = AtomicInteger(0)
            invocationCounts.add(counter)
            val shouldThrow = random.nextBoolean()
            Warmable {
                counter.incrementAndGet()
                if (shouldThrow) throw RuntimeException("warmup failure")
            }
        }

        // When / Then — failure isolation across any adapter set (Requirement 2.10).
        assertDoesNotThrow { WarmupCoordinator(warmables).warmUpConnections() }

        // Every registered adapter's warmUp() ran exactly once, throwing or not (Requirement 2.8).
        invocationCounts.forEachIndexed { index, counter ->
            assertEquals(1, counter.get(), "warmable #$index should have been warmed exactly once")
        }
    }
}
