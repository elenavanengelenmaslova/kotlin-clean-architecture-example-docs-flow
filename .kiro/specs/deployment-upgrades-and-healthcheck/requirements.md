# Requirements Document

## Introduction

This feature enables cold-start optimization for the Kotlin Clean Architecture project. The core work introduces ARM64 architecture, AWS Lambda SnapStart, and a two-phase priming model (network-free class pre-loading followed by connection/credential warmup) that minimizes cold start latency on AWS. The priming logic lives in the shared application layer so that the same components are reused by the Azure warmup trigger. Because the Azure `@WarmupTrigger` is only available on the Premium and Flex Consumption hosting plans (not the current Consumption `Y1` plan), this feature also includes moving the Azure Function to the Flex Consumption plan as a hard prerequisite for the Azure side of the priming work. The JVM, Kotlin, and dependency versions remain unchanged, and no new health check endpoints, pipeline health checks, or deployment checkpoints are introduced.

## Glossary

- **AWS_Lambda**: The AWS serverless compute service hosting the application via API Gateway
- **Azure_Function**: The Azure serverless compute service hosting the application
- **Flex_Consumption_Plan**: The Azure Functions hosting plan that provides per-second billing, virtual network integration, instance memory configuration, and support for the `@WarmupTrigger`
- **CDK_Stack**: The Terraform CDK infrastructure-as-code definition (written in Kotlin) that provisions cloud resources
- **SnapStart**: An AWS Lambda feature that reduces cold start latency by capturing a snapshot of the initialized execution environment at publish time and restoring it on a different host when a request arrives
- **CRaC**: Coordinated Restore at Checkpoint — the JVM mechanism underlying AWS Lambda SnapStart that exposes the `beforeCheckpoint` and `afterRestore` lifecycle hooks
- **beforeCheckpoint**: The AWS SnapStart (CRaC) lifecycle hook that runs once at publish time, before the snapshot is captured; the resulting snapshot is frozen and later restored on a different host, so open network connections and fetched tokens do not survive into the restored environment
- **afterRestore**: The AWS SnapStart (CRaC) lifecycle hook that runs after the snapshot is thawed on the serving host, before the first request is handled; this is the correct place to re-establish network connections
- **WarmupTrigger**: The Azure Functions trigger (`@WarmupTrigger`) that runs on every fresh function instance, on the real serving host, before the instance receives production traffic; it is only available on the Premium and Flex Consumption hosting plans
- **Class_Preload**: A network-free warmup phase (conceptually `primeClasses()`) that pre-loads application classes and warms serialization caches without performing any I/O; it is safe to execute in AWS SnapStart `beforeCheckpoint`, AWS SnapStart `afterRestore`, and Azure `@WarmupTrigger`
- **Warmable**: An application-layer service provider interface (SPI) that exposes a connection/credential warmup operation; infrastructure adapters may optionally implement it to open their network connection and/or fetch their managed-identity token before serving traffic
- **Priming**: The combined two-phase warmup model for reducing cold start latency, consisting of the network-free Class_Preload phase and the connection/credential warmup phase exposed via the Warmable SPI
- **ARM64**: The AWS Graviton processor architecture that provides improved price-performance for Lambda functions
- **CDKTF**: Cloud Development Kit for Terraform — a framework that allows defining Terraform infrastructure using general-purpose programming languages (Kotlin in this project) instead of HCL
- **Spring_Cloud_Function**: A Spring project that provides a programming model for implementing business logic via functions, with adapters for serverless platforms (AWS Lambda, Azure Functions)
- **SES**: AWS Simple Email Service used for sending notification emails
- **ACS**: Azure Communication Services used for sending notification emails
- **Event_Grid**: The Azure eventing service that routes discrete events (such as blob-created notifications) from a source to one or more subscribers using a publish-subscribe model
- **Event_Grid_System_Topic**: An Azure-managed Event Grid topic that represents events emitted by an Azure resource (here, the `docsflow` storage account) and serves as the source for event subscriptions
- **Event_Grid_Event_Subscription**: An Event Grid resource that selects events from a topic using event-type and subject filters and delivers the matching events to a configured destination endpoint
- **Event_Based_Blob_Trigger**: The Event Grid-sourced Azure Functions Blob Storage trigger, selected via `BlobTriggerSource.EVENT_GRID` on `@BlobTrigger`, which fires when Event Grid delivers a `Microsoft.Storage.BlobCreated` event; it is the only Blob Storage trigger source supported on the Flex Consumption plan
- **Polling_Blob_Trigger**: The legacy (default) Azure Functions Blob Storage trigger source that detects new blobs by periodically scanning the container and tracking receipts; it is not supported on the Flex Consumption plan
- **Blob_Extension_Endpoint**: The function app's built-in blob-extension webhook endpoint (`/runtime/webhooks/blobs`) that receives Event Grid blob-created events and dispatches them to the matching blob-triggered function
- **BlobCreated_Event**: The `Microsoft.Storage.BlobCreated` event type emitted by Azure Storage when a blob is written, used to trigger downstream processing

