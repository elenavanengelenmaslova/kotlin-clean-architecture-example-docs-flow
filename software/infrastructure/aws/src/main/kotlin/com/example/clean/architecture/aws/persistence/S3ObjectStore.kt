package com.example.clean.architecture.aws.persistence

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger {}

@Repository
class S3ObjectStore(
    @Value("\${aws.s3.bucket-name}") private val bucketName: String,
    private val s3Client: S3Client,
) : ObjectStorageInterface {

    override fun save(id: String, content: ByteArray): String = runBlocking {
        logger.info { "Saving doc with id: $id" }
        runCatching {
            val byteStream = ByteStream.fromBytes(content)
            s3Client.putObject(
                PutObjectRequest {
                    bucket = bucketName
                    key = id
                    body = byteStream
                }
            )
            "s3://$bucketName/$id"
        }.onFailure { e -> logger.error(e) { "Failed to save doc with id: $id" } }
            .getOrThrow()

    }

    override fun get(id: String): ByteArray? = runBlocking {
        logger.info { "Getting doc with id: $id" }
        runCatching {
            var content: ByteArray? = null
            s3Client.getObject(GetObjectRequest {
                bucket = bucketName
                key = id
            }) { response ->
                content = response.body?.toByteArray()
            }
            content
        }.onFailure { e ->
            logger.info { "Doc with id: $id not found: ${e.message}" }
        }.getOrThrow()
    }

    override fun delete(id: String): Unit = runBlocking {
        logger.info { "Deleting doc with id: $id" }
        runCatching {
            s3Client.deleteObject(DeleteObjectRequest {
                bucket = bucketName
                key = id
            })
        }.onFailure { e -> logger.info { "Error deleting doc with id: $id: ${e.message}" } }
            .getOrThrow()
    }

    override fun list(): List<String> = runBlocking {
        logger.info { "Listing all docs" }
        runCatching {
            s3Client.listObjectsV2(ListObjectsV2Request { bucket = bucketName })
        }.onFailure { e -> logger.error(e) { "Failed to list docs" } }
            .getOrThrow().contents?.mapNotNull { it.key } ?: emptyList()
    }
}
