# Implementation Plan: Cold-Start Optimization (ARM64, SnapStart, Priming) and Azure Flex Consumption

## Overview

This plan implements three scoped concerns: (1) a shared two-phase priming model in the application layer (network-free class pre-load + connection/credential warmup via a `Warmable` SPI), wired into AWS via a SnapStart CRaC hook and into Azure via a `@WarmupTrigger`; (2) the infrastructure changes that enable it — AWS Lambda ARM64 + SnapStart with a published-version API Gateway alias, and the Azure move from the Consumption (`Y1`) plan to Flex Consumption (`FC1`), which is the hard prerequisite for the Azure warmup trigger; and (3) the Event Grid-based blob-trigger migration that keeps the `ProcessDocument` document-processing path working on Flex Consumption (which supports only the Event Grid blob-trigger source, not the legacy polling source) by switching `ProcessDocument`'s `@BlobTrigger` to `source = BlobTriggerSource.EVENT_GRID`, declaring the `azure-functions-java-library` version that exposes `BlobTriggerSource`, and provisioning an Event Grid system topic + event subscription that routes `BlobCreated` events to the function app's blob-extension webhook. The existing JVM/Java 21 stack, Kotlin version, and dependencies are unchanged (aside from the explicit `azure-functions-java-library` pin), and no health check endpoints, pipeline health-check steps, or deployment checkpoints are introduced. The build order starts with the shared application-layer priming components, then the Warmable adapters and infrastructure (AWS CDK + Azure Flex Consumption), then the hooks/triggers that coordinate the phases, then the Event Grid blob-trigger migration, then tests, ending with a build/test verification task.

## Tasks

- [ ] 1. Implement two-phase priming components in the application layer
  - [x] 1.1 Create ClassPreloader (Phase 1, network-free)
    - Create `software/application/src/main/kotlin/com/example/clean/architecture/service/ClassPreloader.kt` as an `@Component`
    - Implement `primeClasses()` to warm serialization caches by round-tripping a sample `HttpRequest` and `HttpResponse` through the injected Jackson `ObjectMapper` / Spring Cloud Function message converters
    - Perform NO network or filesystem I/O — Phase 1 must be safe to run inside a SnapStart snapshot
    - Wrap the logic in `runCatching` so it never throws (non-fatal errors logged as warnings via KotlinLogging)
    - _Requirements: 2.4, 2.5, 2.6, 2.7_

  - [x] 1.2 Create Warmable SPI and WarmupCoordinator (Phase 2)
    - Create the `Warmable` SPI at `software/application/src/main/kotlin/com/example/clean/architecture/warmup/Warmable.kt` exposing a single `warmUp()` operation (functional interface)
    - Create `WarmupCoordinator` at `software/application/src/main/kotlin/com/example/clean/architecture/warmup/WarmupCoordinator.kt` as an `@Component` that injects `List<Warmable>` and exposes `warmUpConnections()` calling `warmUp()` on each, wrapped in per-adapter `runCatching` for failure isolation
    - Clean-architecture constraint: do NOT add `warmUp()` to `ObjectStorageInterface` or `DocumentNotificationInterface` — `Warmable` is a distinct application-layer SPI
    - _Requirements: 2.8, 2.9, 2.10_

  - [ ] 1.3 Write unit tests for ClassPreloader and WarmupCoordinator
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/service/ClassPreloaderTest.kt`: assert `primeClasses()` completes without throwing, performs no network/filesystem I/O (mocked collaborators assert no I/O calls), and round-trips the sample `HttpRequest`/`HttpResponse`
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/warmup/WarmupCoordinatorTest.kt`: assert `warmUpConnections()` calls `warmUp()` on every registered `Warmable`, and that one failing `Warmable` is isolated (others still warmed, no exception propagates)
    - **Property 1: Priming safety and failure isolation**
    - **Validates: Requirements 2.4, 2.5, 2.8, 2.10**

