package com.example.clean.architecture.test.config

import com.azure.storage.blob.BlobContainerClient
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LocalTestConfiguration {

    @Bean
    fun blobContainerClient(): BlobContainerClient = mockk(relaxed = true)
}