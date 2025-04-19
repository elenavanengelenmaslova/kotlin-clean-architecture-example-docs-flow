#!/bin/bash
set -e

# Step 1: Go to project root (this assumes script is in scripts/)
cd "$(dirname "$0")/../.."

# Step 2: Generate Terraform files
echo "📦 Generating Terraform files from CDK..."
cd cdk/azure
cdktf get
cdktf synth

echo "✅ Terraform generated in: cdk/aws/cdktf.out/stacks"

# Step 3: Clean up not needed files
echo "Cleaning"

rm -rf src/main/java
rm -rf src/main/resources

echo "✅ Done!"
