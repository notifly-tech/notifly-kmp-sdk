package tech.notifly.kmp.popup.data.model

import kotlinx.serialization.json.*
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest

internal sealed class PopupRequestEncoding {
    data class Body(val json: String) : PopupRequestEncoding()
    data class Invalid(val errorCode: String) : PopupRequestEncoding()
}

internal object PopupRenderRequestDto {
    private const val MAX_BYTES = 262144
    private val number = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun encode(request: ValidatedPopupRenderRequest): PopupRequestEncoding {
        val raw = request.eventParamsJson ?: "{}"
        if (raw.encodeToByteArray().size > MAX_BYTES) return PopupRequestEncoding.Invalid("payload_too_large")
        var inString = false
        var escaped = false
        for (char in raw) {
            if (inString && char < ' ') return PopupRequestEncoding.Invalid("invalid_request")
            if (escaped) escaped = false
            else if (inString && char == '\\') escaped = true
            else if (char == '"') inString = !inString
        }
        val params = try { Json.parseToJsonElement(raw) as? JsonObject } catch (error: Exception) { null }
            ?: return PopupRequestEncoding.Invalid("invalid_request")
        // The 1.5 parser preserves number literals, but also accepts invalid unquoted primitives.
        val pending = ArrayDeque<JsonElement>()
        pending.add(params)
        while (pending.isNotEmpty()) {
            when (val value = pending.removeLast()) {
                is JsonObject -> pending.addAll(value.values)
                is JsonArray -> pending.addAll(value)
                is JsonPrimitive -> if (!value.isString && value != JsonNull && value.content != "true" && value.content != "false" && !number.matches(value.content)) return PopupRequestEncoding.Invalid("invalid_request")
            }
        }
        val body = buildJsonObject {
            put("deviceId", request.deviceId)
            put("eventName", request.eventName)
            put("eventParams", params)
        }.toString()
        if (body.encodeToByteArray().size > MAX_BYTES) return PopupRequestEncoding.Invalid("payload_too_large")
        return PopupRequestEncoding.Body(body)
    }
}
