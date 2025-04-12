package com.example.cdk.azure

import com.hashicorp.cdktf.App

fun main() {
    val app = App()
    AzureStack(app, "Demo-Azure-Clean-Architecture")
    app.synth()
}