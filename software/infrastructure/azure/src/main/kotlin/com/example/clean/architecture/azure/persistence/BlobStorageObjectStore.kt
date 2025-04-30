package com.example.clean.architecture.azure.persistence

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.sas.BlobSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import com.azure.storage.common.sas.SasProtocol
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

private val logger = KotlinLogging.logger {}

@Repository
@Primary
class BlobStorageObjectStore(
    private val containerClient: BlobContainerClient,
    private val blobServiceClient: BlobServiceClient,
) : ObjectStorageInterface {

    override fun save(id: String, content: ByteArray): String {
        logger.info { "Saving doc with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        blobClient.upload(content.inputStream(), true)
        return blobClient.blobUrl
    }

    override fun generateSecureAccessUri(id: String): String {
        logger.info { "Generating secure access URI with SAS token for doc with id: $id" }

        val blobClient = containerClient.getBlobClient(id)
        val now = OffsetDateTime.now()
        val expiryTime = now.plusHours(24)

        val sasPermission = BlobSasPermission().setReadPermission(true)

        val sasValues = BlobServiceSasSignatureValues(expiryTime, sasPermission)
            .setStartTime(now)
            .setProtocol(SasProtocol.HTTPS_ONLY)

        val userDelegationKey = blobServiceClient.getUserDelegationKey(now, expiryTime)

        val sasToken = blobClient.generateUserDelegationSas(sasValues, userDelegationKey)

        val blobUrlWithSas = "${blobClient.blobUrl}?$sasToken"
        logger.info { "Generated SAS URL: $blobUrlWithSas" }

        return blobUrlWithSas
    }


}
