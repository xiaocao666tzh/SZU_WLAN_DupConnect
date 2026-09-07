package com.szu.wlandup.core

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Local-only credential persistence. Same class is used by the app (filesDir) and unit tests.
 * Credentials are never uploaded anywhere by this store.
 */
class CredentialStore(private val file: File) {
    fun load(): Credentials? {
        if (!file.exists()) return null
        val text = file.readText(StandardCharsets.UTF_8).trim()
        if (text.isEmpty()) return null
        val lines = text.lines()
        if (lines.size < 2) return null
        val account = lines[0]
        val password = lines.drop(1).joinToString("\n")
        val creds = Credentials(account, password)
        return if (creds.isComplete()) creds else null
    }

    fun save(credentials: Credentials) {
        require(credentials.isComplete()) { "credentials incomplete" }
        file.parentFile?.mkdirs()
        file.writeText(
            credentials.userAccount + "\n" + credentials.userPassword,
            StandardCharsets.UTF_8,
        )
    }

    fun delete() {
        if (file.exists()) {
            file.delete()
        }
    }

    fun hasCredentials(): Boolean = load() != null
}
