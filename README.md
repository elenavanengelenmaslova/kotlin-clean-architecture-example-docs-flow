# Getting Started

## Use cases
```plantuml

@startuml

actor "Mock Admin" as Admin
actor "Client App" as Client

rectangle "MockNest\n(Serverless WireMock)" {
  (Create Mock) as Create
  (Update Mock) as Update
  (Delete Mock) as Delete
  (Reset All Mocks) as Reset
  (Call Mocked API) as Call
}

Admin --> Create
Admin --> Update
Admin --> Delete
Admin --> Reset

Client --> Call
@enduml



```

# Solution Architecture
![SolutionDesign.png](docs/SolutionDesign.png)

## Project Structure

```
kotlin-spring-cloud-function-clean-architecture-example/
│
├── build.gradle.kts     // Root build file
├── settings.gradle.kts  // Contains include statements for subprojects
│
├── software/            // Holds all the business logic and application code
│   ├── domain/
│   │   ├── src/
│   │   └── build.gradle.kts
│   ├── application/
│   │   ├── src/
│   │   └── build.gradle.kts
│   └── infra/            // Infrastructure specific code
│       ├── aws/          // AWS-specific code, including AWS Lambda
│       │   ├── src/
│       │   └── build.gradle.kts
│       └── azure/        // Azure-specific code, including Azure Function
│           ├── src/
│           └── build.gradle.kts
│
└── cdk/                  // Terraform CDK Kotlin code
    ├── aws/
    │   ├──src/
    │   └──build.gradle.kts
    └── azure/
        ├──src/
        └──build.gradle.kts

```
# Delete resources
1. IAM Policy `Spring-Clean-Architecture-Fun-Policy`
2. Iam Role `Spring-Clean-Architecture-Fun-Role`
3. Table `Products-Clean-Architecture-Example`
4. Lambda `Spring-Clean-Architecture-Fun`


### Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.1.4/gradle-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.1.4/gradle-plugin/reference/html/#build-image)
* [Function](https://docs.spring.io/spring-cloud-function/docs/current/reference/html/spring-cloud-function.html)

### Additional Links

These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
* [Various sample apps using Spring Cloud Function](https://github.com/spring-cloud/spring-cloud-function/tree/main/spring-cloud-function-samples)

