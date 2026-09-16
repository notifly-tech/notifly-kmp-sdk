import tech.notifly.kmp.identity.UserIdTransitionPolicy
import tech.notifly.kmp.popup.PopupFactory
import tech.notifly.kmp.popup.PopupRenderTask
import tech.notifly.kmp.popup.model.PopupRenderInput
import tech.notifly.kmp.popup.model.PopupRenderOutput
import tech.notifly.kmp.popup.model.PopupRendererConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private fun renderAndAwait(
    renderer: tech.notifly.kmp.popup.PopupRenderer,
    input: PopupRenderInput,
    afterRender: (PopupRenderTask) -> Unit = {},
): PopupRenderOutput {
    val callbackCount = AtomicInteger()
    val output = AtomicReference<PopupRenderOutput>()
    val completed = CountDownLatch(1)
    val task: PopupRenderTask =
        renderer.render(input) {
            output.set(it)
            callbackCount.incrementAndGet()
            completed.countDown()
        }
    afterRender(task)
    check(completed.await(5, TimeUnit.SECONDS)) { "popup callback timed out" }
    Thread.sleep(100)
    check(callbackCount.get() == 1) { "popup callback must run exactly once" }
    return checkNotNull(output.get())
}

fun main() {
    check(UserIdTransitionPolicy.evaluate(null, "A").shouldMerge)

    val staticRenderer = PopupFactory.create(PopupRendererConfig("", "not-https", ""))
    val static =
        renderAndAwait(
            staticRenderer,
            PopupRenderInput("static", null, null, null, null, null),
        )
    check(static.outcome == "static")
    check(static.html == null && static.errorCode == null && static.httpStatus == null)

    val reused =
        renderAndAwait(
            staticRenderer,
            PopupRenderInput("static", null, null, null, null, null),
        )
    check(reused.outcome == "static")

    val cancellableRenderer = PopupFactory.create(PopupRendererConfig("", "not-https", ""))
    val cancelledOrCompleted =
        renderAndAwait(
            cancellableRenderer,
            PopupRenderInput("ssr", "campaign", "user", "device", "open", emptyMap()),
        ) { task ->
            task.cancel()
            task.cancel()
        }
    check(
        cancelledOrCompleted.outcome == "cancelled" ||
            (cancelledOrCompleted.outcome == "failed" && cancelledOrCompleted.errorCode == "invalid_configuration"),
    )
}
