package com.example.clean.architecture.aws.config

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.ses.SesClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!local")
class AWSConfig(@Value("\${aws.default.region}") private val awsRegion: String,) {

    @Bean
    fun s3Client(): S3Client = S3Client { region = awsRegion }

    @Bean
    fun sesClient(): SesClient = SesClient { region = awsRegion }
}