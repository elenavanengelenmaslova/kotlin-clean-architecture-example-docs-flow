package com.example.clean.architecture.warmup

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Phase 2 priming: iterates all Warmable adapters and warms each one.
 * Each warmUp() is wrapped in runCatching so one failure does not abort the rest.
 */
@Component
class WarmupCoordinator(
    private val warmables: List<Warmable>,
) {
    fun warmUpConnections() {
        logger.info { "Phase 2 priming: warming up ${warmables.size} connection(s)/credential(s)" }
        warmables.forEach { warmable ->
            runCatching { warmable.warmUp() }
                .onFailure { e -> logger.warn(e) { "Warmable failed to warm up (non-fatal): ${warmable.javaClass.simpleName}" } }
        }
    }
}
