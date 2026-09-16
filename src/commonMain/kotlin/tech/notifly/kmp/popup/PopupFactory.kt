@file:OptIn(ExperimentalJsExport::class)
package tech.notifly.kmp.popup

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import tech.notifly.kmp.core.networking.createHttpClient
import tech.notifly.kmp.popup.data.repository.PopupRenderRepositoryImpl
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.internal.PlatformLock
import tech.notifly.kmp.popup.model.PopupRendererConfig

@JsExport
object PopupFactory {
    fun create(config: PopupRendererConfig): PopupRenderer {
        return createPopupRenderer(config, { createHttpClient() }, Dispatchers.Default)
    }
}

internal fun createPopupRenderer(config: PopupRendererConfig, clientFactory: () -> HttpClient, dispatcher: CoroutineDispatcher): PopupRenderer {
    val lock = PlatformLock()
    var client: HttpClient? = null
    var closed = false
    val repository = PopupRenderRepositoryImpl(config.baseUrl) {
        lock.withLock {
            check(!closed)
            client ?: clientFactory().also { client = it }
        }
    }
    return PopupRenderer(RenderPopupUseCase(config.projectId, config.sdkVersion, repository), {
        val owned = lock.withLock {
            closed = true
            client.also { client = null }
        }
        owned?.close()
    }, dispatcher)
}
