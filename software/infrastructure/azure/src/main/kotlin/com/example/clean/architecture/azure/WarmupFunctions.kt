package com.example.clean.architecture.azure

import com.example.clean.architecture.service.ClassPreloader
import com.example.clean.architecture.warmup.WarmupCoordinator
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.WarmupTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Azure `@WarmupTrigger` function. It runs on every fresh function instance, on the real serving
 * host, before the instance receives production traffic, so it safely runs BOTH priming phases:
 *
 *  - Phase 1 ([ClassPreloader.primeClasses]) — network-free class pre-load + serialization warmup.
 *  - Phase 2 ([WarmupCoordinator.warmUpConnections]) — opens connections / fetches the
 *    managed-identity token for each [com.example.clean.architecture.warmup.Warmable] adapter.
 *
 * The warmup trigger is only available on the Premium and Flex Consumption hosting plans.
 */
@Component
class WarmupFunctions(
    private val classPreloader: ClassPreloader,
    private val warmupCoordinator: WarmupCoordinator,
) {

    @FunctionName("Warmup")
    fun warmup(
        @WarmupTrigger(name = "warmupTrigger") warmupContext: Any,
        context: ExecutionContext,
    ) {
        logger.info {
            "Warmup trigger invoked: running Phase 1 (class preload) and Phase 2 (connection/credential warmup)"
        }
        classPreloader.primeClasses()
        warmupCoordinator.warmUpConnections()
    }
}
