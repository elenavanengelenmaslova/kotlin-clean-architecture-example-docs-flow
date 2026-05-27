# Requirements Document

## Introduction

This feature covers a set of deployment improvements and platform upgrades for the Kotlin Clean Architecture project. It includes updating email configuration from placeholder values to real addresses, adding a health check endpoint for post-deployment verification, integrating health check calls into CI/CD pipelines with dynamic credential retrieval, upgrading the Azure Function to the Flex Consumption plan, upgrading the Kotlin and JVM versions to their latest supported releases, upgrading all project dependencies (Terraform CDK, Spring Boot, Spring Cloud, AWS/Azure SDKs, build plugins, and utility libraries) to their latest stable versions, adding cold-start performance optimizations (ARM64, SnapStart, priming), and implementing deployment checkpoints with test gates to verify each stage of the rollout.

## Glossary

- **Pipeline**: A GitHub Actions CI/CD workflow that builds, deploys, and verifies the application
- **Health_Check_Endpoint**: An HTTP GET endpoint that returns the operational status of the deployed application
- **AWS_Lambda**: The AWS serverless compute service hosting the application via API Gateway
- **Azure_Function**: The Azure serverless compute service hosting the application
- **Flex_Consumption_Plan**: The Azure Functions hosting plan that provides per-second billing, virtual network integration, and instance memory configuration
- **CDK_Stack**: The Terraform CDK infrastructure-as-code definition (written in Kotlin) that provisions cloud resources
- **Application_Properties**: Spring Boot configuration files containing runtime settings such as email addresses and service endpoints
- **SES**: AWS Simple Email Service used for sending notification emails
- **ACS**: Azure Communication Services used for sending notification emails
- **Deployment_Checkpoint**: A pipeline stage that deploys a set of changes and then runs the health check to verify the deployment is operational before proceeding to the next stage
- **SnapStart**: An AWS Lambda feature that reduces cold start latency by pre-initializing a snapshot of the execution environment
- **Priming**: Application-layer class pre-loading and initialization logic executed during startup to reduce cold start latency
- **ARM64**: The AWS Graviton processor architecture that provides improved price-performance for Lambda functions
- **CDKTF**: Cloud Development Kit for Terraform — a framework that allows defining Terraform infrastructure using general-purpose programming languages (Kotlin in this project) instead of HCL
- **Spring_Cloud_Function**: A Spring project that provides a programming model for implementing business logic via functions, with adapters for serverless platforms (AWS Lambda, Azure Functions)

## Requirements

### Requirement 1: Configure Email Addresses

**User Story:** As a developer, I want the email sender and recipient addresses injected via secrets at deployment time, so that the application can send notification emails without hardcoding email addresses in the repository.

#### Acceptance Criteria

1. THE Application_Properties for the AWS infrastructure module SHALL reference environment variables for the `aws.ses.sender-email` property (e.g., `${SENDER_EMAIL}`) without embedding a hardcoded default email address
2. THE Application_Properties for the AWS infrastructure module SHALL reference environment variables for the `aws.ses.recipient-email` property (e.g., `${RECIPIENT_EMAIL}`) without embedding a hardcoded default email address
3. THE Application_Properties for the Azure infrastructure module SHALL reference environment variables for the `azure.acs.sender-email` property (e.g., `${SENDER_EMAIL}`) without embedding a hardcoded default email address
4. THE Application_Properties for the Azure infrastructure module SHALL reference environment variables for the `azure.acs.recipient-email` property (e.g., `${RECIPIENT_EMAIL}`) without embedding a hardcoded default email address
5. THE Application_Properties for both AWS and Azure infrastructure modules SHALL NOT contain the literal string "REPLACEME" or any hardcoded email address in the email property values
6. THE AWS CDK_Stack SHALL pass the `SENDER_EMAIL` and `RECIPIENT_EMAIL` values as Lambda environment variables sourced from deployment-time configuration
7. THE Azure CDK_Stack SHALL pass the `SENDER_EMAIL` and `RECIPIENT_EMAIL` values as function app application settings sourced from deployment-time configuration
8. THE Pipeline SHALL provide the `SENDER_EMAIL` and `RECIPIENT_EMAIL` values from GitHub Actions secrets during deployment

