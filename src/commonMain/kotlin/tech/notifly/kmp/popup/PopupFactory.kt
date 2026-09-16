@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tech.notifly.kmp.core.networking.sharedHttpClient
import tech.notifly.kmp.popup.data.repository.PopupRenderRepositoryImpl
import tech.notifly.kmp.popup.domain.usecase.RenderPopupUseCase
import tech.notifly.kmp.popup.model.PopupRendererConfig
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/** Creates popup renderers for the host SDK. */
@JsExport
object PopupFactory {
    /**
     * Creates an independent renderer using [config] and the platform HTTP transport.
     *
     * Renderers share a lazily initialized HTTP client and require no explicit cleanup.
     * Configuration is validated when rendering an SSR popup, not during construction.
     *
     * These examples use a host-provided [PopupRendererConfig]. JavaScript's `sdk` is the imported KMP module.
     *
     * Android / Kotlin:
     * ```kotlin
     * val renderer = PopupFactory.create(config)
     * ```
     *
     * iOS / Swift (after `import NotiflyKMP`):
     * ```swift
     * let renderer = PopupFactory.shared.create(config: config)
     * ```
     *
     * JavaScript:
     * ```javascript
     * const popup = sdk.tech.notifly.kmp.popup;
     * const renderer = popup.PopupFactory.create(config);
     * ```
     */
    fun create(config: PopupRendererConfig): PopupRenderer =
        createPopupRenderer(config, { sharedHttpClient }, Dispatchers.Default)
}

/** Borrows a client from [clientProvider] only when a validated request needs the transport. */
internal fun createPopupRenderer(
    config: PopupRendererConfig,
    clientProvider: () -> HttpClient,
    dispatcher: CoroutineDispatcher,
): PopupRenderer {
    val repository = PopupRenderRepositoryImpl(config.baseUrl, clientProvider)
    return PopupRenderer(RenderPopupUseCase(config.projectId, config.sdkVersion, repository), dispatcher)
}
