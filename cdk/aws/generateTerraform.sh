#!/bin/bash
set -e

# Step 1: Go to project root (this assumes script is in scripts/)
cd "$(dirname "$0")/../.."

# Step 2: Build the Lambda JAR
echo "🔨 Building Lambda JAR..."
./gradlew clean :infra-aws:build :infra-aws:shadowJar --no-build-cache

# Step 3: Set vars so CDK Kotlin can use them
export DEPLOY_TARGET_REGION="eu-west-1"
export DEPLOY_TARGET_ACCOUNT="021259937026"


# Step 4: Generate Terraform files
echo "📦 Generating Terraform files from CDK..."
cd cdk/aws
cdktf get
cdktf synth

echo "✅ Terraform generated in: cdk/aws/cdktf.out/stacks"
