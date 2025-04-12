package com.example.cdk.aws

import com.hashicorp.cdktf.Fn
import com.hashicorp.cdktf.S3Backend
import com.hashicorp.cdktf.S3BackendConfig
import com.hashicorp.cdktf.TerraformStack
import com.hashicorp.cdktf.providers.aws.api_gateway_api_key.ApiGatewayApiKey
import com.hashicorp.cdktf.providers.aws.api_gateway_api_key.ApiGatewayApiKeyConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_deployment.ApiGatewayDeployment
import com.hashicorp.cdktf.providers.aws.api_gateway_deployment.ApiGatewayDeploymentConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_integration.ApiGatewayIntegration
import com.hashicorp.cdktf.providers.aws.api_gateway_integration.ApiGatewayIntegrationConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_method.ApiGatewayMethod
import com.hashicorp.cdktf.providers.aws.api_gateway_method.ApiGatewayMethodConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_resource.ApiGatewayResource
import com.hashicorp.cdktf.providers.aws.api_gateway_resource.ApiGatewayResourceConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_rest_api.ApiGatewayRestApi
import com.hashicorp.cdktf.providers.aws.api_gateway_rest_api.ApiGatewayRestApiConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_stage.ApiGatewayStage
import com.hashicorp.cdktf.providers.aws.api_gateway_stage.ApiGatewayStageConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_usage_plan.ApiGatewayUsagePlan
import com.hashicorp.cdktf.providers.aws.api_gateway_usage_plan.ApiGatewayUsagePlanApiStages
import com.hashicorp.cdktf.providers.aws.api_gateway_usage_plan.ApiGatewayUsagePlanConfig
import com.hashicorp.cdktf.providers.aws.api_gateway_usage_plan_key.ApiGatewayUsagePlanKey
import com.hashicorp.cdktf.providers.aws.api_gateway_usage_plan_key.ApiGatewayUsagePlanKeyConfig
import com.hashicorp.cdktf.providers.aws.iam_policy.IamPolicy
import com.hashicorp.cdktf.providers.aws.iam_policy.IamPolicyConfig
import com.hashicorp.cdktf.providers.aws.iam_role.IamRole
import com.hashicorp.cdktf.providers.aws.iam_role.IamRoleConfig
import com.hashicorp.cdktf.providers.aws.iam_role_policy.IamRolePolicy
import com.hashicorp.cdktf.providers.aws.iam_role_policy.IamRolePolicyConfig
import com.hashicorp.cdktf.providers.aws.lambda_function.LambdaFunction
import com.hashicorp.cdktf.providers.aws.lambda_function.LambdaFunctionConfig
import com.hashicorp.cdktf.providers.aws.lambda_function.LambdaFunctionEnvironment
import com.hashicorp.cdktf.providers.aws.lambda_permission.LambdaPermission
import com.hashicorp.cdktf.providers.aws.lambda_permission.LambdaPermissionConfig
import com.hashicorp.cdktf.providers.aws.provider.AwsProvider
import com.hashicorp.cdktf.providers.aws.provider.AwsProviderConfig
import com.hashicorp.cdktf.providers.aws.s3_bucket.S3Bucket
import com.hashicorp.cdktf.providers.aws.s3_bucket.S3BucketConfig
import com.hashicorp.cdktf.providers.random_provider.provider.RandomProvider
import com.hashicorp.cdktf.providers.random_provider.string_resource.StringResource
import software.constructs.Construct


