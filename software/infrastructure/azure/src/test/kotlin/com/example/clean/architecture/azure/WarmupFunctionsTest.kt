package com.example.clean.architecture.azure

import com.example.clean.architecture.service.ClassPreloader
import com.example.clean.architecture.warmup.WarmupCoordinator
import com.microsoft.azure.functions.ExecutionContext
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class WarmupFunctionsTest {

    private val classPreloader: ClassPreloader = mockk(relaxed = true)
    private val warmupCoordinator: WarmupCoordinator = mockk(relaxed = true)
    private val context: ExecutionContext = mockk(relaxed = true)

    private val warmupFunctions = WarmupFunctions(classPreloader, warmupCoordinator)

    @AfterEach
    fun tearDown() {
        clearMocks(classPreloader, warmupCoordinator, context)
    }

    @Test
    fun `Given a fresh instance When the Warmup trigger fires Then it runs Phase 1 class preload`() {
        // Given
        val warmupContext = Any()

        // When
        warmupFunctions.warmup(warmupContext, context)

        // Then
        verify(exactly = 1) { classPreloader.primeClasses() }
    }

    @Test
    fun `Given a fresh instance When the Warmup trigger fires Then it runs Phase 2 connection warmup`() {
        // Given
        val warmupContext = Any()

        // When
        warmupFunctions.warmup(warmupContext, context)

        // Then
        verify(exactly = 1) { warmupCoordinator.warmUpConnections() }
    }

    @Test
    fun `Given a fresh instance When the Warmup trigger fires Then it runs Phase 1 before Phase 2`() {
        // Given
        val warmupContext = Any()

        // When
        warmupFunctions.warmup(warmupContext, context)

        // Then both phases run, with the network-free class preload before the connection warmup
        verifyOrder {
            classPreloader.primeClasses()
            warmupCoordinator.warmUpConnections()
        }
    }
}
