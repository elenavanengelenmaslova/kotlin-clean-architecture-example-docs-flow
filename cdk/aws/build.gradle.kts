
plugins {
    application
}

dependencies {
    // https://mvnrepository.com/artifact/com.hashicorp/cdktf-provider-aws
    implementation("com.hashicorp:cdktf-provider-aws:19.54.0")
    // https://mvnrepository.com/artifact/com.hashicorp/cdktf
    implementation("com.hashicorp:cdktf:0.20.11")
    implementation("com.hashicorp:cdktf-provider-random:11.1.0")
    // https://mvnrepository.com/artifact/software.constructs/constructs
    implementation("software.constructs:constructs:10.4.2")
}

application {
    mainClass.set("com.example.cdk.aws.AppKt")
}

tasks.named("run") {
    dependsOn(":infra-aws:shadowJar")
}
tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.jvmArgs = listOf("-Xmx4g")
}
