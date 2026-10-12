package cn.com.omnimind.nativeui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Minimal Dart `jsonDecode`: objects -> Map, arrays -> List, numbers -> Int/Long/Double. */
internal object JsonValues {
    fun decode(text: String): Any? = toValue(Json.parseToJsonElement(text))

    /** [text] as an object; throws when it is malformed or another JSON type. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        decode(text) as? Map<String, Any?> ?: throw IllegalArgumentException("Not a JSON object")

    fun parseArray(text: String): List<Any?> =
        decode(text) as? List<Any?> ?: throw IllegalArgumentException("Not a JSON array")

    private val numberPattern = Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\z")

    private fun toValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> LinkedHashMap<String, Any?>().apply {
            element.forEach { (key, value) -> put(key, toValue(value)) }
        }
        is JsonArray -> element.map(::toValue)
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" -> true
            element.content == "false" -> false
            else -> {
                val literal = element.content
                require(numberPattern.containsMatchIn(literal)) { "Invalid JSON literal: $literal" }
                if (literal.any { it == '.' || it == 'e' || it == 'E' }) {
                    literal.toDouble()
                } else {
                    literal.toIntOrNull() ?: literal.toLongOrNull() ?: literal.toDouble()
                }
            }
        }
    }
}
