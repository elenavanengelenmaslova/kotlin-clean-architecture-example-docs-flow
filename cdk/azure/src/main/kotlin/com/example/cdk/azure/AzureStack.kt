package com.example.cdk.azure

import com.hashicorp.cdktf.AzurermBackend
import com.hashicorp.cdktf.AzurermBackendConfig
import com.hashicorp.cdktf.TerraformStack
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsights
import com.hashicorp.cdktf.providers.azurerm.application_insights.ApplicationInsightsConfig
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroup
import com.hashicorp.cdktf.providers.azurerm.data_azurerm_resource_group.DataAzurermResourceGroupConfig
import com.hashicorp.cdktf.providers.azurerm.linux_function_app.*
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
import com.hashicorp.cdktf.providers.azurerm.storage_table.StorageTable
import com.hashicorp.cdktf.providers.azurerm.storage_table.StorageTableConfig
import software.constructs.Construct


class AzureStack(scope: Construct, id: String) :
    TerraformStack(scope, id) {

    init {
        val resourceGroupName =
            "DefaultResourceGroup-WEU"
        val functionAppName =
            "demo-spring-clean-architecture-fun"
        val appServicePlanName =
            "demo_serverless_app_plan"
        val location = "westeurope"

        // Configure the Azure Provider
        AzurermProvider(
            this,
            "Azure",
            AzurermProviderConfig.builder()
                .subscriptionId(System.getenv("AZURE_SUBSCRIPTION_ID"))
                .clientId(System.getenv("AZURE_CLIENT_ID"))
                .clientSecret(System.getenv("AZURE_CLIENT_SECRET"))
                .tenantId(System.getenv("AZURE_TENANT_ID"))
                .features(
                    mutableListOf(
                        AzurermProviderFeatures.builder()
                            .build()
                    )
                )  // Empty features block as required
                .build()
        )


        // Configure Terraform Backend to Use Azure Blob Storage
        AzurermBackend(
            this,
            AzurermBackendConfig.builder()
                .resourceGroupName(resourceGroupName)
                .storageAccountName(System.getenv("AZURE_STORAGE_ACCOUNT_NAME"))
                .containerName("vintikterraformstorage")
                .key("demo-kscfunction/terraform.tfstate")
                .clientId(System.getenv("AZURE_CLIENT_ID"))
                .clientSecret(System.getenv("AZURE_CLIENT_SECRET"))
                .subscriptionId(System.getenv("AZURE_SUBSCRIPTION_ID"))
                .tenantId(System.getenv("AZURE_TENANT_ID"))
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
        val storageAccount = StorageAccount(
            this,
            "demo-mocknest-sa",
            StorageAccountConfig.builder()
                .name("demomocknest")  // ✅ Storage account name
                .resourceGroupName(resourceGroup.name)
                .location(resourceGroup.location)
                .accountTier("Standard")
                .accountReplicationType("LRS")
                .build()
        )

        // Create a Blob Storage Container for MockNest Mappings
        val storageContainer = StorageContainer(
            this,
            "demo-mocknest-container",
            StorageContainerConfig.builder()
                .name("demo-mocknest-mappings")  // Blob storage container name
                .storageAccountName(storageAccount.name)
                .containerAccessType("private")  // Private access for security
                .dependsOn(listOf(storageAccount))
                .build()
        )

        // Create an App Service Plan
        val servicePlan = ServicePlan(
            this, "DemoSpringCloudExampleServicePlan",
            ServicePlanConfig.builder()
                .dependsOn(listOf(resourceGroup))
                .name(appServicePlanName)
                .resourceGroupName(resourceGroup.name)
                .osType("Linux")
                .skuName("Y1")
                .location(location)
                .build()
        )

        // Create an Application Insights resource
        val appInsights = ApplicationInsights(
            this, "AppInsights",
            ApplicationInsightsConfig.builder()
                .name("demo-spring-cloud-app-insights")
                .resourceGroupName(resourceGroup.name)
                .location(location)
                .applicationType("java")
                .build()
        )

        val storageAccountName =
            System.getenv("AZURE_STORAGE_ACCOUNT_NAME")
                ?: "functionpackages"

        val storageAccountAccessKey =
            System.getenv("AZURE_STORAGE_ACCOUNT_ACCESS_KEY")
        checkNotNull(storageAccountAccessKey)

        // Create the Function App
        val functionApp = LinuxFunctionApp(
            this, "DemoSpringCloudFunctionApp",
            LinuxFunctionAppConfig.builder()
                .dependsOn(
                    listOf(
                        resourceGroup,
                        servicePlan,
                        appInsights,
                    )
                )
                .name(functionAppName)
                .resourceGroupName(resourceGroup.name)
                .location(location)
                .servicePlanId(servicePlan.id)
                .storageAccountName(storageAccountName)
                .storageAccountAccessKey(
                    storageAccountAccessKey
                )
                .siteConfig(
                    LinuxFunctionAppSiteConfig.builder()
                        .applicationStack(
                            LinuxFunctionAppSiteConfigApplicationStack.builder()
                                .javaVersion("17")
                                .build()
                        ).build()
                )
                .identity(
                    LinuxFunctionAppIdentity.builder()
                        .type("SystemAssigned")
                        .build() // ✅ Enables Managed Identity
                )
                .appSettings(
                    mapOf(
                        "MAIN_CLASS" to "com.example.clean.architecture.Application",
                        "APPINSIGHTS_INSTRUMENTATIONKEY" to appInsights.instrumentationKey,
                        "WEBSITE_RUN_FROM_PACKAGE" to "1",
                    )
                )
                .build()
        )
        // Assign Function App Storage Permissions (Read/Write)
        val functionAppBlobStorageRole = RoleAssignment(
            this,
            "DemoFunctionAppBlobStorageRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccount.id)  // Assign access at the Storage Account level
                .roleDefinitionName("Storage Blob Data Contributor")  // Allows reading and writing blobs
                .principalId(functionApp.identity.principalId)  // Assign to Function App's Managed Identity
                .build()
        )

        val functionAppStorageContributorRole = RoleAssignment(
            this,
            "DemoFunctionAppStorageContributorRole",
            RoleAssignmentConfig.builder()
                .scope(storageAccount.id) // ✅ Give access to full Storage Account management
                .roleDefinitionName("Storage Account Contributor") // ✅ Allows creating/deleting tables
                .principalId(functionApp.identity.principalId) // ✅ Assign to Function App's Managed Identity
                .build()
        )

    }
}