### Requirement 2: Health Check Endpoint for AWS Lambda

**User Story:** As a DevOps engineer, I want a health check endpoint on the AWS Lambda deployment, so that I can verify the application is operational after deployment.

#### Acceptance Criteria

1. WHEN an HTTP GET request is sent to the `/health` path, THE AWS_Lambda SHALL return an HTTP 200 response with a JSON body containing a `status` field set to `"UP"`
2. IF the health check function encounters an internal error, THEN THE AWS_Lambda SHALL return an HTTP 503 response with a JSON body containing a `status` field set to `"DOWN"`
3. THE Health_Check_Endpoint on AWS SHALL require API key authentication consistent with the existing `docs-flow` endpoint
4. THE CDK_Stack for AWS SHALL define an API Gateway resource for the `/health` path with an HTTP GET method, API key required, integrated with a Lambda function configured with a health check Spring Cloud Function definition
5. WHEN the health check function is invoked, THE AWS_Lambda SHALL respond within 10 seconds

### Requirement 3: Health Check Endpoint for Azure Function

**User Story:** As a DevOps engineer, I want a health check endpoint on the Azure Function deployment, so that I can verify the application is operational after deployment.

#### Acceptance Criteria

1. WHEN an HTTP GET request is sent to the `/api/health` route, THE Azure_Function SHALL return an HTTP 200 response with a JSON body containing a `status` field set to `UP` and a Content-Type header of `application/json`
2. THE Health_Check_Endpoint on Azure SHALL use FUNCTION-level authorization consistent with the existing endpoint security model
3. WHEN the health check function is invoked, THE Azure_Function SHALL respond within 10 seconds
4. IF the health check function encounters an internal error during invocation, THEN THE Azure_Function SHALL return a non-200 HTTP response with a JSON body containing a `status` field set to `DOWN`

### Requirement 4: Post-Deployment Health Check in AWS Pipeline

**User Story:** As a DevOps engineer, I want the AWS CI/CD pipeline to call the health check endpoint after deployment, so that I can automatically verify the deployment succeeded.

#### Acceptance Criteria

1. WHEN the AWS deployment step completes successfully, THE Pipeline SHALL send an HTTP GET request to the `/health` path on the API Gateway URL with the API key included in the request headers
2. IF the health check returns a non-200 status code or the request times out, THEN THE Pipeline SHALL retry the request up to 3 times with a 10-second wait between attempts before failing the workflow run
3. THE Pipeline SHALL retrieve the API Gateway URL from Terraform outputs and the API key dynamically at runtime using the AWS CLI (e.g., `aws apigateway get-api-keys`) rather than storing credentials as GitHub Actions secrets
4. THE Pipeline SHALL wait up to 30 seconds for each health check response before treating the attempt as a timeout

### Requirement 5: Post-Deployment Health Check in Azure Pipeline

**User Story:** As a DevOps engineer, I want the Azure CI/CD pipeline to call the health check endpoint after deployment, so that I can automatically verify the deployment succeeded.

#### Acceptance Criteria

1. WHEN the Azure deployment step completes successfully, THE Pipeline SHALL send an HTTP GET request to the `/api/health` path of the deployed Azure Function using the function's base URL
2. IF the health check returns a non-200 status code or the request fails due to a connection error, THEN THE Pipeline SHALL retry the request up to 3 times with a 10-second delay between attempts before failing the workflow run
3. THE Pipeline SHALL retrieve the Azure Function base URL and function key dynamically at runtime using the Azure CLI (e.g., `az functionapp keys list` and `az functionapp show`) rather than storing credentials as GitHub Actions secrets
4. THE Pipeline SHALL wait up to 30 seconds for each individual health check response before treating the attempt as a failed request
5. IF all health check retry attempts are exhausted without receiving an HTTP 200 response, THEN THE Pipeline SHALL fail the workflow run and report the last received status code or error

### Requirement 6: Upgrade Azure Function to Flex Consumption Plan

