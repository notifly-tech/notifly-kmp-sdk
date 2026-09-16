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
 */
@JsExport
class PopupRendererConfig(val projectId: String, val baseUrl: String, val sdkVersion: String)
