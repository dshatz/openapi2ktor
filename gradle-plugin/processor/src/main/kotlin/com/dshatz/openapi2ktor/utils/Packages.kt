package com.dshatz.openapi2ktor.utils

data class Packages(private val basePackage: String) {

    val models: String = "$basePackage.models"
    val client: String = "$basePackage.client"

}