**User Story:** As a developer, I want the Azure Function upgraded to the Flex Consumption plan, so that I benefit from per-second billing, improved cold start performance, and virtual network support.

#### Acceptance Criteria

1. THE CDK_Stack for Azure SHALL define the service plan with the Flex Consumption SKU (`FC1`) instead of the current Consumption SKU (`Y1`)
2. THE CDK_Stack for Azure SHALL define the function app using the Flex Consumption-compatible resource type (e.g., `azurerm_linux_function_app_flex_consumption`) instead of the current `azurerm_linux_function_app` resource type
3. WHEN the infrastructure is deployed, THE Azure_Function SHALL report its hosting plan SKU as `FC1` when queried via the Azure Resource Manager API or Azure Portal
4. THE CDK_Stack for Azure SHALL preserve all existing application settings (including `APPINSIGHTS_INSTRUMENTATIONKEY`, `MAIN_CLASS`, `TriggerBlobStorage__accountName`, `TriggerBlobStorage__credential`, `WEBSITE_RUN_FROM_PACKAGE`, and `ACS_ENDPOINT`), the SystemAssigned managed identity configuration, and all role assignments (Storage Blob Data Contributor, Storage Account Contributor, Storage Queue Data Contributor, and the custom ACS role) after the plan upgrade
5. THE CDK_Stack for Azure SHALL retain the Linux OS type and Java 21 application stack configuration after the plan upgrade

### Requirement 7: Upgrade Kotlin to 2.3.20

**User Story:** As a developer, I want to upgrade Kotlin to version 2.3.20, so that I can use the latest language features and improvements.

#### Acceptance Criteria

1. THE root build.gradle.kts SHALL specify Kotlin version 2.3.20 for the `kotlin("jvm")` plugin
2. THE root build.gradle.kts SHALL specify Kotlin version 2.3.20 for the `kotlin("plugin.spring")` plugin
3. WHEN the project is built with `./gradlew build`, THE build system SHALL compile all source sets across all six submodules (domain, application, infra-aws, infra-azure, cdk-aws, cdk-azure) with zero compilation errors using Kotlin 2.3.20
4. WHEN the project is built with `./gradlew build`, THE build system SHALL execute all unit tests across all submodules with zero test failures
5. THE submodule build.gradle.kts files SHALL NOT declare a Kotlin plugin version that overrides the root-level Kotlin 2.3.20 version

### Requirement 8: Upgrade Dependencies and Libraries

**User Story:** As a developer, I want all project dependencies upgraded to their latest stable versions compatible with JVM 25 and Kotlin 2.3.20, so that the project benefits from security patches, performance improvements, and continued community support.

#### Acceptance Criteria

##### Terraform CDK Libraries

1. THE cdk-aws build.gradle.kts SHALL specify the latest stable version of `com.hashicorp:cdktf` (upgrading from 0.20.11)
2. THE cdk-aws build.gradle.kts SHALL specify the latest stable version of `com.hashicorp:cdktf-provider-aws` (upgrading from 19.54.0)
3. THE cdk-aws build.gradle.kts SHALL specify the latest stable version of `com.hashicorp:cdktf-provider-random` (upgrading from 11.1.0)
4. THE cdk-aws and cdk-azure build.gradle.kts files SHALL specify the latest stable version of `software.constructs:constructs` (upgrading from 10.4.2)
5. THE cdk-azure build.gradle.kts SHALL specify the latest stable version of `com.hashicorp:cdktf-provider-azurerm` (upgrading from 13.20.1)

##### Spring Boot and Spring Cloud

