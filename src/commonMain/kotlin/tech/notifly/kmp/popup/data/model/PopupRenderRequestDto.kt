package tech.notifly.kmp.popup.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest

internal sealed class PopupRequestEncoding {
    data class Body(
        val json: String,
    ) : PopupRequestEncoding()

    data class Invalid(
        val errorCode: String,
    ) : PopupRequestEncoding()
}

internal object PopupRenderRequestDto {
    /** Encodes supported values without rounding integer values through a floating-point conversion. */
    fun encode(request: ValidatedPopupRenderRequest): PopupRequestEncoding {
        val params =
            try {
                encodeValue(request.eventParams ?: emptyMap<String, Any?>(), mutableListOf())
            } catch (error: Exception) {
                return PopupRequestEncoding.Invalid("invalid_request")
            }
        val body =
            buildJsonObject {
                put("deviceId", request.deviceId)
                put("eventName", request.eventName)
                put("eventParams", params)
            }.toString()
        return PopupRequestEncoding.Body(body)
    }

    private fun encodeValue(
        value: Any?,
        ancestors: MutableList<Any>,
    ): JsonElement =
        when (value) {
            null -> {
                JsonNull
            }

            is String -> {
                JsonPrimitive(value)
            }

            is Boolean -> {
                JsonPrimitive(value)
            }

            is Number -> {
                require(value.toDouble().isFinite())
                JsonPrimitive(value)
            }

            is Map<*, *> -> {
                encodeCollection(value, ancestors) {
                    JsonObject(
                        value.entries.associate { (key, item) ->
                            require(key is String)
                            key to encodeValue(item, ancestors)
                        },
                    )
                }
            }

            is List<*> -> {
                encodeCollection(value, ancestors) {
                    JsonArray(value.map { encodeValue(it, ancestors) })
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported event parameter value")
            }
        }

    /** Tracks only ancestors so shared, non-circular collections can appear more than once. */
    private inline fun encodeCollection(
        value: Any,
        ancestors: MutableList<Any>,
        encode: () -> JsonElement,
    ): JsonElement {
        require(ancestors.none { it === value })
        ancestors.add(value)
        return try {
            encode()
        } finally {
            ancestors.removeAt(ancestors.lastIndex)
        }
    }
}