- [ ] 2. Implement AWS SnapStart priming hook and Warmable adapters (infra-aws)
  - [x] 2.1 Make AWS adapters implement Warmable
    - Add `Warmable` to the interface list of `S3ObjectStore` and implement `warmUp()` to call `headBucket` once (opens the connection, no payload)
    - Add `Warmable` to the interface list of `SESEmailSender` and implement `warmUp()` to call `getSendQuota` once (credential/connection warmup — must NOT send a real email)
    - _Requirements: 2.10_

  - [ ] 2.2 Create SnapStartPrimingHook with two-phase split
    - Create `software/infrastructure/aws/src/main/kotlin/com/example/clean/architecture/SnapStartPrimingHook.kt` implementing `org.crac.Resource`, registered with `Core.getGlobalContext()` in the `init` block
    - In `beforeCheckpoint()` call `classPreloader.primeClasses()` ONLY (Phase 1) — do NOT run any connection/credential warmup, since connections and tokens captured at checkpoint time are invalid after restore on another host
    - In `afterRestore()` call `warmupCoordinator.warmUpConnections()` (Phase 2) to re-establish connections on the serving host
    - Inject `ClassPreloader` and `WarmupCoordinator` as `private val` dependencies
    - Add the `org.crac:crac` dependency to `software/infrastructure/aws/build.gradle.kts`
    - _Requirements: 2.7, 2.11, 2.12_

  - [ ] 2.3 Write unit tests for AWS priming hook and adapters
    - Create tests in `software/infrastructure/aws/src/test/kotlin/com/example/clean/architecture/`
    - Assert `beforeCheckpoint()` calls `classPreloader.primeClasses()` and does NOT call `warmupCoordinator.warmUpConnections()` / any `Warmable.warmUp()`
    - Assert `afterRestore()` calls `warmupCoordinator.warmUpConnections()`
    - Assert `S3ObjectStore.warmUp()` invokes `headBucket` once and `SESEmailSender.warmUp()` invokes `getSendQuota` once (mocked clients, no real send)
    - **Property 2: Checkpoint phase isolation**
    - **Validates: Requirements 2.7, 2.10, 2.11, 2.12**

- [x] 3. Configure ARM64 architecture in AWS CDK stack
  - [x] 3.1 Set ARM64 for all Lambda definitions
    - Set `architectures` to `["arm64"]` for all Lambda function definitions in `cdk/aws/src/main/kotlin/com/example/cdk/aws/AwsStack.kt`
    - Run `cdk/aws/generateTerraform.sh` to regenerate the Terraform JSON
    - _Requirements: 2.1_

- [ ] 4. Enable SnapStart with published versions and API Gateway alias in AWS CDK stack
  - [ ] 4.1 Configure SnapStart and published-version integration
    - Add `snap_start` configuration with `apply_on = "PublishedVersions"` for all Lambda functions in `cdk/aws/src/main/kotlin/com/example/cdk/aws/AwsStack.kt`
    - Publish a new Lambda version on each deployment (e.g., using a source code hash) and wire the API Gateway integration to invoke the published version alias (required for SnapStart to take effect)
    - Run `cdk/aws/generateTerraform.sh` to regenerate the Terraform JSON
    - _Requirements: 2.2, 2.3_

- [ ] 5. Implement Azure warmup trigger and Warmable adapter (infra-azure)
  - [x] 5.1 Make BlobStorageObjectStore implement Warmable
    - Add `Warmable` to the interface list of `BlobStorageObjectStore` and implement `warmUp()` to call `containerClient.exists()`/`getProperties()` (forces the `DefaultAzureCredential` managed-identity token fetch)
    - Optionally add `Warmable` credential warmup to `ACSEmailSender` (credential warmup only — NOT a real send)
    - _Requirements: 2.10_

  - [ ] 5.2 Add Warmup function running both phases
    - Add a `Warmup` function with the `@WarmupTrigger` annotation in `software/infrastructure/azure/src/main/kotlin/com/example/clean/architecture/`
    - Inject `ClassPreloader` and `WarmupCoordinator` as `private val` dependencies and call BOTH `primeClasses()` (Phase 1) and `warmUpConnections()` (Phase 2) before the instance receives production traffic
    - _Requirements: 2.13_

  - [ ] 5.3 Write unit tests for Azure warmup function and adapter
    - Create tests in `software/infrastructure/azure/src/test/kotlin/com/example/clean/architecture/`
    - Assert the `Warmup` function invokes both `classPreloader.primeClasses()` and `warmupCoordinator.warmUpConnections()` (both phases)
    - Assert `BlobStorageObjectStore.warmUp()` invokes `exists()`/`getProperties()` once, forcing the credential fetch (mocked client)
    - _Requirements: 2.10, 2.13_