6. THE root build.gradle.kts SHALL specify the latest stable Spring Boot 3.5.x version for the `org.springframework.boot` plugin (upgrading from 3.3.9 which has reached end of OSS support)
7. THE root build.gradle.kts SHALL specify the latest stable version of the `io.spring.dependency-management` plugin compatible with Spring Boot 3.5.x (upgrading from 1.1.7)
8. THE infra-azure build.gradle.kts SHALL specify the latest stable Spring Cloud Dependencies BOM version compatible with Spring Boot 3.5.x (upgrading from 2023.0.5)
9. THE infra-aws and infra-azure build.gradle.kts files SHALL specify the latest stable version of `org.springframework.cloud:spring-cloud-function-adapter-aws` and `spring-cloud-function-adapter-azure` compatible with the upgraded Spring Cloud BOM (upgrading from 4.2.2)
10. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `org.springframework.cloud:spring-cloud-function-kotlin` compatible with the upgraded Spring Cloud BOM (upgrading from 4.2.2)

##### AWS SDK and Lambda Libraries

11. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `aws.sdk.kotlin:s3-jvm` and `aws.smithy.kotlin:http-client-engine-okhttp` (upgrading from smithyKotlinVersion 1.4.11)
12. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `aws.sdk.kotlin:ses` (upgrading from 1.4.72)
13. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `com.amazonaws:aws-lambda-java-core` (upgrading from 1.2.2)
14. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `com.amazonaws:aws-lambda-java-events` (upgrading from 3.11.1)

##### Azure SDKs

15. THE infra-azure build.gradle.kts SHALL specify the latest stable version of `com.azure:azure-identity` (upgrading from 1.15.4)
16. THE infra-azure build.gradle.kts SHALL specify the latest stable version of `com.azure:azure-storage-blob` (upgrading from 12.25.1)
17. THE infra-azure build.gradle.kts SHALL specify the latest stable version of `com.azure:azure-communication-email` (upgrading from 1.0.2)

##### Build Plugins

18. THE infra-aws build.gradle.kts SHALL specify the latest stable version of the `com.github.johnrengelman.shadow` plugin (upgrading from 8.1.1)
19. THE infra-azure build.gradle.kts SHALL specify the latest stable version of the `com.microsoft.azure.azurefunctions` plugin (upgrading from 1.13.0)
20. THE infra-aws build.gradle.kts SHALL specify the latest stable version of the `org.springframework.boot.experimental.thin-launcher` plugin (upgrading from 1.0.31.RELEASE)

##### Test Libraries

21. THE infra-aws and infra-azure build.gradle.kts files SHALL specify the latest stable version of `org.junit.jupiter:junit-jupiter-api` and `junit-jupiter-engine` (upgrading from 5.9.2)
22. THE infra-aws and infra-azure build.gradle.kts files SHALL specify the latest stable version of `io.mockk:mockk` (upgrading from 1.13.8)

##### Utility Libraries

23. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `com.squareup.okhttp3:okhttp` (upgrading from 5.0.0-alpha.14 to the latest stable release)
24. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `org.jetbrains.kotlinx:kotlinx-coroutines-core` (upgrading from 1.7.3)
25. THE infra-aws build.gradle.kts SHALL specify the latest stable version of `org.slf4j:slf4j-api` (upgrading from 2.0.5)
26. THE root build.gradle.kts SHALL specify the latest stable version of `io.github.oshai:kotlin-logging-jvm` (upgrading from 7.0.0)

##### Verification

27. WHEN all dependencies are upgraded, THE build system SHALL compile all modules with `./gradlew build` with zero compilation errors targeting JVM 25 and Kotlin 2.3.20
28. WHEN all dependencies are upgraded, THE build system SHALL execute all unit tests across all submodules with zero test failures
29. WHEN all dependencies are upgraded, THE build system SHALL produce zero deprecation warnings related to removed or deprecated APIs in the upgraded libraries
30. WHEN all dependencies are upgraded, THE CDKTF stacks SHALL synthesize valid Terraform JSON for both AWS and Azure without errors

### Requirement 9: Upgrade JVM Target to 25

**User Story:** As a developer, I want to upgrade the JVM target to version 25, so that I can leverage the latest JVM performance improvements on both AWS and Azure.

#### Acceptance Criteria

