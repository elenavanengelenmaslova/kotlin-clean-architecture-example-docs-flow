# Implementation Plan: Deployment Upgrades and Health Check

## Overview

This implementation plan deploys changes in three sequential checkpoints, each verified by a health check gate. The approach ensures each stage is operational before proceeding: (1) email config + health check infrastructure, (2) Kotlin/JVM/dependency upgrades + Flex Consumption, (3) ARM64 + SnapStart + priming optimizations. The pipeline structure uses reusable workflows with sequential jobs connected by `needs:` dependencies.

## Tasks

- [x] 1. Configure email addresses and implement health check in application layer
  - [x] 1.1 Replace hardcoded email defaults in application.properties
    - Remove `REPLACEME` default values from `software/infrastructure/aws/src/main/resources/application.properties` for `aws.ses.sender-email` and `aws.ses.recipient-email` — use `${AWS_SENDER_EMAIL}` and `${RECIPIENT_EMAIL}` without defaults
    - Remove `REPLACEME` default values from `software/infrastructure/azure/src/main/resources/application.properties` for `azure.acs.sender-email` and `azure.acs.recipient-email` — use `${AZURE_SENDER_EMAIL}` and `${RECIPIENT_EMAIL}` without defaults
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 Implement HealthCheckFunction bean in the application layer
    - Create `software/application/src/main/kotlin/com/example/clean/architecture/service/HealthCheckFunction.kt`
    - Implement `HealthCheckConfig` with a `@Bean` method returning `Supplier<HealthStatus>`
    - Create `HealthStatus` data class with `status: String` field
    - Register `healthCheck` in `spring.cloud.function.definition` in application.properties
    - _Requirements: 2.1, 3.1_

  - [x] 1.3 Write unit tests for HealthCheckFunction
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/service/HealthCheckFunctionTest.kt`
    - Test that `healthCheck` supplier returns `HealthStatus("UP")`
    - Test idempotency: multiple invocations return the same result
    - **Property 1: Health check idempotency**
    - **Validates: Requirements 2.1, 3.1**

- [ ] 2. Implement health check in AWS infrastructure
  - [x] 2.1 Add health check Lambda configuration to AWS CDK stack
    - Modify `cdk/aws/src/main/kotlin/com/example/cdk/aws/AwsStack.kt`
    - Add a new Lambda function resource with `SPRING_CLOUD_FUNCTION_DEFINITION` set to `healthCheck`
    - Add API Gateway resource at `/health` with GET method and API key required
    - Add Terraform outputs for `api_gateway_url` and `api_key_name`
    - Pass `AWS_SENDER_EMAIL` and `RECIPIENT_EMAIL` as Lambda environment variables from Terraform variables
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 1.6, 2.3, 2.4, 2.5_

  - [ ] 2.2 Write unit tests for AWS health check Lambda handler
    - Create test in `software/infrastructure/aws/src/test/kotlin/com/example/clean/architecture/`
    - Test that the health check handler returns HTTP 200 with `{"status":"UP"}`
    - Test that an internal error returns HTTP 503 with `{"status":"DOWN"}`
    - _Requirements: 2.1, 2.2_

- [ ] 3. Implement health check in Azure infrastructure
  - [x] 3.1 Add health check HTTP trigger function to Azure infra module
    - Add `Health` function with `@HttpTrigger` (GET, route="health", authLevel=FUNCTION) in `software/infrastructure/azure/src/main/kotlin/com/example/clean/architecture/`
    - Return HTTP 200 with `{"status":"UP"}` on success, HTTP 503 with `{"status":"DOWN"}` on error
    - Use `runCatching` for error handling
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 3.2 Add email environment variables to Azure CDK stack
    - Modify `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt`
    - Pass `AZURE_SENDER_EMAIL` and `RECIPIENT_EMAIL` as function app application settings from Terraform variables
    - Add Terraform outputs for `function_app_name` and `resource_group_name`
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 1.7, 1.8_

  - [ ] 3.3 Write unit tests for Azure health check function
    - Create test in `software/infrastructure/azure/src/test/kotlin/com/example/clean/architecture/`
    - Test that the health function returns HTTP 200 with `{"status":"UP"}`
    - Test that an internal error returns HTTP 503 with `{"status":"DOWN"}`
    - _Requirements: 3.1, 3.4_

- [ ] 4. Add post-deployment health check to AWS pipeline
  - [x] 4.1 Add health check step to `workflow-build-deploy-aws.yml`
    - After Terraform apply, add step to retrieve API Gateway URL from Terraform output
    - Add step to retrieve API key dynamically using `aws apigateway get-api-keys`
    - Add health check step with curl: 3 retries, 10s delay, 30s timeout
    - Fail the workflow if all retries are exhausted
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 5. Add post-deployment health check to Azure pipeline
  - [x] 5.1 Add health check step to `workflow-build-deploy-azure.yml`
    - After `azureFunctionsDeploy`, add step to retrieve Function URL via `az functionapp show`
    - Add step to retrieve function key via `az functionapp keys list`
    - Add health check step with curl: 3 retries, 10s delay, 30s timeout
    - Fail the workflow if all retries are exhausted and report last status code
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 6. Implement Deployment Checkpoint 1 in pipeline
  - [ ] 6.1 Restructure AWS reusable workflow into checkpoint jobs
    - Refactor `workflow-build-deploy-aws.yml` to have a `checkpoint-1` job that includes build, deploy-infra, deploy-app, and health check
    - Add `checkpoint-2` and `checkpoint-3` job stubs with `needs: [checkpoint-1]` and `needs: [checkpoint-2]` respectively
    - Ensure `AWS_SENDER_EMAIL` and `RECIPIENT_EMAIL` secrets are passed as Terraform variables and Lambda environment variables
    - _Requirements: 1.8, 11.1, 11.4, 11.5_

  - [ ] 6.2 Restructure Azure reusable workflow into checkpoint jobs
    - Refactor `workflow-build-deploy-azure.yml` to have a `checkpoint-1` job that includes deploy-infra, deploy-app, and health check
    - Add `checkpoint-2` and `checkpoint-3` job stubs with `needs: [checkpoint-1]` and `needs: [checkpoint-2]` respectively
    - Ensure `AZURE_SENDER_EMAIL` and `RECIPIENT_EMAIL` secrets are passed as Terraform variables and app settings
    - _Requirements: 1.8, 11.1, 11.4, 11.5_

- [ ] 7. Checkpoint 1 — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Upgrade Kotlin to 2.3.20
  - [ ] 8.1 Update Kotlin version in root build.gradle.kts
    - Change `kotlin("jvm") version "2.1.0"` to `kotlin("jvm") version "2.3.20"`
    - Change `kotlin("plugin.spring") version "2.1.0"` to `kotlin("plugin.spring") version "2.3.20"`
    - Verify no submodule overrides the Kotlin version
    - _Requirements: 7.1, 7.2, 7.5_

- [ ] 9. Upgrade dependencies and libraries
  - [ ] 9.1 Upgrade CDKTF and provider libraries
    - Update `com.hashicorp:cdktf` to latest stable in `cdk/aws/build.gradle.kts` and `cdk/azure/build.gradle.kts`
    - Update `com.hashicorp:cdktf-provider-aws` to latest stable in `cdk/aws/build.gradle.kts`
    - Update `com.hashicorp:cdktf-provider-random` to latest stable in `cdk/aws/build.gradle.kts`
    - Update `software.constructs:constructs` to latest stable in both CDK build files
    - Update `com.hashicorp:cdktf-provider-azurerm` to latest stable in `cdk/azure/build.gradle.kts`
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ] 9.2 Upgrade Spring Boot and Spring Cloud
    - Update `org.springframework.boot` plugin to latest stable 3.5.x in root `build.gradle.kts`
    - Update `io.spring.dependency-management` plugin to latest compatible version
    - Update Spring Boot BOM version in root `build.gradle.kts` `configure<DependencyManagementExtension>`
    - Update `springCloudVersion` in `software/infrastructure/azure/build.gradle.kts` to latest compatible with Spring Boot 3.5.x
    - Update `spring-cloud-function-adapter-aws` and `spring-cloud-function-adapter-azure` to latest compatible version
    - Update `spring-cloud-function-kotlin` to latest compatible version
    - _Requirements: 8.6, 8.7, 8.8, 8.9, 8.10_

  - [ ] 9.3 Upgrade AWS SDK and Lambda libraries
    - Update `aws.sdk.kotlin:s3-jvm` and `aws.smithy.kotlin:http-client-engine-okhttp` (smithyKotlinVersion) to latest stable
    - Update `aws.sdk.kotlin:ses` to latest stable
    - Update `com.amazonaws:aws-lambda-java-core` to latest stable
    - Update `com.amazonaws:aws-lambda-java-events` to latest stable
    - _Requirements: 8.11, 8.12, 8.13, 8.14_

  - [ ] 9.4 Upgrade Azure SDKs
    - Update `com.azure:azure-identity` to latest stable in `software/infrastructure/azure/build.gradle.kts`
    - Update `com.azure:azure-storage-blob` to latest stable
    - Update `com.azure:azure-communication-email` to latest stable
    - _Requirements: 8.15, 8.16, 8.17_

  - [ ] 9.5 Upgrade build plugins and utility libraries
    - Update `com.github.johnrengelman.shadow` plugin to latest stable in `software/infrastructure/aws/build.gradle.kts`
    - Update `com.microsoft.azure.azurefunctions` plugin to latest stable in `software/infrastructure/azure/build.gradle.kts`
    - Update `org.springframework.boot.experimental.thin-launcher` plugin to latest stable
    - Update `com.squareup.okhttp3:okhttp` to latest stable release
    - Update `org.jetbrains.kotlinx:kotlinx-coroutines-core` to latest stable
    - Update `org.slf4j:slf4j-api` to latest stable
    - Update `io.github.oshai:kotlin-logging-jvm` to latest stable in root `build.gradle.kts`
    - _Requirements: 8.18, 8.19, 8.20, 8.23, 8.24, 8.25, 8.26_

  - [ ] 9.6 Upgrade test libraries
    - Update `org.junit.jupiter:junit-jupiter-api` and `junit-jupiter-engine` to latest stable in both infra build files
    - Update `io.mockk:mockk` to latest stable in both infra build files
    - _Requirements: 8.21, 8.22_

- [ ] 10. Upgrade JVM target to 25
  - [ ] 10.1 Update build configuration for JVM 25
    - Change `JvmTarget.JVM_21` to `JvmTarget.JVM_25` in root `build.gradle.kts`
    - Change `JavaVersion.VERSION_21` to `JavaVersion.VERSION_25` for sourceCompatibility and targetCompatibility
    - Change Java toolchain `languageVersion` to `JavaLanguageVersion.of(25)`
    - _Requirements: 9.1, 9.2, 9.3_

  - [ ] 10.2 Update CI/CD pipelines for JDK 25
    - Change `java-version: '21'` to `java-version: '25'` in all `setup-java` steps in `workflow-build-deploy-aws.yml`
    - Change `java-version: '21'` (and `'17'`) to `java-version: '25'` in all `setup-java` steps in `workflow-build-deploy-azure.yml`
    - _Requirements: 9.4, 9.5_

  - [ ] 10.3 Update AWS Lambda runtime to java25
    - Modify `cdk/aws/src/main/kotlin/com/example/cdk/aws/AwsStack.kt` to set Lambda runtime to `java25`
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 9.6_

  - [ ] 10.4 Update Azure Function Java version to 25
    - Modify `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt` to set Java version to `25` in application stack
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 9.7_

- [ ] 11. Upgrade Azure Function to Flex Consumption Plan
  - [ ] 11.1 Update Azure CDK stack for Flex Consumption
    - Change service plan SKU from `Y1` to `FC1` in `cdk/azure/src/main/kotlin/com/example/cdk/azure/AzureStack.kt`
    - Change function app resource type to Flex Consumption-compatible resource (e.g., `azurerm_linux_function_app_flex_consumption`)
    - Preserve all existing application settings, managed identity, and role assignments
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 12. Implement Deployment Checkpoint 2 in pipeline
  - [ ] 12.1 Populate checkpoint-2 job in AWS reusable workflow
    - Add build, deploy-infra, deploy-app, and health check steps to the `checkpoint-2` job
    - Ensure it uses JDK 25, deploys the upgraded code, and verifies with health check
    - `needs: [checkpoint-1]` ensures sequential execution
    - _Requirements: 11.2, 11.4, 11.5_

  - [ ] 12.2 Populate checkpoint-2 job in Azure reusable workflow
    - Add deploy-infra, deploy-app, and health check steps to the `checkpoint-2` job
    - Ensure it uses JDK 25, deploys Flex Consumption + upgraded code, and verifies with health check
    - `needs: [checkpoint-1]` ensures sequential execution
    - _Requirements: 11.2, 11.4, 11.5_

- [ ] 13. Checkpoint 2 — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 14. Switch AWS Lambda to ARM64 architecture
  - [ ] 14.1 Update AWS CDK stack for ARM64
    - Set `architectures` to `["arm64"]` for all Lambda function definitions in `AwsStack.kt`
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 10.1_

- [ ] 15. Enable SnapStart with published versions and version alias
  - [ ] 15.1 Configure SnapStart in AWS CDK stack
    - Add `snap_start` configuration with `apply_on = "PublishedVersions"` for all Lambda functions
    - Add a published Lambda version resource that updates on each deployment (e.g., using source code hash)
    - Configure API Gateway integration to invoke the published version alias
    - Run `generateTerraform.sh` to regenerate Terraform JSON
    - _Requirements: 10.2, 10.3_

- [ ] 16. Implement ApplicationPrimer in application layer
  - [ ] 16.1 Create ApplicationPrimer component
    - Create `software/application/src/main/kotlin/com/example/clean/architecture/service/ApplicationPrimer.kt`
    - Implement `@Component` class with `prime()` method that pre-loads application classes
    - Inject `HandleDocsFlowRequest` to force its dependency tree loading
    - Use `runCatching` to ensure priming never throws (non-fatal errors logged as warnings)
    - _Requirements: 10.4, 10.5_

  - [ ]* 16.2 Write unit tests for ApplicationPrimer
    - Create `software/application/src/test/kotlin/com/example/clean/architecture/service/ApplicationPrimerTest.kt`
    - Test that `prime()` completes without throwing an exception
    - Test that `prime()` logs success message on happy path
    - Test that `prime()` handles class loading failures gracefully (logs warning, does not throw)
    - **Property 2: Priming safety**
    - **Validates: Requirements 10.4, 10.5**

- [ ] 17. Implement SnapStart priming hook in AWS infrastructure
  - [ ] 17.1 Create SnapStartPrimingHook component
    - Create `software/infrastructure/aws/src/main/kotlin/com/example/clean/architecture/SnapStartPrimingHook.kt`
    - Implement `Resource` interface from `org.crac`
    - Register with `Core.getGlobalContext()` in `init` block
    - Call `applicationPrimer.prime()` in `beforeCheckpoint()`
    - No-op in `afterRestore()`
    - Add `org.crac:crac` dependency to `software/infrastructure/aws/build.gradle.kts`
    - _Requirements: 10.2, 10.6_

  - [ ]* 17.2 Write unit tests for SnapStartPrimingHook
    - Create test in `software/infrastructure/aws/src/test/kotlin/com/example/clean/architecture/`
    - Test that `beforeCheckpoint()` invokes `applicationPrimer.prime()`
    - Test that `afterRestore()` is a no-op
    - _Requirements: 10.2, 10.6_

- [ ] 18. Implement @WarmupTrigger in Azure infrastructure
  - [ ] 18.1 Add Warmup function to Azure infra module
    - Add `Warmup` function with `@WarmupTrigger` annotation in `software/infrastructure/azure/src/main/kotlin/com/example/clean/architecture/`
    - Inject `ApplicationPrimer` and call `prime()` when warmup is triggered
    - _Requirements: 10.7_

  - [ ]* 18.2 Write unit tests for Azure Warmup function
    - Create test in `software/infrastructure/azure/src/test/kotlin/com/example/clean/architecture/`
    - Test that the warmup function invokes `applicationPrimer.prime()`
    - _Requirements: 10.7_

- [ ] 19. Implement Deployment Checkpoint 3 in pipeline
  - [ ] 19.1 Populate checkpoint-3 job in AWS reusable workflow
    - Add build, deploy-infra, deploy-app, and health check steps to the `checkpoint-3` job
    - Deploys ARM64 + SnapStart + priming changes and verifies with health check
    - `needs: [checkpoint-2]` ensures sequential execution
    - _Requirements: 10.8, 11.3, 11.4, 11.5, 11.6_

  - [ ] 19.2 Populate checkpoint-3 job in Azure reusable workflow
    - Add deploy-infra, deploy-app, and health check steps to the `checkpoint-3` job
    - Deploys priming changes and verifies with health check
    - `needs: [checkpoint-2]` ensures sequential execution
    - _Requirements: 10.8, 11.3, 11.4, 11.5, 11.6_

- [ ] 20. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation — each deployment stage is verified before proceeding
- The design has Correctness Properties but the testing strategy explicitly states property-based testing does not strongly apply (infrastructure/config focus). Unit tests cover the key invariants instead.
- After any CDK stack changes, `generateTerraform.sh` must be run to regenerate Terraform JSON
- The pipeline checkpoint structure uses `needs:` dependencies between GitHub Actions jobs for sequential execution
- Property tests are included as unit test sub-tasks since the properties are simple enough to verify with standard JUnit assertions

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "3.1", "3.2"] },
    { "id": 2, "tasks": ["2.2", "3.3", "4.1", "5.1"] },
    { "id": 3, "tasks": ["6.1", "6.2"] },
    { "id": 4, "tasks": ["8.1"] },
    { "id": 5, "tasks": ["9.1", "9.2", "9.3", "9.4", "9.5", "9.6"] },
    { "id": 6, "tasks": ["10.1", "10.2", "10.3", "10.4", "11.1"] },
    { "id": 7, "tasks": ["12.1", "12.2"] },
    { "id": 8, "tasks": ["14.1", "16.1"] },
    { "id": 9, "tasks": ["15.1", "16.2", "17.1", "18.1"] },
    { "id": 10, "tasks": ["17.2", "18.2"] },
    { "id": 11, "tasks": ["19.1", "19.2"] }
  ]
}
```
