package com.example.clean.architecture.test.config

import com.azure.communication.email.EmailClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LocalTestConfiguration {

    @Bean
    fun blobContainerClient(): BlobContainerClient = mockk(relaxed = true)

    @Bean
    fun blobServiceClient(): BlobServiceClient = mockk(relaxed = true)

    @Bean
    fun emailClient(): EmailClient = mockk(relaxed = true)
}