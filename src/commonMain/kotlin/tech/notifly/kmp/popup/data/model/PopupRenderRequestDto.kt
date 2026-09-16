package tech.notifly.kmp.popup.data.model

import kotlinx.serialization.json.*
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest

internal sealed class PopupRequestEncoding {
    data class Body(val json: String) : PopupRequestEncoding()
    data class Invalid(val errorCode: String) : PopupRequestEncoding()
}

internal object PopupRenderRequestDto {
    private val number = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun encode(request: ValidatedPopupRenderRequest): PopupRequestEncoding {
        val raw = request.eventParamsJson ?: "{}"
        if (!hasValidPrimitiveTokens(raw)) return PopupRequestEncoding.Invalid("invalid_request")
        val params = try { Json.parseToJsonElement(raw) as? JsonObject } catch (error: Exception) { null }
            ?: return PopupRequestEncoding.Invalid("invalid_request")
        val body = buildJsonObject {
            put("deviceId", request.deviceId)
            put("eventName", request.eventName)
            put("eventParams", params)
        }.toString()
        return PopupRequestEncoding.Body(body)
    }

    // Validate every original token before JsonObject can overwrite duplicate keys.
    // The serialization parser still owns JSON structure, keys, and escape syntax.
    private fun hasValidPrimitiveTokens(raw: String): Boolean {
        var inString = false
        var escaped = false
        var tokenStart = -1
        for ((index, char) in raw.withIndex()) {
            if (inString) {
                if (char < ' ') return false
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else if (char == '"' || char in "{}[]:, \t\r\n") {
                if (tokenStart >= 0) {
                    if (!isValidPrimitive(raw.substring(tokenStart, index))) return false
                    tokenStart = -1
                }
                if (char == '"') inString = true
            } else if (tokenStart < 0) {
                tokenStart = index
            }
        }
        return tokenStart < 0 || isValidPrimitive(raw.substring(tokenStart))
    }

    private fun isValidPrimitive(token: String): Boolean =
        token == "true" || token == "false" || token == "null" || number.matches(token)
}
