package com.example.clean.architecture.aws.persistence

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.HeadBucketRequest
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Verifies the S3 adapter's Phase 2 (Warmable) connection warmup performs a single low-cost
 * `headBucket` call that opens the connection without transferring any payload.
 */
class S3ObjectStoreTest {

    private val s3Client: S3Client = mockk(relaxed = true)
    private val bucketName = "docs-flow-bucket"

    private val objectStore = S3ObjectStore(bucketName, s3Client)

    @AfterEach
    fun tearDown() {
        clearMocks(s3Client)
    }

    @Test
    fun `Given a cold instance When warmUp is called Then it opens the connection via a single headBucket call`() {
        // When
        objectStore.warmUp()

        // Then — exactly one headBucket call opens the S3 connection (no payload, not a real upload)
        coVerify(exactly = 1) { s3Client.headBucket(any<HeadBucketRequest>()) }
    }
}