1. THE root build.gradle.kts SHALL set `jvmTarget` to `JVM_25` for all Kotlin compilation tasks
2. THE root build.gradle.kts SHALL set `sourceCompatibility` and `targetCompatibility` to `JavaVersion.VERSION_25`
3. THE root build.gradle.kts SHALL configure the Java toolchain to use language version 25
4. THE AWS CI/CD Pipeline SHALL use JDK 25 for all jobs that compile or execute Java/Kotlin code, including the build job and the deploy-app job
5. THE Azure CI/CD Pipeline SHALL use JDK 25 for all jobs that compile or execute Java/Kotlin code, including the deploy-infra job and the deploy-app job
6. THE CDK_Stack for AWS SHALL set the runtime to `java25` for all Lambda function definitions in the stack
7. THE CDK_Stack for Azure SHALL set the function app Java version to `25` in the application stack configuration
8. WHEN the project is built with `./gradlew build`, THE build system SHALL compile all modules and execute all unit tests successfully with zero failures targeting JVM 25

### Requirement 10: ARM64 Architecture, SnapStart, and Priming Optimizations

**User Story:** As a developer, I want to enable ARM64 architecture, SnapStart, and application-layer priming/class pre-loading, so that cold start latency is minimized on both AWS and Azure deployments.

#### Acceptance Criteria

1. THE CDK_Stack for AWS SHALL set the `architectures` property to `["arm64"]` for all Lambda function definitions in the stack
2. THE CDK_Stack for AWS SHALL enable SnapStart by setting the `snap_start` configuration with `apply_on` set to `"PublishedVersions"` for all Lambda function definitions in the stack
3. THE CDK_Stack for AWS SHALL publish a new Lambda version on each deployment and configure the API Gateway integration to invoke the published version alias (required for SnapStart to take effect)
4. THE Application layer SHALL contain a reusable priming component that pre-loads and initializes application classes during startup to reduce cold start latency
5. THE Priming component SHALL be implemented in the shared application or domain layer so that both AWS Lambda and Azure Function deployments benefit from the same pre-loading logic
6. WHEN the AWS Lambda function cold-starts with SnapStart and Priming enabled, THE AWS_Lambda SHALL restore from the snapshot with pre-loaded classes and respond to the first request within 10 seconds
7. WHEN a new Azure Function instance is added to the running function app, THE Azure_Function SHALL execute a `@WarmupTrigger`-annotated function that invokes the shared Priming component to pre-load and initialize application classes before the instance receives production traffic
8. WHEN the health check is invoked after deploying the ARM64, SnapStart, and Priming changes, THE Health_Check_Endpoint on both AWS and Azure SHALL return HTTP 200 with status `UP`

### Requirement 11: Deployment Checkpoints with Test Gates

**User Story:** As a DevOps engineer, I want the CI/CD pipeline to deploy changes in stages with health check verification at each checkpoint, so that I can isolate failures to specific change sets and ensure each stage is operational before proceeding.

#### Acceptance Criteria

1. THE Pipeline SHALL implement Deployment_Checkpoint 1 that deploys the email configuration changes (Requirement 1) and health check endpoint infrastructure (Requirements 2, 3), then runs the health check to verify the deployment returns HTTP 200 with status `UP`
2. THE Pipeline SHALL implement Deployment_Checkpoint 2 that deploys the Flex Consumption plan upgrade (Requirement 6), Kotlin 2.3.20 upgrade (Requirement 7), dependency upgrades (Requirement 8), and JVM 25 upgrade (Requirement 9), then runs the health check to verify the deployment returns HTTP 200 with status `UP`
3. THE Pipeline SHALL implement Deployment_Checkpoint 3 that deploys the ARM64 architecture switch, SnapStart enablement, and Priming optimizations (Requirement 10), then runs the health check to verify the deployment returns HTTP 200 with status `UP`
4. IF the health check at any Deployment_Checkpoint fails after exhausting retries, THEN THE Pipeline SHALL fail the workflow run and report which checkpoint failed, preventing subsequent checkpoints from executing
5. THE Pipeline SHALL execute Deployment_Checkpoints in sequential order (1, 2, 3) where each checkpoint depends on the successful completion of the previous checkpoint
6. WHEN all three Deployment_Checkpoints pass their health checks, THE Pipeline SHALL report the overall deployment as successful
