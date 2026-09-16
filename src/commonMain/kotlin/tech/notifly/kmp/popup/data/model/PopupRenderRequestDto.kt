package tech.notifly.kmp.popup.data.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tech.notifly.kmp.core.util.toJsonElement
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
    /** Builds the popup request body, translating unsupported event parameters into an input error. */
    fun encode(request: ValidatedPopupRenderRequest): PopupRequestEncoding {
        val params =
            try {
                (request.eventParams ?: emptyMap<String, Any?>()).toJsonElement()
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
}
