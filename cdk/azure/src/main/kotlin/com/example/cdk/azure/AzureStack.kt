package com.example.cdk.azure

import com.hashicorp.cdktf.*
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsights
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsightsConfig
import com.hashicorp.cdktf.providers.azurerm.communication_service.CommunicationService
import com.hashicorp.cdktf.providers.azurerm.communication_service.CommunicationServiceConfig
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_function_app_host_keys.DataAzurermFunctionAppHostKeys
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_function_app_host_keys.DataAzurermFunctionAppHostKeysConfig
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroup
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroupConfig
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic.EventgridSystemTopic
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic.EventgridSystemTopicConfig
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic_event_subscription.EventgridSystemTopicEventSubscription
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic_event_subscription.EventgridSystemTopicEventSubscriptionConfig
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic_event_subscription.EventgridSystemTopicEventSubscriptionSubjectFilter
import com.hashicorp.cdktf.providers.azurerm.eventgrid_system_topic_event_subscription.EventgridSystemTopicEventSubscriptionWebhookEndpoint
import com.hashicorp.cdktf.providers.azurerm.function_app_flex_consumption.*
import com.hashicorp.cdktf.providers.azurerm.log_analytics_workspace.LogAnalyticsWorkspace
import com.hashicorp.cdktf.providers.azurerm.log_analytics_workspace.LogAnalyticsWorkspaceConfig
import com.hashicorp.cdktf.providers.azurerm.monitor_diagnostic_setting.MonitorDiagnosticSetting
import com.hashicorp.cdktf.providers.azurerm.monitor_diagnostic_setting.MonitorDiagnosticSettingConfig
import com.hashicorp.cdktf.providers.azurerm.monitor_diagnostic_setting.MonitorDiagnosticSettingEnabledLog
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProvider
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProviderConfig
import com.hashicorp.cdktf.providers.azurerm.provider.AzurermProviderFeatures
import com.hashicorp.cdktf.providers.azurerm.role_assignment.RoleAssignment
import com.hashicorp.cdktf.providers.azurerm.role_assignment.RoleAssignmentConfig
import com.hashicorp.cdktf.providers.azurerm.service_plan.ServicePlan
import com.hashicorp.cdktf.providers.azurerm.service_plan.ServicePlanConfig
import com.hashicorp.cdktf.providers.azurerm.storage_account.StorageAccount
import com.hashicorp.cdktf.providers.azurerm.storage_account.StorageAccountConfig
import com.hashicorp.cdktf.providers.azurerm.storage_container.StorageContainer
import com.hashicorp.cdktf.providers.azurerm.storage_container.StorageContainerConfig
import software.constructs.Construct


