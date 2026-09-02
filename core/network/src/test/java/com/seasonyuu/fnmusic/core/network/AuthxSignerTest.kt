package com.seasonyuu.fnmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthxSignerTest {
    private val signer = AuthxSigner(
        nonceProvider = { "123456" },
        timeProvider = { 1_700_000_000_000L },
    )

    @Test
    fun `GET sorts and decodes query before hashing`() {
        val actual = signer.sign(
            method = "GET",
            encodedPath = "/music/api/v1/track/list",
            queryPairs = listOf("sort" to "createdAt,desc", "size" to "12", "page" to "1"),
            body = null,
        )

        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=2f1a1949508596a507ff2d8573e19005",
            actual,
        )
    }

    @Test
    fun `POST hashes exact serialized body`() {
        val actual = signer.sign(
            method = "POST",
            encodedPath = "/music/api/v1/user/password-login",
            queryPairs = emptyList(),
            body = "{\"username\":\"user\",\"password\":\"hash\",\"deviceId\":\"device\"}",
        )

        assertEquals(
            "nonce=123456&timestamp=1700000000000&sign=563ded6de14100a640b16eb46e5d646e",
            actual,
        )
    }

    @Test
    fun `GET discards null-like values and keeps last duplicate`() {
        val withNoise = signer.sign(
            method = "GET",
            encodedPath = "/music/api/v1/search/track",
            queryPairs = listOf("q" to "first", "unused" to "null", "q" to "音乐", "other" to "undefined"),
            body = null,
        )
        val clean = signer.sign(
            method = "GET",
            encodedPath = "/music/api/v1/search/track",
            queryPairs = listOf("q" to "音乐"),
            body = null,
        )

        assertEquals(clean, withNoise)
    }
}
