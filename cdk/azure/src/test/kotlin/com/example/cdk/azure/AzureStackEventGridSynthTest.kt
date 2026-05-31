package com.example.cdk.azure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.hashicorp.cdktf.Testing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * CDK synth tests for the Event Grid routing added to [AzureStack] (task 7.5).
 *
 * The Azure stack is synthesized in-process via the cdktf [Testing] harness and
 * the resulting Terraform JSON is parsed and asserted against. This verifies the
 * declarative Event Grid infrastructure that keeps the `ProcessDocument`
 * blob-triggered function firing on the Flex Consumption plan, which supports
 * only the Event Grid-sourced blob trigger.
 *
 * Validates: Requirements 3.4, 3.5
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AzureStackEventGridSynthTest {

    private val mapper = ObjectMapper()
    private lateinit var synthesized: JsonNode

    @BeforeAll
    fun synthesizeStack() {
        val app = Testing.app()
        val stack = AzureStack(app, "test")
        val json = Testing.synth(stack)
        synthesized = mapper.readTree(json)
    }

    private fun resource(type: String): JsonNode {
        val resources = synthesized.path("resource").path(type)
        assertTrue(
            !resources.isMissingNode && resources.fieldNames().hasNext(),
            "Synthesized Terraform JSON should contain at least one '$type' resource",
        )
        // Return the single (first) instance of the resource type.
        return resources.fields().next().value
    }

    @Test
    fun `system topic routes BlobCreated events from the docsflow storage account`() {
        // Requirement 3.4: an azurerm_eventgrid_system_topic with
        // topic_type = "Microsoft.Storage.StorageAccounts" sourced from the
        // docsflow storage account.
        val systemTopic = resource("azurerm_eventgrid_system_topic")

        assertEquals(
            "Microsoft.Storage.StorageAccounts",
            systemTopic.path("topic_type").asText(),
            "System topic must use the Storage Accounts topic type",
        )

        val sourceArmResourceId = systemTopic.path("source_arm_resource_id").asText()
        assertTrue(
            sourceArmResourceId.contains("azurerm_storage_account.docs-flow-sa"),
            "source_arm_resource_id must reference the docsflow storage account, but was: $sourceArmResourceId",
        )
    }

    @Test
    fun `event subscription filters BlobCreated events for the docs-flow container and targets the blob-extension webhook`() {
        // Requirement 3.5: the event subscription filters BlobCreated events
        // scoped to the docs-flow container and delivers them to the function
        // app's blob-extension webhook, depending on the topic and function app.
        val subscription = resource("azurerm_eventgrid_system_topic_event_subscription")

        // included_event_types = ["Microsoft.Storage.BlobCreated"]
        val includedEventTypes = subscription.path("included_event_types")
        assertTrue(includedEventTypes.isArray, "included_event_types must be an array")
        assertEquals(1, includedEventTypes.size(), "exactly one included event type expected")
        assertEquals(
            "Microsoft.Storage.BlobCreated",
            includedEventTypes.get(0).asText(),
            "subscription must filter to BlobCreated events",
        )

        // subject_filter.subject_begins_with scopes to the docs-flow container
        assertEquals(
            "/blobServices/default/containers/docs-flow/",
            subscription.path("subject_filter").path("subject_begins_with").asText(),
            "subject filter must scope to the docs-flow container",
        )

        // webhook_endpoint.url targets the blob-extension endpoint on the
        // function app hostname for the ProcessDocument function.
        val webhookUrl = subscription.path("webhook_endpoint").path("url").asText()
        assertNotNull(webhookUrl, "webhook endpoint url must be present")
        assertTrue(
            webhookUrl.contains("/runtime/webhooks/blobs?functionName=Host.Functions.ProcessDocument"),
            "webhook url must target the blob-extension endpoint for ProcessDocument, but was: $webhookUrl",
        )
        assertTrue(
            webhookUrl.contains("azurerm_function_app_flex_consumption.DocsFlowSpringCloudFunctionApp")
                && webhookUrl.contains("default_hostname"),
            "webhook url must be built from the function app's default hostname, but was: $webhookUrl",
        )

        // depends_on must include both the system topic and the function app.
        val dependsOn = subscription.path("depends_on")
        assertTrue(dependsOn.isArray, "depends_on must be an array")
        val dependencies = dependsOn.map { it.asText() }
        assertTrue(
            dependencies.any { it.contains("azurerm_eventgrid_system_topic.") },
            "depends_on must include the system topic, but was: $dependencies",
        )
        assertTrue(
            dependencies.any { it.contains("azurerm_function_app_flex_consumption.") },
            "depends_on must include the function app, but was: $dependencies",
        )
    }
}
