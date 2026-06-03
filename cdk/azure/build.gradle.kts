
plugins {
    application
}

dependencies {
    // https://mvnrepository.com/artifact/com.hashicorp/cdktf-provider-azurerm
    implementation("com.hashicorp:cdktf-provider-azurerm:13.20.1")
    // https://mvnrepository.com/artifact/software.constructs/constructs
    implementation("software.constructs:constructs:10.4.2")

    // CDK synth tests: JUnit 5 (versions managed by the Spring Boot BOM imported
    // in the root build). The cdktf `Testing` harness and a Jackson JSON parser
    // are already on the test classpath transitively via cdktf-provider-azurerm.
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.example.cdk.azure.AppKt")
}

// Ensure the JUnit 5 (Jupiter) platform is used to run the CDK synth tests.
tasks.test {
    useJUnitPlatform()
}
tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xmx4g")
}