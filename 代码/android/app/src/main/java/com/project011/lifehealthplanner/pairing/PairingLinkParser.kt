package com.project011.lifehealthplanner.pairing

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class PairingRequest(
    val serverUrl: String,
    val serverHost: String,
    val code: String,
)

sealed interface PairingLinkResult {
    data class Valid(val request: PairingRequest) : PairingLinkResult

    data class Invalid(val message: String) : PairingLinkResult
}

object PairingLinkParser {
    private val pairingCode = Regex("[0-9]{6,8}")
    private val dnsLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun parse(rawLink: String): PairingLinkResult {
        if (rawLink.length > MAX_LINK_LENGTH) return invalidLink()
        val link = parseUri(rawLink) ?: return invalidLink()
        if (
            !link.scheme.equals("lifehealth", ignoreCase = true) ||
            !link.host.equals("pair", ignoreCase = true) ||
            link.rawUserInfo != null ||
            link.rawFragment != null ||
            !link.rawPath.isNullOrEmpty() ||
            link.port != -1
        ) {
            return invalidLink()
        }

        val parameters = parseQuery(link.rawQuery) ?: return invalidLink()
        if (parameters.keys != setOf("server", "code")) return invalidLink()

        val code = parameters.getValue("code")
        if (!pairingCode.matches(code)) {
            return PairingLinkResult.Invalid("配对码格式无效，必须为 6 至 8 位数字")
        }

        val server = validateServer(parameters.getValue("server"))
            ?: return PairingLinkResult.Invalid(
                "电脑地址无效，只能连接 HTTPS 的 Tailscale（ts.net）地址",
            )
        return PairingLinkResult.Valid(
            PairingRequest(
                serverUrl = server.normalizedUrl,
                serverHost = server.host,
                code = code,
            ),
        )
    }

    /** Returns a canonical private Tailscale endpoint for manual pairing, or null when invalid. */
    fun normalizeServerUrl(rawServer: String): String? = validateServer(rawServer)?.normalizedUrl

    private fun validateServer(rawServer: String): ValidServer? {
        val uri = parseUri(rawServer) ?: return null
        if (
            !uri.scheme.equals("https", ignoreCase = true) ||
            uri.isOpaque ||
            uri.rawAuthority.isNullOrEmpty() ||
            uri.rawUserInfo != null ||
            uri.rawQuery != null ||
            uri.rawFragment != null ||
            uri.port !in ALLOWED_HTTPS_PORTS ||
            !(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
        ) {
            return null
        }

        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!host.endsWith(".ts.net") || host.length <= ".ts.net".length) return null
        if (host.length > 253 || host.split('.').any { !dnsLabel.matches(it) }) return null

        val normalizedAuthority = uri.rawAuthority.lowercase(Locale.ROOT)
        val expectedAuthority = if (uri.port == -1) host else "$host:${uri.port}"
        if (normalizedAuthority != expectedAuthority) return null
        val normalizedUrl = if (uri.port == COMPANION_PORT) {
            "https://${host}:$COMPANION_PORT"
        } else {
            "https://$host"
        }
        return ValidServer(host, uri.port, normalizedUrl)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String>? {
        if (rawQuery.isNullOrEmpty()) return null
        val result = linkedMapOf<String, String>()
        var start = 0
        while (start <= rawQuery.length) {
            val separator = rawQuery.indexOf('&', start).let { if (it == -1) rawQuery.length else it }
            val field = rawQuery.substring(start, separator)
            val equals = field.indexOf('=')
            if (field.isEmpty() || equals <= 0 || equals != field.lastIndexOf('=')) return null
            val name = decode(field.substring(0, equals)) ?: return null
            val value = decode(field.substring(equals + 1)) ?: return null
            if (name in result) return null
            result[name] = value
            if (separator == rawQuery.length) break
            start = separator + 1
        }
        return result
    }

    private fun parseUri(value: String): URI? = runCatching { URI(value) }.getOrNull()

    private fun decode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private fun invalidLink() = PairingLinkResult.Invalid("配对链接格式无效，请在电脑端重新生成二维码")

    private data class ValidServer(val host: String, val port: Int, val normalizedUrl: String)

    private const val MAX_LINK_LENGTH = 4096

    private const val COMPANION_PORT = 8443
    private val ALLOWED_HTTPS_PORTS = setOf(-1, 443, COMPANION_PORT)
}
