package com.example.clean.architecture.test.config

import aws.sdk.kotlin.services.s3.S3Client
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LocalTestConfiguration {

    @Bean
    fun s3Client(): S3Client = mockk(relaxed = true)
}