package tech.notifly.kmp.popup.domain.usecase

import kotlinx.coroutines.test.runTest
import tech.notifly.kmp.popup.domain.model.PopupRenderRequest
import tech.notifly.kmp.popup.domain.model.PopupRenderResult
import tech.notifly.kmp.popup.domain.repository.PopupRenderRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderPopupUseCaseTest {
    private val project = "0123456789abcdef0123456789abcdef"
    private val input = PopupRenderRequest("ssr", "campaign", "user", "device", "open", emptyMap())

    @Test
    fun nonSsrBypassesAllValidationAndRepository() =
        runTest {
            val useCase = RenderPopupUseCase("bad", "\r\n", PopupRenderRepository { error("must not call") })
            for (mode in listOf(null, "static", "SSR", " ssr", "unknown")) {
                assertEquals(
                    PopupRenderResult.Static,
                    useCase.render(PopupRenderRequest(mode, null, null, null, null, mapOf("bad" to Any()))),
                )
            }
        }

    @Test
    fun validatesConfigurationAndHeaderInjection() =
        runTest {
            for ((id, version) in listOf(
                "A".repeat(32) to "sdk",
                project.drop(1) to "sdk",
                project to " ",
                project to "a".repeat(65),
                project to "sdk\r\n",
                project to "sdk\nother",
            )) {
                val useCase = RenderPopupUseCase(id, version, PopupRenderRepository { error("must not call") })
                assertEquals(PopupRenderResult.Failed("invalid_configuration"), useCase.render(input))
            }
        }

    @Test
    fun validatesRequiredFieldsAndUtf16Lengths() =
        runTest {
            val useCase = RenderPopupUseCase(project, "sdk", PopupRenderRepository { PopupRenderResult.Skipped })
            val invalid =
                listOf(
                    input.copy(campaignId = null),
                    input.copy(campaignId = "x".repeat(1025)),
                    input.copy(notiflyUserId = "\uFEFF\u00A0"),
                    input.copy(notiflyUserId = "😀".repeat(128)),
                    input.copy(deviceId = "x".repeat(256)),
                    input.copy(deviceId = null),
                    input.copy(eventName = null),
                    input.copy(eventName = ""),
                    input.copy(eventName = "\uFEFF\u00A0"),
                    input.copy(eventName = "😀".repeat(128)),
                    input.copy(eventName = "x".repeat(256)),
                )
            for (request in invalid) assertEquals(PopupRenderResult.Failed("invalid_request"), useCase.render(request))
            for (request in listOf(
                input.copy(notiflyUserId = "😀".repeat(127) + "x"),
                input.copy(campaignId = "x".repeat(1024)),
                input.copy(deviceId = "x".repeat(255)),
                input.copy(eventName = "x".repeat(255)),
            )) {
                assertEquals(PopupRenderResult.Skipped, useCase.render(request))
            }
        }

    @Test
    fun render_identifiersAtLengthLimits_passesNormalizedValuesToRepository() =
        runTest {
            val user = "u".repeat(255)
            val device = "d".repeat(255)
            val campaign = "c".repeat(1024)
            val event = "e".repeat(255)
            val useCase =
                RenderPopupUseCase(
                    project,
                    "sdk",
                    PopupRenderRepository { request ->
                        assertEquals(user, request.notiflyUserId)
                        assertEquals(device, request.deviceId)
                        assertEquals(campaign, request.campaignId)
                        assertEquals(event, request.eventName)
                        PopupRenderResult.Skipped
                    },
                )

            val result =
                useCase.render(
                    input.copy(
                        notiflyUserId = " $user ",
                        deviceId = " $device ",
                        campaignId = " $campaign ",
                        eventName = event,
                    ),
                )

            assertEquals(PopupRenderResult.Skipped, result)
        }

    @Test
    fun render_sdkVersionAtLengthLimit_passesTrimmedHeaderToRepository() =
        runTest {
            val version = "v".repeat(64)
            val useCase =
                RenderPopupUseCase(
                    project,
                    "\uFEFF $version \u00A0",
                    PopupRenderRepository { request ->
                        assertEquals(version, request.sdkVersion)
                        PopupRenderResult.Skipped
                    },
                )

            val result = useCase.render(input)

            assertEquals(PopupRenderResult.Skipped, result)
        }

    @Test
    fun render_paddedIdentifiers_normalizesIdsAndPreservesEvent() =
        runTest {
            val params = mapOf("big" to 9007199254740993L)
            val useCase =
                RenderPopupUseCase(
                    project,
                    "\uFEFF sdk \u00A0",
                    PopupRenderRepository { request ->
                        assertEquals(project, request.projectId)
                        assertEquals("sdk", request.sdkVersion)
                        assertEquals("UJ~|~journey~|~node~|~session", request.campaignId)
                        assertEquals("user", request.notiflyUserId)
                        assertEquals("device", request.deviceId)
                        assertEquals(" open ", request.eventName)
                        assertEquals(params, request.eventParams)
                        PopupRenderResult.Rendered("original")
                    },
                )
            assertEquals(
                PopupRenderResult.Rendered("original"),
                useCase.render(
                    input.copy(
                        campaignId = "\uFEFF UJ~|~journey~|~node~|~session\u00A0",
                        notiflyUserId = "\u2000user\u2029",
                        deviceId = " device ",
                        eventName = " open ",
                        eventParams = params,
                    ),
                ),
            )
        }

    @Test
    fun doesNotTrimCharactersOutsideEcmaWhitespace() =
        runTest {
            val useCase =
                RenderPopupUseCase(
                    project,
                    "sdk",
                    PopupRenderRepository {
                        assertEquals("\u0085user\u001C", it.notiflyUserId)
                        PopupRenderResult.Skipped
                    },
                )
            assertEquals(PopupRenderResult.Skipped, useCase.render(input.copy(notiflyUserId = "\u0085user\u001C")))
        }

    @Test
    fun permitsDotDeviceIdsAndOtherDotContainingPathIds() =
        runTest {
            for (id in listOf("a.b", "...", "a/..", "%2E")) {
                for (device in listOf(".", "..")) {
                    val useCase =
                        RenderPopupUseCase(
                            project,
                            "sdk",
                            PopupRenderRepository {
                                assertEquals(id, it.campaignId)
                                assertEquals(id, it.notiflyUserId)
                                assertEquals(device, it.deviceId)
                                PopupRenderResult.Skipped
                            },
                        )
                    assertEquals(
                        PopupRenderResult.Skipped,
                        useCase.render(input.copy(campaignId = id, notiflyUserId = id, deviceId = device)),
                    )
                }
            }
        }
}
