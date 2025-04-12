
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.3.9")
}

configurations {
    runtimeClasspath {
        exclude("org.apache.httpcomponents")
        exclude("org.jetbrains")
    }
}
repositories {
    mavenCentral()
}