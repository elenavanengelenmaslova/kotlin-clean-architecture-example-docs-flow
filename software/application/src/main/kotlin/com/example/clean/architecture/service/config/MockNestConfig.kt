package com.example.clean.architecture.service.config

import com.example.clean.architecture.persistence.ObjectStorageInterface
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn


private val logger = KotlinLogging.logger {}

@Configuration
class MockNestConfig {

    @Value("\${mocknest.root-dir:mocknest}")
    private val rootDir: String = "mocknest"

    @Bean
    fun directCallHttpServerFactory() = DirectCallHttpServerFactory()

    @Bean
    fun wireMockServer(directCallHttpServerFactory: DirectCallHttpServerFactory, mappingRepository: ObjectStorageInterface): WireMockServer {
        val wireMockServer = WireMockServer(
            wireMockConfig()
                .usingFilesUnderClasspath(rootDir)
                .notifier(ConsoleNotifier(true))
                .httpServerFactory(directCallHttpServerFactory)
        )
        wireMockServer.start()
        wireMockServer.loadMappingsFromStorage(mappingRepository)
        logger.info { "MockNest server started with root dir: $rootDir" }
        return wireMockServer
    }


    @Bean
    @DependsOn("wireMockServer")
    fun directCallHttpServer(directCallHttpServerFactory: DirectCallHttpServerFactory): DirectCallHttpServer {
        return directCallHttpServerFactory.httpServer
    }

    private fun WireMockServer.loadMappingsFromStorage(objectStorageRepository: ObjectStorageInterface) {
        logger.info { "Loading mappings from storage..." }

        val mappings = objectStorageRepository.list()
        if (mappings.isEmpty()) {
            logger.info { "No mappings found in storage." }
            return
        }

        mappings.forEach { mappingId ->
            objectStorageRepository.get(mappingId)?.let {
                val stubMapping = StubMapping.buildFrom(it)
                addStubMapping(stubMapping)
                logger.info { "Loaded mapping: $mappingId into MockNest memory" }
            }?: logger.warn { "Failed to load mapping: $mappingId" }
        }
        logger.info { "Finished loading mappings from storage." }
    }
}
