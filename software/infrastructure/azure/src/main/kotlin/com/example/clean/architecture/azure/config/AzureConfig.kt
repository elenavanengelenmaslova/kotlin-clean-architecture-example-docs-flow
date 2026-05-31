package com.example.clean.architecture.azure.config

import com.azure.communication.email.EmailClient
import com.azure.communication.email.EmailClientBuilder
import com.azure.core.credential.TokenCredential
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("!local")
class AzureConfig(
    @Value("\${azure.storage.endpoint}") private val endpoint: String,
    @Value("\${azure.storage.container-name}") private val containerName: String,
    @Value("\${azure.acs.endpoint}") private val acsEndpoint: String,
) {

    @Bean
    fun azureCredential(): TokenCredential {
        logger.info { "Initializing DefaultAzureCredential (managed identity)" }
        return DefaultAzureCredentialBuilder().build()
    }

    @Bean
    fun blobServiceClient(azureCredential: TokenCredential): BlobServiceClient {
        logger.info { "Initializing Blob Service Client using managed identity" }
        return BlobServiceClientBuilder()
            .endpoint(endpoint)
            .credential(azureCredential)
            .buildClient()
    }

    @Bean
    fun blobContainerClient(blobServiceClient: BlobServiceClient): BlobContainerClient {
        logger.info { "Initializing Blob Container Client with container name: $containerName" }
        return blobServiceClient.getBlobContainerClient(containerName)
    }



    @Bean
    fun emailClient(azureCredential: TokenCredential): EmailClient {
        logger.info { "Initializing Azure Communication Services Email Client using managed identity" }
        return EmailClientBuilder()
            .endpoint(acsEndpoint)
            .credential(azureCredential)
            .buildClient()
    }
}