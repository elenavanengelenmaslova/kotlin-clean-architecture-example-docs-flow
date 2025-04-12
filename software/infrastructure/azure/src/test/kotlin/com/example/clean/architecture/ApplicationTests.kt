package com.example.clean.architecture

import com.example.clean.architecture.test.config.LocalTestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("local")
@Import(LocalTestConfiguration::class)
class ApplicationTests {

    @Test
    fun contextLoads() {
    }
}
