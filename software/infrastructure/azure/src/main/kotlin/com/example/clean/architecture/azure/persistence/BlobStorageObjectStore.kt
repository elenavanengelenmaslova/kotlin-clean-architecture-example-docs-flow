package com.example.clean.architecture.azure.persistence

import com.azure.storage.blob.BlobContainerClient
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger {}

@Repository
@Primary
class BlobStorageObjectStore(
    private val containerClient: BlobContainerClient
) : ObjectStorageInterface {

    override fun save(id: String, content: ByteArray): String {
        logger.info { "Saving doc with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        blobClient.upload(content.inputStream() , true)
        return blobClient.blobUrl
    }

    override fun get(id: String): ByteArray? {
        logger.info { "Getting doc with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        return if (blobClient.exists()) {
            blobClient.downloadContent().toBytes()
        } else {
            logger.info { "Doc with id: $id not found" }
            null
        }
    }

    override fun delete(id: String) {
        logger.info { "Deleting doc with id: $id" }
        val blobClient = containerClient.getBlobClient(id)
        if (blobClient.exists()) {
            blobClient.delete()
        } else {
            logger.info { "Doc with id: $id not found, nothing to delete" }
        }
    }

    override fun list(): List<String> {
        logger.info { "Listing all docs" }
        return containerClient.listBlobs()
            .map { it.name }
            .toList()
    }
}
