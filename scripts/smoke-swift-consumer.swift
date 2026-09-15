import Foundation
import NotiflyKMP

private final class PopupCompletion {
    private let lock = NSLock()
    private let semaphore = DispatchSemaphore(value: 0)
    private var callbackCount = 0
    private var output: PopupRenderOutput?

    func complete(_ value: PopupRenderOutput) {
        lock.lock()
        callbackCount += 1
        output = value
        lock.unlock()
        semaphore.signal()
    }

    func await() -> PopupRenderOutput {
        precondition(semaphore.wait(timeout: .now() + 5) == .success, "popup callback timed out")
        Thread.sleep(forTimeInterval: 0.1)
        lock.lock()
        defer { lock.unlock() }
        precondition(callbackCount == 1, "popup callback must run exactly once")
        return output!
    }
}

private func renderAndAwait(
    renderer: PopupRenderer,
    input: PopupRenderInput,
    afterRender: (PopupRenderTask) -> Void = { _ in }
) -> PopupRenderOutput {
    let completion = PopupCompletion()
    let task: PopupRenderTask = renderer.render(input: input, onComplete: completion.complete)
    afterRender(task)
    return completion.await()
}

precondition(UserIdTransitionPolicy.shared.evaluate(previousUserId: nil, newUserId: "A").shouldMerge)

let staticRenderer = PopupFactory.shared.create(
    config: PopupRendererConfig(projectId: "", baseUrl: "not-https", sdkVersion: "")
)
let staticOutput = renderAndAwait(
    renderer: staticRenderer,
    input: PopupRenderInput(
        templateRenderingMode: "static",
        campaignId: nil,
        notiflyUserId: nil,
        deviceId: nil,
        eventName: nil,
        eventParamsJson: nil
    )
)
precondition(staticOutput.outcome == "static")
precondition(staticOutput.html == nil && staticOutput.errorCode == nil && staticOutput.httpStatus == nil)
staticRenderer.close()

let closedOutput = renderAndAwait(
    renderer: staticRenderer,
    input: PopupRenderInput(
        templateRenderingMode: "static",
        campaignId: nil,
        notiflyUserId: nil,
        deviceId: nil,
        eventName: nil,
        eventParamsJson: nil
    )
)
precondition(closedOutput.outcome == "failed" && closedOutput.errorCode == "renderer_closed")

let cancellableRenderer = PopupFactory.shared.create(
    config: PopupRendererConfig(projectId: "", baseUrl: "not-https", sdkVersion: "")
)
let cancelledOrCompleted = renderAndAwait(
    renderer: cancellableRenderer,
    input: PopupRenderInput(
        templateRenderingMode: "ssr",
        campaignId: "campaign",
        notiflyUserId: "user",
        deviceId: "device",
        eventName: "open",
        eventParamsJson: "{}"
    ),
    afterRender: { task in
        task.cancel()
        task.cancel()
    }
)
precondition(
    cancelledOrCompleted.outcome == "cancelled" ||
        (cancelledOrCompleted.outcome == "failed" &&
            cancelledOrCompleted.errorCode == "invalid_configuration")
)
cancellableRenderer.close()
