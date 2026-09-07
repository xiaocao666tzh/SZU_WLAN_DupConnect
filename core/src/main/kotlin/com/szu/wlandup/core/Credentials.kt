package com.szu.wlandup.core

data class Credentials(
    val userAccount: String,
    val userPassword: String,
) {
    fun isComplete(): Boolean =
        userAccount.isNotBlank() && userPassword.isNotBlank()
}
