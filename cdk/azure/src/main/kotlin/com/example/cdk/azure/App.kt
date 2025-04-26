package com.example.cdk.azure

import com.hashicorp.cdktf.App

fun main() {
    val app = App()
    AzureStack(app, "Docs-Flow-Azure-Clean-Architecture")
    app.synth()
}