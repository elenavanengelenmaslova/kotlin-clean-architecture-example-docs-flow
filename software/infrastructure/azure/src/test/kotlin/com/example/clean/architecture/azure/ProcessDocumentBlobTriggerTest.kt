package com.example.clean.architecture.azure

import com.microsoft.azure.functions.annotation.BlobTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Wiring test for Requirements 3.1 and 3.2.
 *
 * Uses pure reflection on the `@BlobTrigger` annotation declared on the `content` parameter of
 * [DocsFlowFunctions.processDocument]. No instance is created — the annotation is read directly
 * from the method's parameter annotations (the annotation is `@Retention(RUNTIME)`, so it is
 * reflectively visible).
 *
 * The actual Java library form uses a String `source = "EventGrid"` attribute on `@BlobTrigger`
 * (the azure-functions-java-library does NOT ship a `BlobTriggerSource` enum), so the Event Grid
 * source is asserted against the String "EventGrid".
 */
class ProcessDocumentBlobTriggerTest {

    private fun processDocumentBlobTrigger(): BlobTrigger {
        // When: reflectively locating the processDocument method and its @BlobTrigger parameter annotation
        val method = DocsFlowFunctions::class.java.declaredMethods
            .firstOrNull { it.name == "processDocument" }
        assertNotNull(method, "processDocument method should exist on DocsFlowFunctions")

        val blobTrigger = method!!.parameterAnnotations
            .flatMap { it.asList() }
            .filterIsInstance<BlobTrigger>()
            .firstOrNull()
        assertNotNull(blobTrigger, "processDocument should declare a @BlobTrigger parameter annotation")
        return blobTrigger!!
    }

    @Test
    fun `Given ProcessDocument When inspecting BlobTrigger Then declares the Event Grid source`() {
        // Given / When
        val blobTrigger = processDocumentBlobTrigger()

        // Then: the Event Grid source is declared via the String "EventGrid" attribute (Requirement 3.1)
        assertEquals("EventGrid", blobTrigger.source, "@BlobTrigger source should declare the Event Grid source")
    }

    @Test
    fun `Given ProcessDocument When inspecting BlobTrigger Then retains the docs-flow container path`() {
        // Given / When
        val blobTrigger = processDocumentBlobTrigger()

        // Then: the existing container path is retained after the trigger source change (Requirement 3.2)
        assertEquals("docs-flow/{name}", blobTrigger.path, "@BlobTrigger path should remain docs-flow/{name}")
    }

    @Test
    fun `Given ProcessDocument When inspecting BlobTrigger Then retains the TriggerBlobStorage connection`() {
        // Given / When
        val blobTrigger = processDocumentBlobTrigger()

        // Then: the managed-identity connection setting is retained after the trigger source change (Requirement 3.2)
        assertEquals(
            "TriggerBlobStorage",
            blobTrigger.connection,
            "@BlobTrigger connection should remain TriggerBlobStorage",
        )
    }

    @Test
    fun `Given ProcessDocument When inspecting BlobTrigger Then retains the content binding name`() {
        // Given / When
        val blobTrigger = processDocumentBlobTrigger()

        // Then: the binding name is retained so the bound content parameter keeps working (Requirement 3.2)
        assertEquals("content", blobTrigger.name, "@BlobTrigger name should remain content")
    }
}
