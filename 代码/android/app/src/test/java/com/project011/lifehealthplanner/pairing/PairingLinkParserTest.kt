package com.project011.lifehealthplanner.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PairingLinkParserTest {
    @Test
    fun `accepts encoded HTTPS Tailscale address and strict code`() {
        val result = PairingLinkParser.parse(link("https://desktop.tail123.ts.net", "123456"))

        assertEquals(
            PairingLinkResult.Valid(
                PairingRequest(
                    serverUrl = "https://desktop.tail123.ts.net",
                    serverHost = "desktop.tail123.ts.net",
                    code = "123456",
                ),
            ),
            result,
        )
    }

    @Test
    fun `accepts explicit default HTTPS port and normalizes address`() {
        val result = PairingLinkParser.parse(link("https://Desktop.Tail123.ts.net:443/", "12345678"))

        assertEquals(
            PairingLinkResult.Valid(
                PairingRequest(
                    serverUrl = "https://desktop.tail123.ts.net",
                    serverHost = "desktop.tail123.ts.net",
                    code = "12345678",
                ),
            ),
            result,
        )
    }

    @Test
    fun `accepts and preserves the fixed companion port`() {
        val result = PairingLinkParser.parse(link("https://desktop.tail123.ts.net:8443", "001234"))

        assertEquals(
            PairingLinkResult.Valid(
                PairingRequest(
                    serverUrl = "https://desktop.tail123.ts.net:8443",
                    serverHost = "desktop.tail123.ts.net",
                    code = "001234",
                ),
            ),
            result,
        )
    }

    @Test
    fun `rejects codes that are not six to eight ASCII digits`() {
        listOf("12345", "123456789", "12a456", "１２３４５６", "123 456").forEach { code ->
            assertInvalid(link("https://desktop.tail123.ts.net", code), "配对码格式无效")
        }
    }

    @Test
    fun `rejects non HTTPS and non Tailscale servers`() {
        listOf(
            "http://desktop.tail123.ts.net",
            "https://ts.net",
            "https://desktop.example.com",
            "https://desktop.tail123.ts.net.evil.example",
        ).forEach { server ->
            assertInvalid(link(server, "123456"), "电脑地址无效")
        }
    }

    @Test
    fun `rejects user info fragments queries paths and abnormal ports`() {
        listOf(
            "https://user@desktop.tail123.ts.net",
            "https://desktop.tail123.ts.net#evil",
            "https://desktop.tail123.ts.net?redirect=evil",
            "https://desktop.tail123.ts.net/api",
            "https://desktop.tail123.ts.net:8444",
            "https://desktop.tail123.ts.net:8765",
            "https://desktop.tail123.ts.net:0",
        ).forEach { server ->
            assertInvalid(link(server, "123456"), "电脑地址无效")
        }
    }

    @Test
    fun `rejects malformed or empty server hosts`() {
        listOf(
            "https://",
            "https:///pair",
            "https://-bad.tail123.ts.net",
            "https://bad-.tail123.ts.net",
            "https://bad..tail123.ts.net",
        ).forEach { server ->
            assertInvalid(link(server, "123456"), "电脑地址无效")
        }
    }

    @Test
    fun `rejects malformed outer links and query parameter pollution`() {
        listOf(
            "https://pair?server=x&code=123456",
            "lifehealth://other?server=x&code=123456",
            "lifehealth://pair/path?server=x&code=123456",
            "lifehealth://pair?server=x&code=123456#fragment",
            "lifehealth://user@pair?server=x&code=123456",
            "lifehealth://pair?server=x&server=y&code=123456",
            "lifehealth://pair?server=x&code=123456&extra=value",
            "lifehealth://pair?server=%ZZ&code=123456",
        ).forEach { rawLink ->
            assertInvalid(rawLink, "配对链接格式无效")
        }
    }

    @Test
    fun `rejects empty values and malformed separators`() {
        assertInvalid("lifehealth://pair?server=&code=123456", "电脑地址无效")
        assertInvalid("lifehealth://pair?server=x&code=", "配对码格式无效")
        listOf(
            "lifehealth://pair?server=x&code=123456&",
            "lifehealth://pair?server=x&&code=123456",
            "lifehealth://pair?server=x&code=123456=7",
        ).forEach { rawLink ->
            assertInvalid(rawLink, "配对链接格式无效")
        }
    }

    private fun assertInvalid(rawLink: String, expectedMessage: String) {
        val result = PairingLinkParser.parse(rawLink)
        assertTrue("Expected invalid result for $rawLink", result is PairingLinkResult.Invalid)
        assertTrue((result as PairingLinkResult.Invalid).message.contains(expectedMessage))
    }

    private fun link(server: String, code: String): String =
        "lifehealth://pair?server=${encode(server)}&code=${encode(code)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
