package com.example.clean.architecture.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Supplier

@Configuration
class HealthCheckConfig {
    @Bean
    fun healthCheck(): Supplier<HealthStatus> = Supplier {
        HealthStatus(status = "UP")
    }
}

data class HealthStatus(val status: String)
