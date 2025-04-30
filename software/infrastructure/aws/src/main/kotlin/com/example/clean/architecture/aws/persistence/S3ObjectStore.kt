package com.example.clean.architecture.aws.persistence

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.smithy.kotlin.runtime.content.ByteStream
import com.example.clean.architecture.persistence.ObjectStorageInterface
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import kotlin.time.Duration.Companion.hours

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

    override fun generateSecureAccessUri(id: String): String = runBlocking {
        logger.info { "Generating presigned URL for doc with id: $id" }
        runCatching {
            val request = GetObjectRequest {
                bucket = bucketName
                key = id
            }
            val presigned = s3Client.presignGetObject(request, 24.hours)
            val url = presigned.url.toString()
            logger.info { "Generated presigned URL: $url" }
            url
        }.onFailure { e ->
            logger.error(e) { "Failed to generate presigned URL for doc with id: $id" }
        }.getOrThrow()
    }
}
