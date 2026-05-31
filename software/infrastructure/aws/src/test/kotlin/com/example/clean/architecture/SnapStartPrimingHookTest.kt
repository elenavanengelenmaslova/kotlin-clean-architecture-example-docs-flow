package com.example.clean.architecture

import com.example.clean.architecture.service.ClassPreloader
import com.example.clean.architecture.warmup.Warmable
import com.example.clean.architecture.warmup.WarmupCoordinator
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.crac.Context
import org.crac.Resource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Verifies the AWS SnapStart (CRaC) priming hook splits the two priming phases correctly across
 * the checkpoint lifecycle, with the critical safety invariant that Phase 2
 * (connection/credential warmup) NEVER runs inside the snapshot (`beforeCheckpoint`).
 */
class SnapStartPrimingHookTest {

    private val classPreloader: ClassPreloader = mockk(relaxed = true)
    private val warmupCoordinator: WarmupCoordinator = mockk(relaxed = true)
    private val context: Context<Resource> = mockk(relaxed = true)

    private val hook = SnapStartPrimingHook(classPreloader, warmupCoordinator)

    @AfterEach
    fun tearDown() {
        clearMocks(classPreloader, warmupCoordinator, context)
    }

    @Test
    fun `Given the SnapStart checkpoint When beforeCheckpoint runs Then it primes classes (Phase 1)`() {
        // When
        hook.beforeCheckpoint(context)

        // Then — the network-free class preload runs at publish time
        verify(exactly = 1) { classPreloader.primeClasses() }
    }

    @Test
    fun `Given the SnapStart checkpoint When beforeCheckpoint runs Then it does NOT warm up connections (Phase 2)`() {
        // When
        hook.beforeCheckpoint(context)

        // Then — connection/credential warmup must never run inside the snapshot,
        // because anything opened now is invalid once restored on another host
        verify(exactly = 0) { warmupCoordinator.warmUpConnections() }
    }

    @Test
    fun `Given the SnapStart restore When afterRestore runs Then it warms up connections (Phase 2)`() {
        // When
        hook.afterRestore(context)

        // Then — connections/credentials are (re)established on the serving host after thaw
        verify(exactly = 1) { warmupCoordinator.warmUpConnections() }
    }

    @Test
    fun `Given the SnapStart restore When afterRestore runs Then it does NOT re-run the class preload`() {
        // When
        hook.afterRestore(context)

        // Then — Phase 1 is not repeated in afterRestore (it was already captured in the snapshot)
        verify(exactly = 0) { classPreloader.primeClasses() }
    }

    // Feature: deployment-upgrades-and-healthcheck, Property 2: Checkpoint phase isolation
    //
    // For ANY set of registered Warmable adapters, when beforeCheckpoint() executes it SHALL
    // invoke ClassPreloader.primeClasses() (Phase 1) and SHALL NOT invoke any Warmable.warmUp() /
    // WarmupCoordinator.warmUpConnections() (Phase 2). Exercised with a REAL WarmupCoordinator
    // wrapping a varying set of mocked Warmable adapters over >= 100 iterations.
    @Test
    fun `Property 2 - For any set of registered Warmable adapters beforeCheckpoint never invokes warmUp`() {
        val random = Random(42)
        val iterations = 100

        repeat(iterations) {
            // Given a varying set of registered Warmable adapters wired into a REAL coordinator
            val warmableCount = random.nextInt(0, 11)
            val warmables = List(warmableCount) { mockk<Warmable>(relaxed = true) }
            val realCoordinator = WarmupCoordinator(warmables)
            val preloader: ClassPreloader = mockk(relaxed = true)
            val ctx: Context<Resource> = mockk(relaxed = true)
            val hookUnderTest = SnapStartPrimingHook(preloader, realCoordinator)

            // When the checkpoint (snapshot) phase executes
            hookUnderTest.beforeCheckpoint(ctx)

            // Then Phase 1 runs exactly once ...
            verify(exactly = 1) { preloader.primeClasses() }
            // ... and NO registered adapter is warmed up during the checkpoint, regardless of
            // how many adapters are registered (Phase 2 is fully isolated from the snapshot)
            warmables.forEach { warmable ->
                verify(exactly = 0) { warmable.warmUp() }
            }
        }
    }
}
