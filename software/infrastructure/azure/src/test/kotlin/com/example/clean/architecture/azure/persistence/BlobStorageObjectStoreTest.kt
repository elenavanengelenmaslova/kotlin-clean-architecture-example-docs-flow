package com.example.clean.architecture.azure.persistence

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class BlobStorageObjectStoreTest {

    private val containerClient: BlobContainerClient = mockk(relaxed = true)
    private val blobServiceClient: BlobServiceClient = mockk(relaxed = true)

    private val objectStore = BlobStorageObjectStore(containerClient, blobServiceClient)

    @AfterEach
    fun tearDown() {
        clearMocks(containerClient, blobServiceClient)
    }

    @Test
    fun `Given a cold instance When warmUp is called Then it forces the credential fetch via a single exists call`() {
        // When
        objectStore.warmUp()

        // Then a single low-cost exists() call opens the connection and forces the
        // DefaultAzureCredential managed-identity token fetch.
        verify(exactly = 1) { containerClient.exists() }
    }
}
