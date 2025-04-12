
plugins {
    application
}

dependencies {
    // https://mvnrepository.com/artifact/com.hashicorp/cdktf-provider-azurerm
    implementation("com.hashicorp:cdktf-provider-azurerm:13.20.1")
    // https://mvnrepository.com/artifact/software.constructs/constructs
    implementation("software.constructs:constructs:10.4.2")
}

application {
    mainClass.set("com.example.cdk.azure.AppKt")
}

repositories {
    mavenCentral()
}
