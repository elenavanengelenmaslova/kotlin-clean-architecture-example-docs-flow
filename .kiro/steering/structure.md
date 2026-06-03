# System Architecture

This project demonstrates Clean Architecture for serverless functions deployed to both AWS Lambda and Azure Functions. It implements a document review workflow ("Docs Flow") where documents are uploaded, analyzed, and reviewers are notified via email.

## Clean Architecture for Serverless

The architecture follows the clean-architecture style described in ["Keeping Business Logic Portable in Serverless Functions with Clean Architecture"](https://medium.com/nntech/keeping-business-logic-portable-in-serverless-functions-with-clean-architecture-bd1976276562) to keep core behavior decoupled from infrastructure.

Dependencies flow strictly inward: infrastructure depends on application, and application depends on domain, but never the other way around.

### Domain Layer (`:domain`)
- Contains domain models and business rules
- Zero external dependencies (no Spring, no cloud SDKs)
- Defines the core entities of the document review workflow

### Application Layer (`:application`)
- Contains use cases and orchestration logic
- Defines interfaces for external concerns (storage, email)
- Depends only on the domain layer
- Spring Boot application configuration lives here

### Infrastructure Layer — AWS (`:infra-aws`)
- AWS Lambda entry point using Spring Cloud Function adapter
- AWS SDK Kotlin for S3 storage and SES email
- Shadow JAR packaging for Lambda deployment
- Implements interfaces defined in the application layer

### Infrastructure Layer — Azure (`:infra-azure`)
- Azure Functions entry point using Spring Cloud Function adapter
- Azure SDK for Blob Storage and Azure Communication Services (ACS) email
- Boot JAR packaging for Azure Functions deployment
- Implements interfaces defined in the application layer

### CDK Layer — AWS (`:cdk-aws`)
- Terraform CDK in Kotlin defining AWS infrastructure
- API Gateway, Lambda, S3, SES resources
- Generates Terraform JSON via `generateTerraform.sh`

### CDK Layer — Azure (`:cdk-azure`)
- Terraform CDK in Kotlin defining Azure infrastructure
- Function App, Storage Account, Communication Services resources
- Generates Terraform JSON via `generateTerraform.sh`

## Project Structure

```
kotlin-clean-architecture-example-docs-flow/
│
├── build.gradle.kts          // Root build file (Kotlin/JVM config, shared dependencies)
├── settings.gradle.kts       // Module registration
│
├── .github/workflows/        // CI/CD pipelines
│   ├── feature-aws.yml       // Feature branch → AWS deploy
│   ├── feature-azure.yml     // Feature branch → Azure deploy
│   ├── main-aws.yml          // Main branch → AWS deploy
│   ├── main-azure.yml        // Main branch → Azure deploy
│   ├── workflow-build-deploy-aws.yml    // Reusable AWS workflow
│   └── workflow-build-deploy-azure.yml  // Reusable Azure workflow
│
├── software/                 // Application code (clean architecture layers)
│   ├── domain/               // Domain models, business rules
│   ├── application/          // Use cases, Spring Boot app config
│   └── infrastructure/
│       ├── aws/              // AWS Lambda + SDK implementations
│       └── azure/            // Azure Functions + SDK implementations
│
├── cdk/                      // Infrastructure as Code (Terraform CDK in Kotlin)
│   ├── aws/                  // AWS CDK stack
│   │   ├── src/
│   │   ├── cdktf.out/       // Generated Terraform JSON
│   │   └── generateTerraform.sh
│   └── azure/               // Azure CDK stack
│       ├── src/
│       ├── cdktf.out/       // Generated Terraform JSON
│       └── generateTerraform.sh
│
└── docs/                     // Documentation, Postman collections
```

## Technology Stack

- **Kotlin** — Primary language for all layers including CDK infrastructure
- **Spring Boot** — Application framework and dependency injection
- **Spring Cloud Function** — Serverless function abstraction (adapters for AWS Lambda and Azure Functions)
- **Gradle with Kotlin DSL** — Build system
- **Terraform CDK (CDKTF)** — Infrastructure as Code in Kotlin (not HCL)
- **AWS SDK Kotlin** — AWS service interactions (S3, SES)
- **Azure SDK for Java** — Azure service interactions (Blob Storage, ACS)
- **kotlin-logging** — Logging facade
- **JUnit 5 + MockK** — Testing

## API Design

Business logic is exposed as Spring Cloud Functions, not REST controllers:
- `docs-flow` — POST endpoint that accepts documents, stores them, and triggers email notification
- `health` — GET endpoint for deployment verification (to be added)

## Deployment Architecture

- **AWS**: API Gateway → Lambda (Shadow JAR) → S3 + SES
- **Azure**: Function App (Flex Consumption) → Blob Storage + ACS
- **CI/CD**: GitHub Actions with OIDC authentication (no long-lived credentials)
- **IaC**: Terraform CDK in Kotlin, generating Terraform JSON for both clouds

## Multi-Cloud Strategy

The same business logic runs on both AWS and Azure:
- Domain and application layers are cloud-agnostic
- Infrastructure adapters implement the same interfaces for each cloud
- CDK stacks provision equivalent resources on each platform
- CI/CD pipelines deploy to both clouds independently
