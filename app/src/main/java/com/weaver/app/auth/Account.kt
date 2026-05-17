package com.weaver.app.auth

data class Account(
    val id: String,
    val email: String,
    val displayName: String?,
    val idToken: String?,
)