## Requirements

### Requirement 1: Move Azure Function to Flex Consumption Plan (Prerequisite for Azure Warmup Trigger)

**User Story:** As a developer, I want the Azure Function moved to the Flex Consumption plan, so that the Azure `@WarmupTrigger` required for cold-start priming becomes available, since the warmup trigger is only supported on the Premium and Flex Consumption plans and not on the current Consumption `Y1` plan.

#### Acceptance Criteria

1. THE CDK_Stack for Azure SHALL define the service plan with the Flex Consumption SKU (`FC1`) instead of the current Consumption SKU (`Y1`)
2. THE CDK_Stack for Azure SHALL define the function app using the Flex Consumption-compatible resource type (e.g., `azurerm_linux_function_app_flex_consumption`) instead of the current `azurerm_linux_function_app` resource type
3. WHEN the infrastructure is deployed, THE Azure_Function SHALL report its hosting plan SKU as `FC1` when queried via the Azure Resource Manager API or Azure Portal
4. THE CDK_Stack for Azure SHALL preserve all existing application settings (including `APPINSIGHTS_INSTRUMENTATIONKEY`, `MAIN_CLASS`, `TriggerBlobStorage__accountName`, `TriggerBlobStorage__credential`, `WEBSITE_RUN_FROM_PACKAGE`, and `ACS_ENDPOINT`), the SystemAssigned managed identity configuration, and all role assignments (Storage Blob Data Contributor, Storage Account Contributor, Storage Queue Data Contributor, and the custom ACS role) after the plan move
5. THE CDK_Stack for Azure SHALL retain the Linux OS type and the existing Java 21 application stack configuration after the plan move

### Requirement 2: ARM64 Architecture, SnapStart, and Priming Optimizations

**User Story:** As a developer, I want to enable ARM64 architecture, SnapStart, and a two-phase priming model that separates network-free class pre-loading from connection/credential warmup, so that cold start latency is minimized on both AWS and Azure deployments without performing unsafe I/O during the SnapStart checkpoint.

#### Acceptance Criteria

##### ARM64 and SnapStart Infrastructure

1. THE CDK_Stack for AWS SHALL set the `architectures` property to `["arm64"]` for all Lambda function definitions in the stack
2. THE CDK_Stack for AWS SHALL enable SnapStart by setting the `snap_start` configuration with `apply_on` set to `"PublishedVersions"` for all Lambda function definitions in the stack
3. THE CDK_Stack for AWS SHALL publish a new Lambda version on each deployment and configure the API Gateway integration to invoke the published version alias (required for SnapStart to take effect)

##### Phase 1: Network-Free Class Pre-Load

