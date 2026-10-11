package cn.com.omnimind.nativeui.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLDecoder

/** What a PC Bridge QR code carries (Dart `CodexBridgeQrScanResult`). */
@Immutable
data class BridgeQrCode(val bridgeUrl: String, val token: String = "", val cwd: String = "")

private val URL_KEYS = listOf("bridgeUrl", "remoteBridgeUrl", "url", "wsUrl")
private val TOKEN_KEYS = listOf("token", "bridgeToken", "remoteBridgeToken")
private val CWD_KEYS = listOf("cwd", "remoteCwd")
private const val DEFAULT_BRIDGE_PORT = 17321

/**
 * Reads a PC Bridge QR code (Dart `CodexBridgeQrScanResult.tryParse`). It
 * accepts three forms:
 * - a JSON payload typed `omnibot.codex_bridge`;
 * - an `omnibot://codex-bridge?bridgeUrl=…` link;
 * - a bare Bridge URL with the token in its query.
 * Anything else is null.
 */
fun parseBridgeQrCode(raw: String): BridgeQrCode? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    parseJson(text)?.let { return it }
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val query = queryOf(uri)
    if (uri.scheme == "omnibot" && (uri.host == "codex-bridge" || uri.path.orEmpty().contains("codex-bridge"))) {
        val url = first(query, URL_KEYS) ?: return null
        return BridgeQrCode(url, first(query, TOKEN_KEYS).orEmpty(), first(query, CWD_KEYS).orEmpty())
    }
    if (isBridgeUrl(uri)) {
        return BridgeQrCode(withoutQuickConnect(uri, query), first(query, TOKEN_KEYS).orEmpty(), first(query, CWD_KEYS).orEmpty())
    }
    return null
}

private fun parseJson(text: String): BridgeQrCode? {
    if (!text.startsWith("{")) return null
    val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    // Numbers and booleans read as text too, like Dart's `toString()`.
    fun string(key: String) = (json[key] as? JsonPrimitive)?.content?.trim().orEmpty()
    val type = string("type")
    if (type.isNotEmpty() && type != "omnibot.codex_bridge" && type != "codex_bridge") return null
    fun value(keys: List<String>) = keys.firstNotNullOfOrNull { key -> string(key).takeIf { it.isNotEmpty() } }
    val url = value(URL_KEYS) ?: return null
    return BridgeQrCode(url, value(TOKEN_KEYS).orEmpty(), value(CWD_KEYS).orEmpty())
}

/** Query parameters in order; a repeated key keeps its last value, as Dart's `queryParameters`. */
private fun queryOf(uri: URI): LinkedHashMap<String, String> {
    val result = LinkedHashMap<String, String>()
    uri.rawQuery?.split('&')?.filter { it.isNotEmpty() }?.forEach { part ->
        val name = part.substringBefore('=')
        val value = if ('=' in part) part.substringAfter('=') else ""
        result[decode(name)] = decode(value)
    }
    return result
}

private fun decode(value: String) = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)

private fun first(query: Map<String, String>, keys: List<String>): String? =
    keys.firstNotNullOfOrNull { key -> query[key]?.trim()?.takeIf { it.isNotEmpty() } }

/** Dart `_isBridgeUrl`: any ws(s) URL, or http(s) on the Bridge port or one of its paths. */
private fun isBridgeUrl(uri: URI): Boolean {
    if (uri.host.isNullOrBlank()) return false
    if (uri.scheme == "ws" || uri.scheme == "wss") return true
    if (uri.scheme != "http" && uri.scheme != "https") return false
    val path = uri.path.orEmpty().lowercase()
    return uri.port == DEFAULT_BRIDGE_PORT || "/codex" in path || "/health" in path || "/fs/list" in path
}

/**
 * Dart `_stripQuickConnectQuery`: the stored URL never keeps the token or
 * folder, and a health or listing URL points at `/codex`.
 */
private fun withoutQuickConnect(uri: URI, query: Map<String, String>): String {
    val kept = query.filterKeys { it !in TOKEN_KEYS && it !in CWD_KEYS }
    val path = uri.path.orEmpty().let { if (it.lowercase() == "/health" || it.lowercase() == "/fs/list") "/codex" else it }
    return buildString {
        append(uri.scheme).append("://")
        uri.rawUserInfo?.let { append(it).append('@') }
        append(uri.host)
        if (uri.port >= 0) append(':').append(uri.port)
        append(path)
        if (kept.isNotEmpty()) append('?').append(kept.entries.joinToString("&") { "${it.key}=${it.value}" })
        uri.rawFragment?.takeIf { it.isNotEmpty() }?.let { append('#').append(it) }
    }
}
