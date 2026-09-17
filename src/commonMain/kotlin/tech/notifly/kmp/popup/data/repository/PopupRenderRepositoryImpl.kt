package tech.notifly.kmp.popup.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import tech.notifly.kmp.core.networking.encodePathSegment
import tech.notifly.kmp.core.networking.networkErrorCode
import tech.notifly.kmp.core.networking.parseHttpsOrigin
import tech.notifly.kmp.core.util.isJsBlank
import tech.notifly.kmp.popup.data.mapper.popupRenderErrorCode
import tech.notifly.kmp.popup.data.model.PopupRenderRequestDto
import tech.notifly.kmp.popup.data.model.PopupRequestEncoding
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository

internal class PopupRenderRepositoryImpl(
    private val baseUrl: String,
    private val client: () -> HttpClient,
) : PopupRenderRepository {
    override suspend fun render(request: ValidatedPopupRenderRequest): PopupRenderResult {
        val origin = parseHttpsOrigin(baseUrl) ?: return PopupRenderResult.Failed("invalid_configuration")
        val encoded = PopupRenderRequestDto.encode(request)
        if (encoded is PopupRequestEncoding.Invalid) return PopupRenderResult.Failed(encoded.errorCode)
        val body = (encoded as PopupRequestEncoding.Body).json
        return try {
            val endpoint =
                URLBuilder(origin)
                    .apply {
                        encodedPath =
                            "/projects/${encodePathSegment(request.projectId)}" +
                            "/users/${encodePathSegment(request.notiflyUserId)}" +
                            "/popup-pages/${encodePathSegment(request.campaignId)}"
                    }.build()
            client()
                .preparePost(endpoint) {
                    header("X-Notifly-SDK-Version", request.sdkVersion)
                    accept(ContentType.Text.Html)
                    setBody(TextContent(body, ContentType.Application.Json))
                }.execute { response ->
                    val status = response.status.value
                    when (status) {
                        200 -> {
                            val html = response.bodyAsText()
                            val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
                            if (!type.equals("text/html", ignoreCase = true) ||
                                html.isJsBlank()
                            ) {
                                PopupRenderResult.Failed("invalid_response", 200)
                            } else {
                                PopupRenderResult.Rendered(html)
                            }
                        }

                        204 -> {
                            PopupRenderResult.Skipped
                        }

                        else -> {
                            PopupRenderResult.Failed(popupRenderErrorCode(status), status)
                        }
                    }
                }
        } catch (error: Exception) {
            PopupRenderResult.Failed(networkErrorCode(error))
        }
    }
}