4. THE Application layer SHALL contain a reusable Class_Preload component that pre-loads application classes and warms serialization without performing any network or I/O calls
5. THE Class_Preload component SHALL warm the serialization caches by round-tripping a sample `HttpRequest` and `HttpResponse` through the Spring_Cloud_Function and Jackson message converters
6. THE Class_Preload component SHALL be implemented in the shared application layer so that AWS Lambda and Azure Function deployments invoke the same pre-loading logic
7. WHERE the AWS Lambda function uses SnapStart, THE AWS_Lambda SHALL execute the Class_Preload component during the SnapStart `beforeCheckpoint` hook

##### Phase 2: Connection and Credential Warmup via Warmable SPI

8. THE Application layer SHALL define a Warmable SPI that exposes a connection and credential warmup operation that infrastructure adapters may optionally implement
9. THE existing domain ports `ObjectStorageInterface` and `DocumentNotificationInterface` SHALL NOT declare the Warmable warmup operation
10. WHERE an infrastructure adapter implements the Warmable SPI, THE adapter SHALL open its network connection and, where applicable, fetch its managed-identity token using a single low-cost call (for example, S3 `headBucket`, SES `getSendQuota`, or Azure Blob container `exists()`/`getProperties()` which forces the `DefaultAzureCredential` token fetch)
11. IF the AWS Lambda function is executing the SnapStart `beforeCheckpoint` hook, THEN THE AWS_Lambda SHALL NOT execute the Warmable connection and credential warmup
12. WHEN the AWS Lambda function executes the SnapStart `afterRestore` hook, THE AWS_Lambda SHALL execute the Warmable connection and credential warmup to re-establish network connections on the serving host
13. WHEN a new Azure Function instance is started, THE Azure_Function SHALL execute a `@WarmupTrigger`-annotated function that invokes both the Class_Preload component and the Warmable connection and credential warmup before the instance receives production traffic

##### Verification

14. WHEN the AWS Lambda function cold-starts with ARM64, SnapStart, and Priming enabled, THE AWS_Lambda SHALL restore from the snapshot and respond successfully to the first authenticated request to the existing `docs-flow` endpoint (which requires API key authentication on AWS and function key authorization on Azure) within 10 seconds
15. WHEN the new priming components are built, THE build system SHALL compile the Class_Preload and Warmable components and execute their unit tests with zero failures

### Requirement 3: Migrate the Blob-Triggered Document Processing to the Event Grid Source for Flex Consumption

**User Story:** As a developer, I want the blob-triggered document-processing function to keep working after the move to Flex Consumption, so that uploaded documents continue to be processed, given that Flex Consumption only supports the Event Grid-based Blob Storage trigger (not the legacy polling trigger).

#### Acceptance Criteria

1. THE Azure_Function `ProcessDocument` Event_Based_Blob_Trigger SHALL declare the Event Grid trigger source (the `source = BlobTriggerSource.EVENT_GRID` attribute on `@BlobTrigger`) instead of the default Polling_Blob_Trigger source
2. THE Azure_Function `ProcessDocument` Event_Based_Blob_Trigger SHALL retain the existing container path `docs-flow/{name}` and the `TriggerBlobStorage` connection setting after the trigger source change
3. THE `:infra-azure` module SHALL declare the Azure Functions Java library dependency version that exposes the `BlobTriggerSource` type providing the `EVENT_GRID` source
4. THE CDK_Stack for Azure SHALL provision an Event_Grid_System_Topic for the `docsflow` storage account that emits `Microsoft.Storage.BlobCreated` events
5. THE CDK_Stack for Azure SHALL provision an Event_Grid_Event_Subscription that filters the Event_Grid_System_Topic to `Microsoft.Storage.BlobCreated` events scoped to the `docs-flow` container and delivers matching events to the Azure_Function Blob_Extension_Endpoint
6. WHEN a document is uploaded to the `docs-flow` container after deployment on the Flex_Consumption_Plan, THE Azure_Function SHALL invoke the `ProcessDocument` function via the Event_Grid_Event_Subscription and complete the existing review-and-notify flow
7. THE Azure_Function `ProcessDocument` trigger connection SHALL continue to authenticate to the storage account using the existing managed identity (the `TriggerBlobStorage__credential` setting value `managedidentity`) after the trigger source change