class AzureStack(scope: Construct, id: String) :
    TerraformStack(scope, id) {

    init {
        val azureClientIdVar = TerraformVariable(
            this,
            "AZURE_CLIENT_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure client ID")
                .build()
        )

        val azureSubscriptionIdVar = TerraformVariable(
            this,
            "AZURE_SUBSCRIPTION_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure subscription ID")
                .build()
        )

        val azureTenantIdVar = TerraformVariable(
            this,
            "AZURE_TENANT_ID",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure tenant ID")
                .build()
        )

        val azureResourceGroupNameVar = TerraformVariable(
            this,
            "AZURE_RESOURCE_GROUP_NAME",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Azure resource group name")
                .build()
        )

        val senderEmailVar = TerraformVariable(
            this,
            "AZURE_SENDER_EMAIL",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Sender email address for notifications")
                .build()
        )

        val recipientEmailVar = TerraformVariable(
            this,
            "RECIPIENT_EMAIL",
            TerraformVariableConfig.builder()
                .type("string")
                .description("Recipient email address for notifications")
                .build()
        )

        val resourceGroupName = azureResourceGroupNameVar.stringValue
        // Keep the function app name <= 32 chars. The Functions platform derives
        // the default host ID from the (slot) host name truncated to 32 chars;
        // a longer name risks host ID truncation/collisions and, on Functions v4,
        // a hard host shutdown. At 24 chars this name stays within the limit, so
        // no explicit AzureFunctionsWebHost__hostid override is needed.
        val functionAppName =
            "docs-flow-clean-arch-fun"
        val appServicePlanName =
            "clean_architecture_app_plan"


        // Configure the Azure Provider
        AzurermProvider(
            this,
            "Azure",
            AzurermProviderConfig.builder()
                .subscriptionId(azureSubscriptionIdVar.stringValue)
                .clientId(azureClientIdVar.stringValue)
                .tenantId(azureTenantIdVar.stringValue)
                .features(
                    mutableListOf(
                        AzurermProviderFeatures.builder().build()
                    )
                )
                .build()
        )

        // Configure Terraform Backend to Use Azure Blob Storage.
        // useAzureadAuth makes state access use Azure AD (the OIDC deploy
        // principal's "Storage Blob Data Contributor" role on the state
        // account) instead of fetching a shared account key — no long-lived
        // storage credential is used for state.
        AzurermBackend(
            this,
            AzurermBackendConfig.builder()
                .resourceGroupName("\${resource_group_name}")
                .storageAccountName("\${storage_account_name}")
                .containerName("cleanarchterraformstorage")
                .key("docs-flow-kscfunction/terraform.tfstate")
                .useAzureadAuth(true)
                .useOidc(true)
                .build()
        )
        // Reference the existing Resource Group
        val resourceGroup = DataAzurermResourceGroup(
            this,
            "ExistingResourceGroup",
            DataAzurermResourceGroupConfig.builder()
                .name(resourceGroupName) // Use existing resource group name
                .build()
        )

        // Create Storage Account for Blob Storage
        val storageAccountDocsFlow = StorageAccount(
            this,
            "docs-flow-sa",
            StorageAccountConfig.builder()
                .name("docsflow")  // ✅ Storage account name
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .accountTier("Standard")
                .accountReplicationType("LRS")
                .build()
        )

        // Create a Blob Storage Container for Docs Flow
        val storageContainer = StorageContainer(
            this,
            "docs-flow-container",
            StorageContainerConfig.builder()
                .name("docs-flow")  // Blob storage container name
                .storageAccountId(storageAccountDocsFlow.id)
                .containerAccessType("private")  // Private access for security
                .dependsOn(listOf(storageAccountDocsFlow))
                .build()
        )

        // Deployment-package container for the Flex Consumption function app.
        // Flex Consumption uploads the code package (via OneDeploy) into this
        // dedicated blob container; it must exist before deployment, otherwise
        // OneDeploy fails its StorageAccessibleCheck with "The specified
        // container does not exist". The function app's system-assigned
        // managed identity authenticates to it (no access key).
        val deploymentContainer = StorageContainer(
            this,
            "docs-flow-deployment-container",
            StorageContainerConfig.builder()
                .name("deploymentpackage")
                .storageAccountId(storageAccountDocsFlow.id)
                .containerAccessType("private")
                .dependsOn(listOf(storageAccountDocsFlow))
                .build()
        )

        // Create an App Service Plan (Flex Consumption)
        val servicePlan = ServicePlan(
            this, "CleanArchitectureAppServicePlan",
            ServicePlanConfig.builder()
                .dependsOn(listOf(resourceGroup))
                .name(appServicePlanName)
                .resourceGroupName(resourceGroup.name)
                .osType("Linux")
                .skuName("FC1")
                .location(resourceGroup.location)
                .build()
        )

        val logAnalyticsWorkspace = LogAnalyticsWorkspace(
            this,
            "DocsFlowLogAnalyticsWorkspace",
            LogAnalyticsWorkspaceConfig.builder()
                .name("docs-flow-logs")
                .location(resourceGroup.location)
                .resourceGroupName(resourceGroup.name)
                .sku("PerGB2018")
                .retentionInDays(30)
                .retentionInDays(30)
                .build()
        )

        // Create an Application Insights resource
        val appInsights = ApplicationInsights(
            this, "AppInsights",
            ApplicationInsightsConfig.builder()
                .name("docs-flow-spring-cloud-app-insights")
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .applicationType("java")
                .workspaceId(logAnalyticsWorkspace.id)
                .build()
        )

        val acsService = CommunicationService(
            this,
            "DocsFlowACS",
            CommunicationServiceConfig.builder()
                .name("docsflow-acs")  // must be globally unique
                .resourceGroupName(resourceGroup.name)
                .dataLocation("Europe")
                .build()
        )


        // Create the Function App (Flex Consumption)
        val functionApp = FunctionAppFlexConsumption(
            this, "DocsFlowSpringCloudFunctionApp",
            FunctionAppFlexConsumptionConfig.builder()
                .dependsOn(
                    listOf(
                        resourceGroup,
                        servicePlan,
                        appInsights,
                        deploymentContainer,
                    )
                )
                .name(functionAppName)
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .servicePlanId(servicePlan.id)
                // Flex Consumption hosts the code package in a blob container.
                // Authenticate to it with the function app's system-assigned
                // managed identity (RBAC) — NO storage account access key. The
                // identity is granted "Storage Blob Data Contributor" on the
                // docsflow account below, which covers this container.
                //
                // The endpoint MUST point at the docsflow account (where the
                // deploymentpackage container and the role assignment live),
                // NOT at AZURE_STORAGE_ACCOUNT_NAME (that secret names the
                // Terraform state account, on which the function app identity
                // has no role).
                .storageContainerType("blobContainer")
                .storageContainerEndpoint(
                    "${storageAccountDocsFlow.primaryBlobEndpoint}deploymentpackage"
                )
                .storageAuthenticationType("SystemAssignedIdentity")
                // Retain the existing Java 21 runtime (no Java 25). On the Flex
                // Consumption resource the application stack is expressed via the
                // top-level runtime_name / runtime_version instead of an
                // application_stack block.
                .runtimeName("java")
                .runtimeVersion("21")
                .siteConfig(
                    FunctionAppFlexConsumptionSiteConfig.builder()
                        .build()
                )
                .identity(
                    FunctionAppFlexConsumptionIdentity.builder()
                        .type("SystemAssigned")
                        .build() // ✅ Enables Managed Identity
                )
                .appSettings(
                    mapOf(
                        "APPINSIGHTS_INSTRUMENTATIONKEY" to appInsights.instrumentationKey,
                        "APPLICATIONINSIGHTS_ENABLE_AGENT" to "true",
                        "APPLICATIONINSIGHTS_INSTRUMENTATION_LOGGING_LEVEL" to "INFO",
                        "APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_LEVEL" to "ERROR",
                        "MAIN_CLASS" to "com.example.clean.architecture.Application",
                        // AzureWebJobsStorage is the host's own runtime storage
                        // (used for function indexing, leases, the
                        // azure-webjobs-secrets container, etc.). Authenticate
                        // with the function app's system-assigned managed
                        // identity (no shared key): the identity already holds
                        // "Storage Blob Data Contributor" and "Storage Queue
                        // Data Contributor" on the docsflow account.
                        //
                        // Use the `__accountName` form (matching
                        // TriggerBlobStorage__accountName below). The Functions
                        // tooling/runtime specifically probes for either a keyed
                        // `AzureWebJobsStorage` OR `AzureWebJobsStorage__accountName`;
                        // the `__blobServiceUri`/`__queueServiceUri` form alone is
                        // NOT recognized as host storage, which left the host
                        // without a usable identity-based connection and falling
                        // back to a (failing) shared-key connection. The
                        // blob/queue service URIs are kept for explicit endpoint
                        // resolution.
                        "AzureWebJobsStorage__accountName" to "docsflow",
                        "AzureWebJobsStorage__credential" to "managedidentity",
                        "AzureWebJobsStorage__blobServiceUri" to storageAccountDocsFlow.primaryBlobEndpoint,
                        "AzureWebJobsStorage__queueServiceUri" to storageAccountDocsFlow.primaryQueueEndpoint,
                        "TriggerBlobStorage__accountName" to "docsflow",
                        "TriggerBlobStorage__credential" to "managedidentity",
                        // NOTE: WEBSITE_RUN_FROM_PACKAGE is intentionally NOT set.
                        // It is a classic Consumption setting; Flex Consumption
                        // delivers code via its deployment-storage container +
                        // OneDeploy and REJECTS this app setting at deploy time
                        // (InvalidAppSettingsException "not supported with this SKU").
                        "ACS_ENDPOINT" to "https://${acsService.hostname}",
                        "AZURE_SENDER_EMAIL" to senderEmailVar.stringValue,
                        "RECIPIENT_EMAIL" to recipientEmailVar.stringValue,
                    )
                )
                .build()
        )
        // Assign Function App Storage Permissions (Read/Write)
        val functionAppBlobStorageRole = RoleAssignment(
            this,
            "DocsFlowFunctionAppBlobStorageRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccountDocsFlow.id)  // Assign access at the Storage Account level
                .roleDefinitionName("Storage Blob Data Contributor")  // Allows reading and writing blobs
                .principalId(functionApp.identity.principalId)  // Assign to Function App's Managed Identity
                .build()
        )

        val functionAppStorageContributorRole = RoleAssignment(
            this,
            "DocsFlowFunctionAppStorageContributorRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccountDocsFlow.id) // ✅ Give access to full Storage Account management
                .roleDefinitionName("Storage Account Contributor") // ✅ Allows creating/deleting tables
                .principalId(functionApp.identity.principalId) // ✅ Assign to Function App's Managed Identity
                .build()
        )

        // Add Queue Data Contributor role for the Function App to access queues
        RoleAssignment(
            this,
            "DocsFlowFunctionAppQueueContributorRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccountDocsFlow.id) // Assign access at the Storage Account level
                .roleDefinitionName("Storage Queue Data Contributor") // Allows reading, writing, and processing queue messages
                .principalId(functionApp.identity.principalId) // Assign to Function App's Managed Identity
                .build()
        )


        val acsCustomRole = com.hashicorp.cdktf.providers.azurerm.role_definition.RoleDefinition(
            this,
            "DocsFlowACSRoleDefinition",
            com.hashicorp.cdktf.providers.azurerm.role_definition.RoleDefinitionConfig.builder()
                .name("DocsFlowACSFunctionRole")
                .scope("/subscriptions/${azureSubscriptionIdVar.stringValue}")
                .permissions(
                    listOf(
                        com.hashicorp.cdktf.providers.azurerm.role_definition.RoleDefinitionPermissions.builder()
                            .actions(
                                listOf(
                                    "Microsoft.Communication/CommunicationServices/Read",
                                    "Microsoft.Communication/CommunicationServices/Write"
                                )
                            )
                            .notActions(emptyList())
                            .build()
                    )
                )
                .assignableScopes(listOf(acsService.id))
                .description("Custom role for Azure Function to send emails using ACS")
                .build()
        )

        RoleAssignment(
            this,
            "DocsFlowFunctionAppACSEmailSenderRole",
            RoleAssignmentConfig.builder()
                .scope(acsService.id)
                .roleDefinitionId(acsCustomRole.roleDefinitionResourceId)
                .principalId(functionApp.identity.principalId)
                .build()
        )

        // ---------------------------------------------------------------------
        // Event Grid-based blob trigger routing (Flex Consumption).
        //
        // Flex Consumption supports only the Event Grid-sourced Blob Storage
        // trigger, so the ProcessDocument function is driven by an Event Grid
        // system topic + event subscription that routes Microsoft.Storage
        // .BlobCreated events from the docsflow storage account to the function
        // app's built-in blob-extension webhook. (design §6c / §6d)
        // ---------------------------------------------------------------------

        // System topic representing the docsflow storage account as an event
        // source (Requirement 3.4).
        val blobSystemTopic = EventgridSystemTopic(
            this,
            "DocsFlowBlobSystemTopic",
            EventgridSystemTopicConfig.builder()
                .name("docsflow-blob-created-topic")
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .topicType("Microsoft.Storage.StorageAccounts")
                .sourceArmResourceId(storageAccountDocsFlow.id)
                .dependsOn(listOf(storageAccountDocsFlow))
                .build()
        )

        // Reference the function app's built-in `blobs_extension` system key via
        // the azurerm_function_app_host_keys data source. The key is consumed
        // only as a Terraform interpolation in the webhook URL below, so the
        // secret value is never hard-coded in source or written to logs.
        val functionAppHostKeys = DataAzurermFunctionAppHostKeys(
            this,
            "DocsFlowFunctionAppHostKeys",
            DataAzurermFunctionAppHostKeysConfig.builder()
                .name(functionApp.name)
                .resourceGroupName(resourceGroup.name)
                .dependsOn(listOf(functionApp))
                .build()
        )

        // Blob-extension webhook endpoint that Event Grid delivers BlobCreated
        // events to. The `code` query parameter is a Terraform reference to the
        // blobs_extension system key (never a literal value). (design §6d)
        val blobExtensionWebhookUrl =
            "https://${functionApp.defaultHostname}/runtime/webhooks/blobs" +
                "?functionName=Host.Functions.ProcessDocument" +
                "&code=\${${functionAppHostKeys.fqn}.blobs_extension_key}"

        // Event subscription filtering the system topic to BlobCreated events
        // scoped to the docs-flow container and delivering them to the function
        // app's blob-extension webhook (Requirement 3.5). Managed identity on the
        // trigger connection is preserved; this subscription only routes the
        // event notification (no shared access key is introduced).
        EventgridSystemTopicEventSubscription(
            this,
            "DocsFlowBlobEventSubscription",
            EventgridSystemTopicEventSubscriptionConfig.builder()
                .name("docsflow-process-document-sub")
                .systemTopic(blobSystemTopic.name)
                .resourceGroupName(resourceGroup.name)
                .includedEventTypes(listOf("Microsoft.Storage.BlobCreated"))
                .subjectFilter(
                    EventgridSystemTopicEventSubscriptionSubjectFilter.builder()
                        .subjectBeginsWith("/blobServices/default/containers/docs-flow/")
                        .build()
                )
                .webhookEndpoint(
                    EventgridSystemTopicEventSubscriptionWebhookEndpoint.builder()
                        .url(blobExtensionWebhookUrl)
                        .build()
                )
                .dependsOn(listOf(blobSystemTopic, functionApp))
                .build()
        )

        // Diagnostic setting on the system topic that streams Event Grid
        // DeliveryFailures to the existing Log Analytics workspace. This makes
        // the actual delivery status code (e.g. 401 from a stale blob-extension
        // webhook key) visible from the portal / Log Analytics instead of only
        // the metric counts, so a broken blob-trigger delivery can be diagnosed
        // without re-instrumenting.
        MonitorDiagnosticSetting(
            this,
            "DocsFlowBlobTopicDiagnostics",
            MonitorDiagnosticSettingConfig.builder()
                .name("eg-delivery-failures")
                .targetResourceId(blobSystemTopic.id)
                .logAnalyticsWorkspaceId(logAnalyticsWorkspace.id)
                .enabledLog(
                    listOf(
                        MonitorDiagnosticSettingEnabledLog.builder()
                            .category("DeliveryFailures")
                            .build()
                    )
                )
                .dependsOn(listOf(blobSystemTopic, logAnalyticsWorkspace))
                .build()
        )

        // Terraform outputs for pipeline health check steps
        TerraformOutput(
            this,
            "function_app_name",
            TerraformOutputConfig.builder()
                .value(functionApp.name)
                .description("The Azure Function App name")
                .build()
        )

        TerraformOutput(
            this,
            "resource_group_name",
            TerraformOutputConfig.builder()
                .value(resourceGroup.name)
                .description("The Azure resource group name")
                .build()
        )
    }
}
