# Implementation Plan: Cold-Start Optimization (ARM64, SnapStart, Priming) and Azure Flex Consumption

## Overview

This plan implements two scoped concerns: (1) a shared two-phase priming model in the application layer (network-free class pre-load + connection/credential warmup via a `Warmable` SPI), wired into AWS via a SnapStart CRaC hook and into Azure via a `@WarmupTrigger`; and (2) the infrastructure changes that enable it — AWS Lambda ARM64 + SnapStart with a published-version API Gateway alias, and the Azure move from the Consumption (`Y1`) plan to Flex Consumption (`FC1`), which is the hard prerequisite for the Azure warmup trigger. The existing JVM/Java 21 stack, Kotlin version, and dependencies are unchanged, and no health check endpoints, pipeline health-check steps, or deployment checkpoints are introduced. The build order starts with the shared application-layer priming components, then the Warmable adapters and infrastructure (AWS CDK + Azure Flex Consumption), then the hooks/triggers that coordinate the phases, then tests, ending with a build/test verification task.

## Tasks

- [ ] 1. Implement two-phase priming components in the application layer
  - [ ] 1.1 Create ClassPreloader (Phase 1, network-free)
    - Create `software/application/src/main/kotlin/com/example/clean/architecture/service/ClassPreloader.kt` as an `@Component`
    - Implement `primeClasses()` to warm serialization caches by round-tripping a sample `HttpRequest` and `HttpResponse` through the injected Jackson `ObjectMapper` / Spring Cloud Function message converters
    - Perform NO network or filesystem I/O — Phase 1 must be safe to run inside a SnapStart snapshot
    - Wrap the logic in `runCatching` so it never throws (non-fatal errors logged as warnings via KotlinLogging)
    - _Requirements: 2.4, 2.5, 2.6, 2.7_

  - [ ] 1.2 Create Warmable SPI and WarmupCoordinator (Phase 2)
    - Create the `Warmable` SPI at `software/application/src/main/kotlin/com/example/clean/architecture/warmup/Warmable.kt` exposing a single `warmUp()` operation (functional interface)
    - Create `WarmupCoordinator` at `software/application/src/main/kotlin/com/example/clean/architecture/warmup/WarmupCoordinator.kt` as an `@Component` that injects `List<Warmable>` and exposes `warmUpConnections()` calling `warmUp()` on each, wrapped in per-adapter `runCatching` for failure isolation
    - Clean-architecture constraint: do NOT add `warmUp()` to `ObjectStorageInterface` or `DocumentNotificationInterface` — `Warmable` is a distinct application-layer SPI
    - _Requirements: 2.8, 2.9, 2.10_

  - [ ]* 1.3 Write unit tests for ClassPreloader and WarmupCoordinator
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/service/ClassPreloaderTest.kt`: assert `primeClasses()` completes without throwing, performs no network/filesystem I/O (mocked collaborators assert no I/O calls), and round-trips the sample `HttpRequest`/`HttpResponse`
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/warmup/WarmupCoordinatorTest.kt`: assert `warmUpConnections()` calls `warmUp()` on every registered `Warmable`, and that one failing `Warmable` is isolated (others still warmed, no exception propagates)
    - **Property 1: Priming safety and failure isolation**
    - **Validates: Requirements 2.4, 2.5, 2.8, 2.10**

- [ ] 2. Implement AWS SnapStart priming hook and Warmable adapters (infra-aws)
  - [ ] 2.1 Make AWS adapters implement Warmable
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

  - [ ]* 2.3 Write unit tests for AWS priming hook and adapters
    - Create tests in `software/infrastructure/aws/src/test/kotlin/com/example/clean/architecture/`
    - Assert `beforeCheckpoint()` calls `classPreloader.primeClasses()` and does NOT call `warmupCoordinator.warmUpConnections()` / any `Warmable.warmUp()`
    - Assert `afterRestore()` calls `warmupCoordinator.warmUpConnections()`
    - Assert `S3ObjectStore.warmUp()` invokes `headBucket` once and `SESEmailSender.warmUp()` invokes `getSendQuota` once (mocked clients, no real send)
    - **Property 2: Checkpoint phase isolation**
    - **Validates: Requirements 2.7, 2.10, 2.11, 2.12**

