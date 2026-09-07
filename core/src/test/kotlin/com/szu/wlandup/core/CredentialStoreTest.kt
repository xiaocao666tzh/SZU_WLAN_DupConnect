package com.szu.wlandup.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CredentialStoreTest {
    @TempDir
    lateinit var tempDir: File

    private fun store(): CredentialStore = CredentialStore(File(tempDir, "credentials.dat"))

    @Test
    fun saveLoadDeleteRoundTrip() {
        val store = store()
        assertNull(store.load())
        assertFalse(store.hasCredentials())

        val creds = Credentials("2020123456", "secret&pass=1")
        store.save(creds)

        val loaded = store.load()
        assertEquals(creds, loaded)
        assertTrue(store.hasCredentials())

        store.delete()
        assertNull(store.load())
        assertFalse(store.hasCredentials())
    }

    @Test
    fun persistsAcrossNewStoreInstance() {
        val file = File(tempDir, "credentials.dat")
        CredentialStore(file).save(Credentials("id1", "pw1"))
        val again = CredentialStore(file).load()
        assertEquals(Credentials("id1", "pw1"), again)
    }
}
