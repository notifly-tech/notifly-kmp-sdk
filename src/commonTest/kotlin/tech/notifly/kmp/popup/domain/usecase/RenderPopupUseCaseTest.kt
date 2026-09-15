package tech.notifly.kmp.popup.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import tech.notifly.kmp.popup.domain.model.*
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository

class RenderPopupUseCaseTest {
    private val project = "0123456789abcdef0123456789abcdef"
    private val input = PopupRenderRequest("ssr", "campaign", "user", "device", "open", "{}")

    @Test fun nonSsrBypassesAllValidationAndRepository() = runTest {
        val useCase = RenderPopupUseCase("bad", "\r\n", PopupRenderRepository { error("must not call") })
        for (mode in listOf(null, "static", "SSR", " ssr", "unknown")) {
            assertEquals(PopupRenderResult.Static, useCase.render(PopupRenderRequest(mode, null, null, null, null, "invalid")))
        }
    }

    @Test fun validatesConfigurationAndHeaderInjection() = runTest {
        for ((id, version) in listOf("A".repeat(32) to "sdk", project.drop(1) to "sdk", project to " ", project to "a".repeat(65), project to "sdk\r\n", project to "sdk\nother")) {
            val useCase = RenderPopupUseCase(id, version, PopupRenderRepository { error("must not call") })
            assertEquals(PopupRenderResult.Failed("invalid_configuration"), useCase.render(input))
        }
    }

    @Test fun validatesRequiredFieldsAndUtf16Lengths() = runTest {
        val useCase = RenderPopupUseCase(project, "sdk", PopupRenderRepository { PopupRenderResult.Skipped })
        val invalid = listOf(input.copy(campaignId = null), input.copy(campaignId = "x".repeat(1025)), input.copy(notiflyUserId = "\uFEFF\u00A0"), input.copy(notiflyUserId = "😀".repeat(128)), input.copy(deviceId = "x".repeat(256)), input.copy(deviceId = null), input.copy(eventName = null), input.copy(eventName = ""), input.copy(eventName = "\uFEFF\u00A0"), input.copy(eventName = "😀".repeat(128)), input.copy(eventName = "x".repeat(256)))
        for (request in invalid) assertEquals(PopupRenderResult.Failed("invalid_request"), useCase.render(request))
        for (request in listOf(input.copy(notiflyUserId = "😀".repeat(127) + "x"), input.copy(campaignId = "x".repeat(1024)), input.copy(deviceId = "x".repeat(255)), input.copy(eventName = "x".repeat(255)))) assertEquals(PopupRenderResult.Skipped, useCase.render(request))
    }

    @Test fun normalizesJsWhitespaceIdsButPreservesEventNameAndJson() = runTest {
        val json = " {\"big\":9007199254740993} "
        val useCase = RenderPopupUseCase(project, "\uFEFF sdk \u00A0", PopupRenderRepository { request ->
            assertEquals(project, request.projectId)
            assertEquals("sdk", request.sdkVersion)
            assertEquals("UJ~|~journey~|~node~|~session", request.campaignId)
            assertEquals("user", request.notiflyUserId)
            assertEquals("device", request.deviceId)
            assertEquals(" open ", request.eventName)
            assertEquals(json, request.eventParamsJson)
            PopupRenderResult.Rendered("original")
        })
        assertEquals(PopupRenderResult.Rendered("original"), useCase.render(input.copy(campaignId = "\uFEFF UJ~|~journey~|~node~|~session\u00A0", notiflyUserId = "\u2000user\u2029", deviceId = " device ", eventName = " open ", eventParamsJson = json)))
    }

    @Test fun doesNotTrimCharactersOutsideEcmaWhitespace() = runTest {
        val useCase = RenderPopupUseCase(project, "sdk", PopupRenderRepository {
            assertEquals("\u0085user\u001C", it.notiflyUserId)
            PopupRenderResult.Skipped
        })
        assertEquals(PopupRenderResult.Skipped, useCase.render(input.copy(notiflyUserId = "\u0085user\u001C")))
    }
}