class AwsStack(
    scope: Construct,
    id: String,
) : TerraformStack(scope, id) {

    init {
        // Get the region from environment variables
        val region =
            System.getenv("DEPLOY_TARGET_REGION")

        // Configure the AWS Provider
        AwsProvider(
            this,
            "Aws",
            AwsProviderConfig.builder().region(region)
                .build()
        )

        // Configure Terraform Backend to Use S3
        S3Backend(
            this,
            S3BackendConfig.builder()
                .region(region)
                .bucket("kotlin-lambda-terraform-state")
                .key("demo-terraform-cdk/terraform.tfstate")
                .encrypt(true).build()
        )

        RandomProvider(this, "Random")
        // Create a unique random ID
        val bucketSuffix = StringResource.Builder.create(this, "bucketSuffix")
            .length(8)
            .special(false)  // No special characters
            .upper(false)    // No uppercase letters
            .numeric(false)  // No numbers (optional, but you can allow them)
            .build()
            .result

        // Define S3 bucket for MockNest mappings
        val s3Bucket = S3Bucket(
            this,
            "DemoMockNestMappingsBucket",
            S3BucketConfig.builder()
                .bucket("demo-mocknest-mappings-$bucketSuffix")
                .build()
        )

        val lambdaRole = IamRole(
            this,
            "Demo-Spring-Clean-Architecture-Fun-Role",
            IamRoleConfig.builder()
                .name("Demo-Spring-Clean-Architecture-Fun-Role")
                .assumeRolePolicy(
                    """{
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Action": "sts:AssumeRole",
                            "Principal": {"Service": "lambda.amazonaws.com"},
                            "Effect": "Allow"
                        }
                    ]
                }"""
                ).build()
        )

        val policy = IamPolicy(
            this,
            "Demo-Spring-Clean-Architecture-Fun-Policy",
            IamPolicyConfig.builder()
                .name("Demo-Spring-Clean-Architecture-Fun-Policy")
                .dependsOn(listOf(s3Bucket))
                .policy(
                    """
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:*:*:*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:ListBucket",
                "s3:DeleteObject"
            ],
            "Resource": [
                "${s3Bucket.arn}",          
                "${s3Bucket.arn}/*"   
            ]
        }
    ]
}
                    """.trimIndent()
                )
                .build()
        )

        IamRolePolicy(
            this,
            "Demo-Spring-Clean-Architecture-Fun-RolePolicy",
            IamRolePolicyConfig.builder()
                .name("Demo-Spring-Clean-Architecture-Fun-RolePolicy")
                .policy(policy.policy)
                .role(lambdaRole.name).build()
        )


        val lambdaFunction = LambdaFunction(
            this,
            "Demo-Spring-Clean-Architecture-Fun",
            LambdaFunctionConfig.builder()
                .functionName("Demo-Spring-Clean-Architecture-Fun")
                .handler("org.springframework.cloud.function.adapter.aws.FunctionInvoker")
                .runtime("java17")
                .s3Bucket("lambda-deployment-clean-architecture-example")
                .s3Key("demo-aws-function.jar")
                .sourceCodeHash(
                    Fn.filebase64sha256("../../../../../build/dist/demo-aws-function.jar")
                )
                .role(lambdaRole.arn)
                .dependsOn(
                    listOf(
                        s3Bucket,
                        lambdaRole
                    )
                )
                .memorySize(1024)
                .snapStart { "PublishedVersions" }
                .environment(
                    LambdaFunctionEnvironment.builder()
                        .variables(
                            mapOf(
                                "SPRING_CLOUD_FUNCTION_DEFINITION" to "router",
                                "MAIN_CLASS" to "com.example.clean.architecture.Application",
                                "AWS_S3_BUCKET_NAME" to s3Bucket.bucket,
                                //Stop at level 1 (C1 compiler)
                                "JAVA_TOOL_OPTIONS" to "-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
                            )
                        ).build()
                )
                .timeout(120)
                .build()
        )

        // Create API Gateway REST API (API Gateway v1)
        val api = ApiGatewayRestApi(
            this,
            "Demo-Spring-Clean-Architecture-API",
            ApiGatewayRestApiConfig.builder()
                .name("Demo-Spring-Clean-Architecture-API")
                .description("API for Spring Clean Architecture Example")
                // Using REGIONAL endpoint type
                .build()
        )

        // Create API Gateway Resource
        val resource = ApiGatewayResource(
            this,
            "Demo-Spring-Clean-Architecture-Resource",
            ApiGatewayResourceConfig.builder()
                .restApiId(api.id)
                .parentId(api.rootResourceId)
                .pathPart("{proxy+}")
                .build()
        )

        // Create API Gateway Method
        val method = ApiGatewayMethod(
            this,
            "Demo-Spring-Clean-Architecture-Method",
            ApiGatewayMethodConfig.builder()
                .restApiId(api.id)
                .resourceId(resource.id)
                .httpMethod("ANY")
                .authorization("NONE")
                .apiKeyRequired(true)  // Require API key
                .build()
        )

        // Create Lambda integration for API Gateway
        val integration = ApiGatewayIntegration(
            this,
            "Demo-Spring-Clean-Architecture-Integration",
            ApiGatewayIntegrationConfig.builder()
                .restApiId(api.id)
                .resourceId(resource.id)
                .httpMethod(method.httpMethod)
                .integrationHttpMethod("POST")
                .type("AWS_PROXY")
                .uri("arn:aws:apigateway:${System.getenv("DEPLOY_TARGET_REGION")}:lambda:path/2015-03-31/functions/${lambdaFunction.arn}/invocations")
                .build()
        )

        // Create API Gateway Deployment
        val deployment = ApiGatewayDeployment(
            this,
            "Demo-Spring-Clean-Architecture-Deployment",
            ApiGatewayDeploymentConfig.builder()
                .restApiId(api.id)
                .dependsOn(listOf(integration))
                .build()
        )

        // Create API Gateway Stage
        val stage = ApiGatewayStage(
            this,
            "Demo-Spring-Clean-Architecture-Stage",
            ApiGatewayStageConfig.builder()
                .restApiId(api.id)
                .deploymentId(deployment.id)
                .stageName("demo")
                .build()
        )

        // Create API Key
        val apiKey = ApiGatewayApiKey(
            this,
            "Demo-Spring-Clean-Architecture-ApiKey",
            ApiGatewayApiKeyConfig.builder()
                .name("Demo-Spring-Clean-Architecture-ApiKey")
                .description("API Key for Spring Clean Architecture Example")
                .enabled(true)
                .build()
        )

        // Create Usage Plan and associate it with the "prod" stage
        val usagePlan = ApiGatewayUsagePlan(
            this,
            "Demo-Spring-Clean-Architecture-UsagePlan",
            ApiGatewayUsagePlanConfig.builder()
                .name("Demo-Spring-Clean-Architecture-UsagePlan")
                .description("Usage Plan for Spring Clean Architecture Example")
                .apiStages(
                    listOf(
                        ApiGatewayUsagePlanApiStages.builder()
                            .apiId(api.id)          // Link to API Gateway
                            .stage(stage.stageName) // Associate with "prod" stage
                            .build()
                    )
                )
                .build()
        )


        // Link API Key to Usage Plan
        ApiGatewayUsagePlanKey(
            this,
            "Demo-Spring-Clean-Architecture-UsagePlanKey",
            ApiGatewayUsagePlanKeyConfig.builder()
                .keyId(apiKey.id)
                .keyType("API_KEY")
                .usagePlanId(usagePlan.id)
                .build()
        )

        // Grant API Gateway permission to invoke Lambda
        LambdaPermission(
            this,
            "Demo-Spring-Clean-Architecture-Permission",
            LambdaPermissionConfig.builder()
                .functionName(lambdaFunction.functionName)
                .action("lambda:InvokeFunction")
                .principal("apigateway.amazonaws.com")
                .sourceArn("arn:aws:execute-api:${System.getenv("DEPLOY_TARGET_REGION")}:${System.getenv("DEPLOY_TARGET_ACCOUNT")}:${api.id}/*/${method.httpMethod}/${resource.pathPart}")
                .build()
        )
    }
}