- [ ] 3. Configure ARM64 architecture in AWS CDK stack
  - [ ] 3.1 Set ARM64 for all Lambda definitions
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
  - [ ] 5.1 Make BlobStorageObjectStore implement Warmable
    - Add `Warmable` to the interface list of `BlobStorageObjectStore` and implement `warmUp()` to call `containerClient.exists()`/`getProperties()` (forces the `DefaultAzureCredential` managed-identity token fetch)
    - Optionally add `Warmable` credential warmup to `ACSEmailSender` (credential warmup only — NOT a real send)
    - _Requirements: 2.10_

  - [ ] 5.2 Add Warmup function running both phases
    - Add a `Warmup` function with the `@WarmupTrigger` annotation in `software/infrastructure/azure/src/main/kotlin/com/example/clean/architecture/`
    - Inject `ClassPreloader` and `WarmupCoordinator` as `private val` dependencies and call BOTH `primeClasses()` (Phase 1) and `warmUpConnections()` (Phase 2) before the instance receives production traffic
    - _Requirements: 2.13_

  - [ ]* 5.3 Write unit tests for Azure warmup function and adapter
    - Create tests in `software/infrastructure/azure/src/test/kotlin/com/example/clean/architecture/`
    - Assert the `Warmup` function invokes both `classPreloader.primeClasses()` and `warmupCoordinator.warmUpConnections()` (both phases)
    - Assert `BlobStorageObjectStore.warmUp()` invokes `exists()`/`getProperties()` once, forcing the credential fetch (mocked client)
    - _Requirements: 2.10, 2.13_

- [ ] 6. Move Azure Function to Flex Consumption plan in Azure CDK stack
  - [ ] 6.1 Switch service plan and function-app resource type to Flex Consumption
    - Change the service plan SKU from `Y1` to `FC1` in `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt`
    - Switch the function app to the flex-compatible resource type `azurerm_linux_function_app_flex_consumption` (replacing `azurerm_linux_function_app`)
    - Explicitly preserve all existing application settings (`APPINSIGHTS_INSTRUMENTATIONKEY`, `MAIN_CLASS`, `TriggerBlobStorage__accountName`, `TriggerBlobStorage__credential`, `WEBSITE_RUN_FROM_PACKAGE`, `ACS_ENDPOINT`), the SystemAssigned managed identity, and all role assignments (Storage Blob Data Contributor, Storage Account Contributor, Storage Queue Data Contributor, custom ACS role)
    - Retain the Linux OS type and the existing Java 21 application stack (no Java 25)
    - Run `cdk/azure/generateTerraform.sh` to regenerate the Terraform JSON
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 7. Final checkpoint — Ensure the build compiles and all priming tests pass
  - Ensure `./gradlew build` compiles the `ClassPreloader`, `Warmable`, and `WarmupCoordinator` components and runs their unit tests (and the AWS/Azure adapter + hook/trigger tests) with zero failures; ask the user if questions arise.
  - _Requirements: 2.14, 2.15_

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; they cover the priming safety/wiring unit tests.
- The design has a Correctness Properties section, so Property 1 (priming safety and failure isolation) and Property 2 (checkpoint phase isolation) are turned into property/invariant unit tests (run with varying `Warmable` sets and mocked collaborators).
- Each task references the granular requirement clauses it satisfies for traceability.
- The Azure `@WarmupTrigger` (task 5.2) is only available on Premium/Flex Consumption plans, so the Flex Consumption move (task 6.1) is its functional prerequisite.
- After any CDK stack change, the appropriate `generateTerraform.sh` must be run to regenerate the Terraform JSON.
- No JVM/Kotlin/dependency upgrades, no health check endpoints, and no pipeline health-check or deployment-checkpoint steps are part of this scope.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "5.1", "6.1"] },
    { "id": 2, "tasks": ["2.2", "4.1", "5.2"] },
    { "id": 3, "tasks": ["1.3", "2.3", "5.3"] }
  ]
}
```