- [x] 6. Move Azure Function to Flex Consumption plan in Azure CDK stack
  - [x] 6.1 Switch service plan and function-app resource type to Flex Consumption
    - Change the service plan SKU from `Y1` to `FC1` in `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt`
    - Switch the function app to the flex-compatible resource type `azurerm_linux_function_app_flex_consumption` (replacing `azurerm_linux_function_app`)
    - Explicitly preserve all existing application settings (`APPINSIGHTS_INSTRUMENTATIONKEY`, `MAIN_CLASS`, `TriggerBlobStorage__accountName`, `TriggerBlobStorage__credential`, `WEBSITE_RUN_FROM_PACKAGE`, `ACS_ENDPOINT`), the SystemAssigned managed identity, and all role assignments (Storage Blob Data Contributor, Storage Account Contributor, Storage Queue Data Contributor, custom ACS role)
    - Retain the Linux OS type and the existing Java 21 application stack (no Java 25)
    - Run `cdk/azure/generateTerraform.sh` to regenerate the Terraform JSON
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 7. Migrate Azure blob trigger to the Event Grid source (Flex Consumption)
  - [ ] 7.1 Declare the `azure-functions-java-library` dependency exposing `BlobTriggerSource`
    - In `software/infrastructure/azure/build.gradle.kts`, declare `com.microsoft.azure.functions:azure-functions-java-library` **explicitly** (it is currently transitive via `spring-cloud-function-adapter-azure:4.2.2`), pinned to a version that exposes the `BlobTriggerSource` type with the `EVENT_GRID` source — verify the version that provides it (the `3.1.0+` line provides the `source` attribute / `BlobTriggerSource`)
    - Declaring it explicitly makes the event-based trigger a deliberate, verifiable build contract and prevents a transitive downgrade from silently breaking compilation of the `source = BlobTriggerSource.EVENT_GRID` reference
    - _Requirements: 3.3_

  - [ ] 7.2 Switch `ProcessDocument`'s `@BlobTrigger` to the Event Grid source
    - In `software/infrastructure/azure/src/main/kotlin/com/example/clean/architecture/azure/DocsFlowFunctions.kt`, add `source = BlobTriggerSource.EVENT_GRID` to the `@BlobTrigger` on `ProcessDocument` and add the import `com.microsoft.azure.functions.annotation.BlobTriggerSource`
    - Keep `name = "content"`, `path = "docs-flow/{name}"`, and `connection = "TriggerBlobStorage"` unchanged so the bound `content`/`name` parameters and the managed-identity connection setting continue to work (managed identity preserved — `TriggerBlobStorage__credential = "managedidentity"`, no shared access key)
    - _Requirements: 3.1, 3.2, 3.7_

  - [ ] 7.3 Add the Event Grid system topic and event subscription to the Azure CDK stack
    - In `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt`, add an `azurerm_eventgrid_system_topic` (`EventgridSystemTopic`) for the `docsflow` storage account: `topic_type = "Microsoft.Storage.StorageAccounts"`, `source_arm_resource_id` referencing the `docsflow` storage account, with `dependsOn(storageAccountDocsFlow)` (design §6c)
    - Add an `azurerm_eventgrid_system_topic_event_subscription` (`EventgridSystemTopicEventSubscription`): `included_event_types = ["Microsoft.Storage.BlobCreated"]`, `subject_filter.subject_begins_with = "/blobServices/default/containers/docs-flow/"`, and a `webhook_endpoint.url` targeting the function app's blob-extension endpoint `/runtime/webhooks/blobs?functionName=Host.Functions.ProcessDocument`, with `dependsOn(blobSystemTopic, functionApp)` (design §6c)
    - Reference the `blobs_extension` system key via the `azurerm_function_app_host_keys` data source (`blob_storage_extension_key`) and assemble the webhook URL from `functionApp.defaultHostname` + that referenced key — keep the key out of source/logs as a Terraform reference/interpolation (design §6d)
    - Run `cdk/azure/generateTerraform.sh` to regenerate the Terraform JSON
    - _Requirements: 3.4, 3.5, 3.7_

  - [ ]* 7.4 Write Azure infra wiring/annotation test for the Event Grid trigger source
    - Create a test in `software/infrastructure/azure/src/test/kotlin/com/example/clean/architecture/azure/` that uses reflection/annotation assertion on the `processDocument` parameter to assert `ProcessDocument`'s `@BlobTrigger` declares `source = BlobTriggerSource.EVENT_GRID` and retains `path = "docs-flow/{name}"` and `connection = "TriggerBlobStorage"`
    - Compiling the `BlobTriggerSource.EVENT_GRID` reference also verifies the explicit `azure-functions-java-library` build dependency contract (Requirement 3.3)
    - _Requirements: 3.1, 3.2_

  - [ ]* 7.5 Write CDK synth tests for the Event Grid routing in the Azure Terraform JSON
    - Add CDK synth tests that synthesize the Azure stack and assert the generated Terraform JSON contains an `azurerm_eventgrid_system_topic` with `topic_type = "Microsoft.Storage.StorageAccounts"` and `source_arm_resource_id` referencing the `docsflow` storage account (Requirement 3.4)
    - Assert the `azurerm_eventgrid_system_topic_event_subscription` has `included_event_types = ["Microsoft.Storage.BlobCreated"]`, `subject_filter.subject_begins_with = "/blobServices/default/containers/docs-flow/"`, a `webhook_endpoint.url` targeting `/runtime/webhooks/blobs?functionName=Host.Functions.ProcessDocument` on the function app hostname, and a `depends_on` that includes the system topic and function app (Requirement 3.5)
    - _Requirements: 3.4, 3.5_

