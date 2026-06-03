package com.example.clean.architecture.warmup

/**
 * Phase 2 priming SPI. Infrastructure adapters MAY implement this to open their
 * network connection and/or fetch their managed-identity token before serving traffic.
 *
 * This is intentionally separate from the domain ports (ObjectStorageInterface,
 * DocumentNotificationInterface), which MUST NOT declare warmUp().
 */
fun interface Warmable {
    fun warmUp()
}
