# Development Practices

## Gradle Cache Policy

This policy is about **how you gather information**, not about modifying the cache.

**Do NOT inspect the Gradle cache as a source of truth.** When determining which libraries or versions are available, read the `build.gradle.kts` files directly rather than browsing `~/.gradle/caches/`. Do not assume a dependency exists simply because it may have been cached by a previous build — always confirm it is declared in a repository (Maven Central, etc.).

**When you need to understand a library's API**, look up the library's official documentation online or browse the library's GitHub repository at the correct tag/version. Do not dig into the Gradle cache for this purpose.

**Never delete, clear, or modify the Gradle cache.** Specifically:
- NEVER run `rm -rf ~/.gradle/caches` or `rm -rf ~/.gradle/caches/transforms-*` or any command that removes files under the global Gradle home (`~/.gradle`). This is machine-wide and affects every Gradle project on the system.
- If you need a clean build, use **project-scoped** Gradle tasks instead, e.g. `./gradlew :infra-azure:clean :infra-azure:test`. The `clean` task only removes the module's `build/` directory.
- `--rerun-tasks` may be used to force task re-execution without touching any cache.
- "Don't rely on the cache" means *don't read it as authoritative* — it does NOT mean *delete it*.

## Multi-module Gradle Workflow

- Treat the repository as a clean-architecture, multi-module Kotlin project: domain models live in `:domain`, application-layer logic in `:application`, and cloud-specific adapters in `:infra-aws` and `:infra-azure`
- CDK infrastructure code lives in `:cdk-aws` and `:cdk-azure`
- Keep new code aligned with that separation and register new modules in `settings.gradle.kts` if needed
- Ensure dependencies flow correctly: infra → application → domain (never the reverse)

## Terraform CDK Workflow

- Infrastructure is defined using Terraform CDK in Kotlin (not HCL)
- CDK stacks are in `cdk/aws/` and `cdk/azure/`
- After any infrastructure changes, run `generateTerraform.sh` in the appropriate cdk module to regenerate the Terraform JSON
- CDK output goes to `cdktf.out/` directories — these are generated artifacts

## GitHub Actions Integration

- Feature branches trigger `feature-aws.yml` and `feature-azure.yml` workflows
- Main branch changes trigger `main-aws.yml` and `main-azure.yml` for production deployment
- Use `workflow-build-deploy-aws.yml` and `workflow-build-deploy-azure.yml` as reusable workflow templates
- Ensure new features don't break existing CI/CD pipelines
- Both AWS and Azure deployments use OIDC for authentication (no long-lived credentials)

# Code Generation Standards

## General Kotlin Development

- Generate Kotlin code targeting the project's configured JVM version, using Gradle with Spring Boot and Spring Cloud Function
- **Use AWS SDK Kotlin** (not Java SDK) for AWS cloud infrastructure interactions — keep these in `software/infrastructure/aws/`
- **Use Azure SDK for Java** for Azure interactions — keep these in `software/infrastructure/azure/`
- **Use proper imports** instead of fully qualified class names in code
- Follow the existing package structure: `com.example.clean.architecture.*`

## Kotlin Constructor Visibility

- Use the minimum visibility needed for constructor parameters and properties
- Do not expose constructor dependencies as public properties unless they are intentionally part of the class API
- If a constructor argument is only passed to a superclass constructor, keep it as a plain constructor parameter
- If a constructor dependency is stored and used only inside the class, declare it as `private val`
- In Spring dependency injection, default to `private val` for dependencies

## Kotlin Idioms

- Use `runCatching { }` instead of try-catch-finally blocks where appropriate
- Use `.use { }` for automatic resource management (closeable resources)
- Leverage Kotlin's null safety and smart casts
- Avoid `!!` operator
- Prefer `checkNotNull`, `check`, `error` instead of throwing `IllegalStateException`
- Prefer `require` and `requireNotNull` instead of throwing `IllegalArgumentException`
- Prefer latest language features, such as `enum.entries` over `enum.values()`

## Logging Standards

- **Use kotlin-logging (KotlinLogging)** for all logging:
  ```kotlin
  private val logger = KotlinLogging.logger {}
  ```
- Place logger instances as a private top-level member of the kt file
- **Use structured logging** with consistent message formatting:
  ```kotlin
  logger.info { "Processing document: id=$id" }
  logger.warn(exception) { "Failed to store document: id=$id, bucket=$bucketName" }
  ```
- Use appropriate log levels: ERROR for system errors, WARN for recoverable errors, INFO for normal flow, DEBUG for detailed execution
- **Always pass exceptions to the logger** when logging failures
- Avoid logging sensitive data such as email addresses, API keys, or bucket names in production logs

## Serialization

- Use Jackson with `jackson-module-kotlin` for JSON serialization (consistent with Spring Boot)
- Use `@JsonProperty` annotations when JSON field names differ from Kotlin property names

## Spring Cloud Function

- Business logic is exposed as Spring Cloud Functions (not traditional REST controllers)
- AWS Lambda uses `spring-cloud-function-adapter-aws` to bridge Lambda events to Spring functions
- Azure Functions uses `spring-cloud-function-adapter-azure` to bridge Azure triggers to Spring functions
- Function definitions are configured via `spring.cloud.function.definition` in application.properties

# Unit Testing Standards

## Test Structure and Naming

- **Use Given-When-Then naming convention** for test methods:
  ```kotlin
  @Test
  fun `Given valid document When uploading Then should return document ID`()
  ```
- Use backticks for readable test names with spaces

## MockK Configuration

- **Declare mocks with relaxed behavior** at the property level:
  ```kotlin
  private val mockStorage: StorageService = mockk(relaxed = true)
  ```
- **Reset only the mocks used in the test** in teardown:
  ```kotlin
  @AfterEach
  fun tearDown() {
      clearMocks(mockStorage)
  }
  ```
- Prefer `coEvery` and `coVerify` for suspend functions
- Use `relaxed = true` by default to avoid unnecessary stubbing

## Coroutine Testing

- Use JUnit 5 with `kotlinx-coroutines-test` for testing suspend functions
- Prefer `runTest` for coroutine tests that need time control

## Test Data Management

- Store test data in `src/test/resources` folder
- Prefer external files for JSON data larger than 3 lines
- Use parameterized tests for multiple scenarios

# Testing Strategy

- Add JUnit 5/Kotlin test coverage alongside new features
- Target meaningful test coverage for business logic in domain and application layers
- Use integration tests for infrastructure layer (AWS/Azure adapters)
- Follow MockK configuration and Given-When-Then naming conventions

# Deployment Assistance

- For AWS, keep S3 interactions aligned with existing bucket/key patterns
- For Azure, keep blob storage interactions aligned with existing container/blob patterns
- Preserve the documented deployment touchpoints (function URLs, API keys, Postman collections)
- Both clouds use Terraform CDK (Kotlin) for infrastructure — not raw Terraform HCL