- [ ] 8. Final checkpoint — Ensure the build compiles and all priming and Event Grid migration tests pass
  - Ensure `./gradlew build` compiles the `ClassPreloader`, `Warmable`, and `WarmupCoordinator` components and runs their unit tests (and the AWS/Azure adapter + hook/trigger tests) with zero failures
  - Ensure the Event Grid migration compiles — `:infra-azure` resolves `BlobTriggerSource.EVENT_GRID` against the explicitly declared `azure-functions-java-library`, and the `ProcessDocument` trigger-source wiring test and the Azure CDK synth tests for the system topic / event subscription pass
  - Ask the user if questions arise.
  - _Requirements: 2.14, 2.15, 3.1, 3.2, 3.3, 3.4, 3.5_

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; they cover the priming safety/wiring unit tests and the Event Grid trigger-source wiring + Azure CDK synth tests (tasks 7.4, 7.5).
- The design has a Correctness Properties section, so Property 1 (priming safety and failure isolation) and Property 2 (checkpoint phase isolation) are turned into property/invariant unit tests (run with varying `Warmable` sets and mocked collaborators). Requirement 3 (Event Grid trigger migration) adds no correctness property — it is a declarative-infrastructure + single trigger-source wiring change, so it is covered by example-based wiring tests and CDK synth tests only.
- Each task references the granular requirement clauses it satisfies for traceability.
- The Azure `@WarmupTrigger` (task 5.2) is only available on Premium/Flex Consumption plans, so the Flex Consumption move (task 6.1) is its functional prerequisite.
- The Event Grid blob-trigger migration (task group 7) is required because Flex Consumption supports only the Event Grid blob-trigger source, not the legacy polling source — the Flex move (task 6.1) is its prerequisite, and the trigger-source code change (7.2) plus the CDK Event Grid routing (7.3) must ship together for `ProcessDocument` to keep firing.
- After any CDK stack change, the appropriate `generateTerraform.sh` must be run to regenerate the Terraform JSON.
- The Event Grid blob-extension system key is never hard-coded or logged — it is referenced via the `azurerm_function_app_host_keys` data source as a Terraform interpolation (design §6d).
- No JVM/Kotlin/dependency upgrades (aside from the explicit `azure-functions-java-library` pin in task 7.1), no health check endpoints, and no pipeline health-check or deployment-checkpoint steps are part of this scope.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "5.1", "6.1", "7.1"] },
    { "id": 2, "tasks": ["2.2", "4.1", "5.2", "7.2", "7.3"] },
    { "id": 3, "tasks": ["1.3", "2.3", "5.3", "7.4", "7.5"] },
    { "id": 4, "tasks": ["8"] }
  ]
}
```
