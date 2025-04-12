package com.example.cdk.aws

import com.hashicorp.cdktf.App

fun main() {
    val app = App()
    AwsStack(app, "Demo-Spring-Clean-Architecture-Lambda")
    app.synth()
}
