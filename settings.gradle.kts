rootProject.name = "kotlin-spring-cloud-function-clean-architecture-demo"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://repo.spring.io/milestone")
        }
    }
}

include(":domain")
project(":domain").projectDir = file("software/domain")
include(":application")
project(":application").projectDir = file("software/application")
include(":infra-aws")
project(":infra-aws").projectDir = file("software/infrastructure/aws")
include(":infra-azure")
project(":infra-azure").projectDir = file("software/infrastructure/azure")
include(":cdk-aws")
project(":cdk-aws").projectDir = file("cdk/aws")
include(":cdk-azure")
project(":cdk-azure").projectDir = file("cdk/azure")