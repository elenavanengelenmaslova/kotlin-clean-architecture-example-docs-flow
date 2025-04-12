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
class MockNestFunctionsIntegrationTest {

    @Autowired
    private lateinit var mockNestFunctions: MockNestFunctions
    private val context = mockk<ExecutionContext>()
    val request =
        mockk<HttpRequestMessage<String>>(relaxed = true)

    @Test
    fun `When mapped SF request then maps SF response in MockNest`() {
        every { request.httpMethod } returns HttpMethod.POST
        every { request.body } returns "{\n" +
                "    \"allOrNone\": true,\n" +
                "    \"compositeRequest\": [\n" +
                "        {\n" +
                "            \"method\": \"GET\",\n" +
                "            \"referenceId\": \"AccountRequest\",\n" +
                "            \"url\": \"/services/data/v59.0/}/query/?q=SELECT+Id,+Name,+Employer__r.EmployerId__c,+Employer__r.Id,+EffectiveTo__c+FROM+PayrollTaxNumber__c+WHERE+EffectiveTo__c=null+AND+Name='861733757L02' LIMIT 1\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"method\": \"GET\",\n" +
                "            \"referenceId\": \"AcountAccountRequest\",\n" +
                "            \"url\": \"/services/data/v59.0/query/?q=SELECT+FinServ__RelatedAccount__r.PensionFundId__c+FROM+FinServ__AccountAccountRelation__c+WHERE+(FinServ__Account__c IN (SELECT Employer__c FROM PayrollTaxNumber__c WHERE EffectiveTo__c=null AND Name='861733757L02s'))\"\n" +
                "        }\n" +
                "    ]\n" +
                "}"
        mockNestFunctions.forwardClientRequest(
            request,
            "services/data/v59.0/composite",
            context
        )
        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When regex match request then maps to a success response`() {
        every { request.httpMethod } returns HttpMethod.GET
        every { request.body } returns """
        {
            "cashflow": {
                "cashflowId": 1,
                "cashflowType": "NEW"
            }
        }
    """.trimIndent()

        mockNestFunctions.forwardClientRequest(request, "invoices/PN2000000001/versions/latest", context)
        verify {
            request
                .createResponseBuilder(HttpStatus.valueOf(200))
        }
    }

    @Test
    fun `When deleting a MockNest mapping then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.DELETE

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/8c5db8b0-2db4-4ad7-a99f-38c9b00da3f7",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When retrieving near misses then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.GET

        mockNestFunctions.forwardAdminRequest(
            request,
            "requests/unmatched/near-misses",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }

    @Test
    fun `When resetting MockNest mappings then returns 200 status code`() {
        every { request.httpMethod } returns HttpMethod.POST

        mockNestFunctions.forwardAdminRequest(
            request,
            "mappings/reset",
            context
        )

        verify {
            request
                .createResponseBuilder(
                    HttpStatus.valueOf(
                        200
                    )
                )
        }
    }
}
