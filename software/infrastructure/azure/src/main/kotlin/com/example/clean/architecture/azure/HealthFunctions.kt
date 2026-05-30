package com.example.clean.architecture.azure

import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import org.springframework.stereotype.Component

@Component
class HealthFunctions {

    @FunctionName("Health")
    fun health(
        @HttpTrigger(
            name = "request",
            methods = [HttpMethod.GET],
            authLevel = AuthorizationLevel.FUNCTION,
            route = "health"
        ) request: HttpRequestMessage<Void>,
        context: ExecutionContext,
    ): HttpResponseMessage {
        return runCatching {
            request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("""{"status":"UP"}""")
                .build()
        }.getOrElse {
            request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Content-Type", "application/json")
                .body("""{"status":"DOWN"}""")
                .build()
        }
    }
}
