package com.example.clean.architecture

import com.example.clean.architecture.service.ClassPreloader
import com.example.clean.architecture.warmup.WarmupCoordinator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.crac.Context
import org.crac.Core
import org.crac.Resource
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * AWS SnapStart (CRaC) priming hook that splits the two priming phases across the checkpoint
 * lifecycle:
 *
 * - [beforeCheckpoint] runs at publish time, before the snapshot is frozen, so it runs
 *   **Phase 1 only** ([ClassPreloader.primeClasses]). Connections and managed-identity tokens are
 *   unsafe to capture in a snapshot — anything opened now is invalid once the snapshot is restored
 *   on another host.
 * - [afterRestore] runs on the serving host after thaw, so it runs **Phase 2**
 *   ([WarmupCoordinator.warmUpConnections]) to (re)establish connections and fetch fresh
 *   credentials before the first request is handled.
 */
@Component
class SnapStartPrimingHook(
    private val classPreloader: ClassPreloader,
    private val warmupCoordinator: WarmupCoordinator,
) : Resource {
    init {
        Core.getGlobalContext().register(this)
    }

    override fun beforeCheckpoint(context: Context<out Resource>) {
        // Phase 1 ONLY — network-free. MUST NOT warm connections/credentials here:
        // anything opened now is invalid once the snapshot is restored on another host.
        logger.info { "beforeCheckpoint: running Phase 1 class preload only" }
        classPreloader.primeClasses()
    }

    override fun afterRestore(context: Context<out Resource>) {
        // Phase 2 — re-establish connections and fetch credentials on the serving host.
        logger.info { "afterRestore: running Phase 2 connection/credential warmup" }
        warmupCoordinator.warmUpConnections()
    }
}
