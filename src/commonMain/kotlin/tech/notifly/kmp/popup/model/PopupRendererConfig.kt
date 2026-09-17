@file:OptIn(ExperimentalJsExport::class)

package tech.notifly.kmp.popup.model

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Host-provided configuration for popup rendering.
 *
 * [projectId] must be 32 lowercase hexadecimal characters. [baseUrl] is an HTTPS origin on port 443,
 * with no credentials, query, fragment, or path other than an optional trailing slash.
 * [sdkVersion] is the full `X-Notifly-SDK-Version` header value, such as `notifly/js/2.21.0`;
 * it must contain no CR or LF and must be 1 to 64 characters after ECMAScript whitespace trimming.
 * Invalid configuration produces `invalid_configuration` when rendering an SSR popup.
 *
 * These examples reuse the host SDK's project ID, rendering origin, and full SDK version header;
 * the KMP module does not choose an environment. JavaScript's `sdk` is the imported KMP module.
 *
 * Android / Kotlin:
 * ```kotlin
 * val config = PopupRendererConfig(
 *     projectId = projectId,
 *     baseUrl = renderingBaseUrl,
 *     sdkVersion = sdkVersion,
 * )
 * ```
 *
 * iOS / Swift (after `import NotiflyKMP`):
 * ```swift
 * let config = PopupRendererConfig(
 *     projectId: projectId,
 *     baseUrl: renderingBaseUrl,
 *     sdkVersion: sdkVersion
 * )
 * ```
 *
 * JavaScript:
 * ```javascript
 * const config = new sdk.tech.notifly.kmp.popup.model.PopupRendererConfig(
 *   projectId, renderingBaseUrl, sdkVersion,
 * );
 * ```
 */
@JsExport
class PopupRendererConfig(
    val projectId: String,
    val baseUrl: String,
    val sdkVersion: String,
)
