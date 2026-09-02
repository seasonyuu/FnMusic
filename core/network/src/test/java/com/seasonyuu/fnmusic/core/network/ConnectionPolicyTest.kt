package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.MusicError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConnectionPolicyTest {
    private val policy = ConnectionPolicy()

    @Test
    fun `trusted HTTPS is normalized to music root`() = runBlocking {
        val result = policy.validateAndNormalize("https://nas.example.com", allowPrivateLanHttp = false)
        assertEquals("https://nas.example.com/music/", result.toString())
    }

    @Test
    fun `private loopback HTTP requires explicit consent`() {
        assertThrows(MusicError.UnsafeConnection::class.java) {
            runBlocking { policy.validateAndNormalize("http://127.0.0.1:5666", allowPrivateLanHttp = false) }
        }
    }

    @Test
    fun `private loopback HTTP is accepted after consent`() = runBlocking {
        val result = policy.validateAndNormalize("http://127.0.0.1:5666/music", allowPrivateLanHttp = true)
        assertEquals("http://127.0.0.1:5666/music/", result.toString())
    }

    @Test
    fun `public HTTP is rejected even after consent`() {
        assertThrows(MusicError.UnsafeConnection::class.java) {
            runBlocking { policy.validateAndNormalize("http://8.8.8.8", allowPrivateLanHttp = true) }
        }
    }
}
