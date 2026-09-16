package tech.notifly.kmp.popup.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.CancellationException
import tech.notifly.kmp.popup.data.model.PopupRenderRequestDto
import tech.notifly.kmp.popup.data.model.PopupRequestEncoding
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.model.ValidatedPopupRenderRequest
import tech.notifly.kmp.popup.domain.model.trimJsWhitespace
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository

internal class PopupRenderRepositoryImpl(
    private val baseUrl: String,
    private val client: () -> HttpClient,
) : PopupRenderRepository {
    override suspend fun render(request: ValidatedPopupRenderRequest): PopupRenderResult {
        val origin = parseOrigin() ?: return PopupRenderResult.Failed("invalid_configuration")
        val encoded = PopupRenderRequestDto.encode(request)
        if (encoded is PopupRequestEncoding.Invalid) return PopupRenderResult.Failed(encoded.errorCode)
        val body = (encoded as PopupRequestEncoding.Body).json
        return try {
            val endpoint =
                URLBuilder(origin)
                    .apply {
                        encodedPath =
                            "/projects/${segment(
                                request.projectId,
                            )}/users/${segment(request.notiflyUserId)}/popup-pages/${segment(request.campaignId)}"
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
                                html.trimJsWhitespace().isEmpty()
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
                            PopupRenderResult.Failed(
                                when (status) {
                                    400 -> "invalid_request"
                                    404 -> "popup_not_found"
                                    408 -> "request_timeout"
                                    413 -> "payload_too_large"
                                    422 -> "invalid_popup_template"
                                    500 -> "internal_server_error"
                                    504 -> "popup_render_timeout"
                                    else -> "unexpected_http_status"
                                },
                                status,
                            )
                        }
                    }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpRequestTimeoutException) {
            PopupRenderResult.Failed("client_timeout")
        } catch (error: ConnectTimeoutException) {
            PopupRenderResult.Failed("client_timeout")
        } catch (error: SocketTimeoutException) {
            PopupRenderResult.Failed("client_timeout")
        } catch (error: Exception) {
            PopupRenderResult.Failed("network_error")
        }
    }

    /** Validates the injected HTTPS origin before constructing an endpoint or obtaining a client. */
    private fun parseOrigin(): Url? {
        if (!baseUrl.startsWith("https://", ignoreCase = true) ||
            baseUrl.any { it <= ' ' || it == '\\' || it == '@' || it == '?' || it == '#' }
        ) {
            return null
        }
        if (baseUrl.substringAfter("://").substringBefore('/').isEmpty()) return null
        return try {
            Url(baseUrl).takeIf {
                it.protocol == URLProtocol.HTTPS && it.host.isNotEmpty() && it.port == 443 &&
                    it.user == null && it.password == null && (it.encodedPath.isEmpty() || it.encodedPath == "/")
            }
        } catch (error: Exception) {
            null
        }
    }

    /** Encodes an original identifier as one UTF-8 path segment without interpreting existing escapes. */
    private fun segment(value: String): String =
        buildString {
            for (byte in value.encodeToByteArray()) {
                val code = byte.toInt() and 255
                val char = code.toChar()
                if (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~") {
                    append(char)
                } else {
                    append('%')
                    append("0123456789ABCDEF"[code ushr 4])
                    append("0123456789ABCDEF"[code and 15])
                }
            }
        }
}
