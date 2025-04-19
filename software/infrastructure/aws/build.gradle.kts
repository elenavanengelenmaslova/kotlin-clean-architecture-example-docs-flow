import com.github.jengelman.gradle.plugins.shadow.transformers.PropertiesFileTransformer
import org.gradle.api.publish.maven.MavenPublication
plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
    id("org.springframework.boot.experimental.thin-launcher") version "1.0.31.RELEASE"
}

val smithyKotlinVersion = "1.4.11"
dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    // https://mvnrepository.com/artifact/org.springframework.cloud/spring-cloud-function-adapter-aws
    implementation("org.springframework.cloud:spring-cloud-function-adapter-aws:4.2.2")
    implementation("org.springframework.cloud:spring-cloud-function-kotlin:4.2.2")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("aws.sdk.kotlin:s3-jvm:$smithyKotlinVersion")
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:$smithyKotlinVersion")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.1")
    implementation("org.slf4j:slf4j-api:2.0.5")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.5.31")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("io.mockk:mockk:1.13.8")
}

configurations {
    runtimeClasspath {
        exclude("org.apache.httpcomponents")
        exclude("org.jetbrains")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
        }
    }
}

tasks {
    val thinJar by existing(Jar::class)
    val shadowJar by getting(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        archiveFileName.set("demo-aws-function.jar")
        destinationDirectory.set(file("${project.rootDir}/build/dist"))
        manifest {
            thinJar.get().manifest.attributes.forEach { key, value ->
                attributes[key] = value
            }
        }
        mergeServiceFiles()
        append("META-INF/spring.handlers")
        append("META-INF/spring.schemas")
        append("META-INF/spring.tooling")
        append("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        append("META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports")
        transform(PropertiesFileTransformer::class.java) {
            paths = listOf("META-INF/spring.factories")
            mergeStrategy = "append"
        }
    }

    thinJar.configure {
        mustRunAfter(shadowJar)
    }
}
