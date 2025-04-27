package com.example.clean.architecture.azure


import com.example.clean.architecture.test.config.LocalTestConfiguration
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

@SpringBootTest
@ActiveProfiles("local")
@Import(LocalTestConfiguration::class)
class DocsFlowFunctionsIntegrationTest {

    @Autowired
    private lateinit var docsFlowFunctions: DocsFlowFunctions
    private val context = mockk<ExecutionContext>(relaxed = true)
    val request =
        mockk<HttpRequestMessage<String>>(relaxed = true)

    @Test
    fun `When valid request then 201 response from DocsFlow`() {
        every { request.httpMethod } returns HttpMethod.POST
        every { request.body } returns "test"
        docsFlowFunctions.uploadDocument(
            request,
            context
        )
        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        201
                    )
                )
        }
    }
}
