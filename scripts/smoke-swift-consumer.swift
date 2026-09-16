import Foundation
import NotiflyKMP
import ObjectiveC.runtime

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
        eventParams: nil
    )
)
precondition(staticOutput.outcome == "static")
precondition(staticOutput.html == nil && staticOutput.errorCode == nil && staticOutput.httpStatus == nil)

let reusedOutput = renderAndAwait(
    renderer: staticRenderer,
    input: PopupRenderInput(
        templateRenderingMode: "static",
        campaignId: nil,
        notiflyUserId: nil,
        deviceId: nil,
        eventName: nil,
        eventParams: nil
    )
)
precondition(reusedOutput.outcome == "static")

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
        eventParams: [:]
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

/// Captures requests locally so the consumer smoke never contacts a rendering service.
private final class PopupFixtureProtocol: URLProtocol {
    static var bodies: [Data] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        precondition(request.url?.host == "render.example")
        var body = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 4096)
            while true {
                let count = stream.read(&buffer, maxLength: buffer.count)
                precondition(count >= 0, "could not read request body")
                if count == 0 { break }
                body.append(contentsOf: buffer.prefix(count))
            }
        }
        Self.bodies.append(body)
        let response = HTTPURLResponse(
            url: request.url!, statusCode: 200, httpVersion: nil,
            headerFields: ["Content-Type": "text/html"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("<html>rendered</html>".utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private extension URLSessionConfiguration {
    /// Calls the original getter after the smoke exchanges implementations, then installs its local protocol.
    @objc class func popupFixtureConfiguration() -> URLSessionConfiguration {
        let configuration = popupFixtureConfiguration()
        configuration.protocolClasses = [PopupFixtureProtocol.self]
        return configuration
    }
}

let defaultConfiguration = class_getClassMethod(
    URLSessionConfiguration.self, #selector(getter: URLSessionConfiguration.default)
)!
let fixtureConfiguration = class_getClassMethod(
    URLSessionConfiguration.self, #selector(URLSessionConfiguration.popupFixtureConfiguration)
)!
method_exchangeImplementations(defaultConfiguration, fixtureConfiguration)
defer { method_exchangeImplementations(defaultConfiguration, fixtureConfiguration) }
let renderer = PopupFactory.shared.create(config: PopupRendererConfig(
    projectId: "0123456789abcdef0123456789abcdef",
    baseUrl: "https://render.example",
    sdkVersion: "notifly/ios/test"
))
let params: [String: Any] = [
    "items": [["id": "P1", "quantity": 2]],
    "tags": ["sale", "new"],
    "enabled": true,
    "nullable": NSNull(),
    "price": 1.25,
    "big": Int64(9007199254740993),
]
let rendered = renderAndAwait(renderer: renderer, input: PopupRenderInput(
    templateRenderingMode: "ssr", campaignId: "campaign", notiflyUserId: "user",
    deviceId: "device", eventName: "purchase", eventParams: params
))
precondition(
    rendered.outcome == "rendered" && rendered.html == "<html>rendered</html>",
    "unexpected result: \(rendered.outcome), error: \(String(describing: rendered.errorCode)), captured: \(PopupFixtureProtocol.bodies.count)"
)
precondition(PopupFixtureProtocol.bodies.count == 1)
let body = try JSONSerialization.jsonObject(with: PopupFixtureProtocol.bodies[0]) as! [String: Any]
precondition(body["deviceId"] as? String == "device")
precondition(body["eventName"] as? String == "purchase")
precondition((body["eventParams"] as! NSDictionary).isEqual(to: params))

let invalid = renderAndAwait(renderer: renderer, input: PopupRenderInput(
    templateRenderingMode: "ssr", campaignId: "campaign", notiflyUserId: "user",
    deviceId: "device", eventName: "purchase", eventParams: ["unsupported": Date()]
))
precondition(invalid.errorCode == "invalid_request")
precondition(PopupFixtureProtocol.bodies.count == 1)
