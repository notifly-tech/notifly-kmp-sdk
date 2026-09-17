# AGENTS.md

This file defines repository conventions for coding agents.

## Language

- All repository documentation, code comments, identifiers, and test names must be written in English. This includes this file, README files, KDoc, and assertion messages.
- Non-English text is allowed only when required as test data, such as Unicode, encoding, or localization fixtures. Preserve those fixtures instead of translating them.
- This rule applies to repository content, not the language of conversations with the user.

## Structure and architecture

### Module and source sets

- This repository contains one Kotlin Multiplatform library module. Architectural boundaries are packages, not separate Gradle modules.
- The package root is `tech.notifly.kmp`. Group code by feature, with shared infrastructure under `core`.
- Keep shared domain behavior in `src/commonMain/kotlin`. Reserve `jvmMain`, `iosMain`, `jsMain`, and other platform source sets for infrastructure required to run that shared implementation.
- The configured targets are JVM, JavaScript IR for Node.js and browsers, and iOS device/simulator targets. These source sets implement one library; they are not independent SDK applications.
- Use `expect`/`actual` only where platform capabilities differ, such as HTTP client creation and locking. Keep validation and rendering decisions in shared code.

The main package layout is:

```text
tech/notifly/kmp/
  core/
    concurrency/      Platform-independent locking contract and platform implementations
    networking/       HTTP client lifecycle, transport configuration, URL utilities, and transport error classification
    util/             Reusable string, JSON, and platform-specific JS value utilities
  identity/           Shared user ID transition decisions
  popup/
    PopupFactory.kt   Public construction entry point and dependency wiring
    PopupRenderer.kt  Public callback API and per-request lifecycle
    PopupRenderTask.kt Public cancellation handle
    model/            Public configuration, input, and output models
    domain/
      model/          Internal requests, validated requests, modes, and results
      repository/     Repository contracts required by use cases
      usecase/        Rendering decisions and shared request validation
    data/
      mapper/         Popup-specific HTTP status mapping
      model/          HTTP request DTOs and encoding
      repository/     Repository implementations and HTTP response mapping
```

This is a package overview across source sets. Platform infrastructure files keep the same package as their shared counterpart: the common `PlatformLock` declaration and all platform implementations belong in `core/concurrency`.

### Platform source-set boundary

- Platform source sets are infrastructure adapters, not separate implementations of each feature. Keep them small and independent of domain concepts wherever possible.
- Put shared business rules, domain validation, use cases, and feature orchestration in `commonMain`. Put platform-specific product behavior, UI integration, and host SDK lifecycle decisions in the corresponding Android, iOS, or JavaScript SDK repository, not in KMP platform source sets.
- Limit platform code to capabilities that genuinely require platform APIs, such as HTTP engines, synchronization primitives, and generic native value conversion. Place these capabilities under the appropriate `core` package, even when only one feature currently uses them.
- Do not grow feature packages, domain-specific factories, use cases, or repository implementations in `jvmMain`, `iosMain`, or `jsMain`. A feature needing a platform capability should use shared logic backed by a narrow infrastructure contract; platform-specific feature decisions belong in the host SDK.
- Keep unavoidable language/export bridges mechanical and minimal: adapt values and delegate without owning business decisions. Existing compatibility bridges such as `PopupRenderInputJs` are not a precedent for adding domain logic to platform source sets; evaluate shared code or host SDK ownership before extending them.
- Before adding platform code, identify whether it is shared domain logic, host SDK behavior, or genuinely platform-dependent infrastructure. Do not hide domain dependencies by merely moving feature code into `core` or renaming it as a utility.

### Layer responsibilities and dependencies

- Public entry points live directly under the feature package, and public boundary models live in its `model` package. Keep use cases, repository contracts, implementations, DTOs, and utilities `internal` unless a host SDK genuinely needs them.
- `PopupFactory` is the composition root: it connects `PopupRenderer`, `RenderPopupUseCase`, and `PopupRenderRepositoryImpl`, supplying the client provider and dispatcher. Host SDKs should not assemble these internal dependencies themselves.
- `PopupRenderer` adapts public input/output and coordinates completion and cancellation. Rendering rules belong in `RenderPopupUseCase`, not in the facade.
- `domain` depends on its own models and repository interfaces, and may use pure shared utilities. It must not depend on `data`, Ktor, platform engines, or public facade models.
- `data` implements the domain repository contract. API requests belong in the repository implementation; serialization belongs in its DTOs. Keep endpoint construction, origin-validation failure handling, successful response validation, and result construction in the repository. Popup-specific HTTP status mapping belongs in `data/mapper`; reusable URL validation and transport exception classification belong in `core/networking`. Transport helpers must preserve coroutine cancellation rather than converting it to a failure code.
- `core` provides reusable infrastructure without depending on `popup` or `identity`. It is a package, not a DI container or a separate module.
- `identity` remains an independent feature that returns user ID transition decisions; the host SDK applies the resulting side effects.

The popup call path is `PopupRenderer -> RenderPopupUseCase -> PopupRenderRepository`. The factory injects `PopupRenderRepositoryImpl`, which implements that interface and uses `core.networking`. This keeps the domain independent of the HTTP implementation.

### Host SDK boundary and lifecycle

- The host SDK supplies the project ID, rendering base URL, full SDK version header, campaign/user/device IDs, and triggering event. Do not hardcode environment selection or read host SDK global state inside KMP.
- KMP resolves rendering results; the host SDK owns popup eligibility, UI/WebView presentation, the existing static path, and UI-thread dispatch.
- Preserve the static fast path: only the exact mode `ssr` requests rendering. Other modes return `static` without request validation or network access.
- Keep the public API usable from Kotlin, Swift, and JavaScript. Do not expose Ktor clients, coroutine types, or serialization internals. Add `@JsExport` to intended JavaScript entry points, not internal helpers.
- Kotlin and Swift callers construct `PopupRenderInput` with native collections. JavaScript callers use `createPopupRenderInput` to adapt plain objects and arrays. Keep event parameters as structured values at the public boundary, not JSON strings.
- Renderers borrow one lazily initialized, runtime-shared HTTP client and require no explicit close step. Keep configuration and request state out of the shared client; test-owned clients remain the test's responsibility to close.
- Cancellation belongs to each `PopupRenderTask`. It must not close the shared client or cancel another request. Do not introduce renderer-wide closed state to manage individual requests.
- Completion and cancellation compete for one terminal result. Deliver callbacks asynchronously outside the request lock, without promising the main thread, and release request references after completion.

### Placement and extension rules

- Apply the platform source-set boundary above when adding or moving code. Do not duplicate business rules across source sets or use feature-specific platform entry points as a default architecture.
- Keep generic platform capabilities in `core`; for example, locking belongs in `core/concurrency`, not `popup/internal`.
- Place reusable, feature-independent helpers in focused files such as `StringUtils.kt`, `JsonUtils.kt`, or `UrlUtils.kt`. Keep popup validation, rendering modes, status mapping, and request lifecycle rules inside `popup`.
- Follow the feature/package boundaries when adding behavior. Do not introduce `api`/`implementation` module pairs, extra abstraction layers, or generic manager/policy objects solely to mirror a diagram.
- Mirror production packages in the appropriate test source set. Shared behavior belongs in `commonTest`; platform interop, transport, and concurrency behavior belongs in platform tests.
- `scripts/` contains release automation and artifact consumer checks, documented in `scripts/README.md`. `smoke-tests/` contains standalone consumer projects that verify the packaged library from outside its module.
- Keep `karma.config.d/` for browser test configuration loaded by Karma, including the loopback HTTP fixture. It is not a general script directory.
- Treat `build/` and generated bindings/artifacts as outputs. Update source declarations or build configuration rather than editing generated files.

## Kotlin style and linting

- Use the pinned ktlint Gradle plugin and engine configured in `build.gradle.kts` and the style rules in `.editorconfig`.
- Run `./gradlew ktlintCheck --no-daemon` before committing or pushing Kotlin changes.
- Run `./gradlew ktlintFormat --no-daemon` to apply automatic fixes, inspect the diff, and rerun the check.
- Kotlin production code, tests, Gradle Kotlin scripts, and Kotlin consumer smoke fixtures are in scope. Generated files and build outputs are excluded.
- CI and release workflows check formatting; they do not rewrite files.
- Prefer explicit imports. Use four-space indentation and a 120-character Kotlin line limit.
- Do not add a baseline or disable rules solely to hide existing violations. Explain any necessary, narrowly scoped exception.
- ktlint enforces syntax and style, not English prose or the meaning of tests. Review the conventions below as well.

## Comment conventions

### Placement and purpose

- Write explanations above the relevant function, class, or interface. Use KDoc (`/** ... */`) for Kotlin declarations.
- Document public API purpose and usage contracts. Include input conditions, results, failures, cancellation, resource ownership, and threading or callback guarantees when relevant.
- Add short declaration-level explanations for internal functions whose intent or preconditions are not obvious. Do not add boilerplate comments to every trivial function.
- Keep design rationale and constraints that apply to an entire function above its declaration. Use paragraphs for longer explanations instead of narrating implementation steps line by line.

### KDoc format

- Start with a concise summary. Put additional conditions or caveats in a separate paragraph.
- Use `/** Short description. */` when the explanation fits on one line. For multiline KDoc, put the delimiters on separate lines and start each content line with ` * `.
- Link declarations and parameters with KDoc references such as `[render]` and `[input]`. Use backticks for literal values and code expressions.
- Describe parameters and return values in prose where practical. Use `@param` and `@return` only when separate, substantial descriptions improve readability; do not add empty or redundant tags.
- Document shared contracts on the common declaration or interface. Do not copy the same explanation into every override or platform implementation; document only platform-specific differences there.

For example, document the contract above `PopupRenderTask.cancel()`:

```kotlin
/**
 * Requests cancellation of this render.
 *
 * Cancellation competes with completion for the first terminal result.
 * Calls after a terminal result have no additional effect.
 */
```

### Minimal inline comments

- Minimize `//` and `/* ... */` comments inside functions. Consider clearer names or smaller functions before adding an explanation.
- Do not repeat the code in prose, number routine execution steps, or add decorative separators.
- Keep inline comments only when their exact location is important to explain a non-obvious constraint, such as a concurrency invariant, platform workaround, or security requirement.
- Keep necessary inline comments short and directly above the relevant code. Do not remove useful risk explanations merely to reduce the comment count.
- Add `TODO` comments only when necessary, with a real issue number or link.

Do not annotate `state.callback = null` with `// Clear the callback.` Explain the concurrency contract above the function:

```kotlin
/** Claims the first terminal result under the request lock, then delivers it outside the lock. */
```

### Maintenance and other languages

- Update comments whenever behavior changes. Document only guarantees the implementation actually provides.
- Apply the same declaration-first, minimal-inline principles to JavaScript, Swift, Ruby, and shell code using the appropriate comment syntax.

## Test conventions

### Names and structure

- Use `kotlin.test` for shared tests. Name files and classes after the subject, such as `PopupRendererTest`; add a qualifier for focused integration or concurrency tests.
- Name new or substantially rewritten tests `<subject>_<condition>_<expectedResult>`, using camelCase within each segment. For example, `render_staticMode_returnsStaticWithoutFetching`.
- Keep `@Test` on its own line. Separate preparation, execution, and assertions with blank lines (Arrange-Act-Assert), without requiring repeated section comments.
- Test one behavior per test, not necessarily one assertion. A lifecycle scenario may contain multiple actions when their order is the behavior under test.
- Prefer named arguments when positional values, repeated booleans, or nulls obscure intent. Do not compress multiple statements onto one line.
- Let the test name explain the purpose. Add KDoc only for a non-obvious reproduction condition or platform constraint.

### Assertions and fixtures

- Verify observable results and contracts rather than private implementation details. Verify interactions when they are part of the contract, such as avoiding HTTP requests for static popups.
- Prefer specific assertions such as `assertEquals(expected, actual)` and `assertNull(actual)` over generic Boolean comparisons.
- Keep important inputs and expected values visible in the test. Use small setup helpers; do not introduce a shared base class or custom DSL merely to remove duplication.
- Table-driven cases may share a test when they verify the same behavior. Include a case label or input in failure messages and avoid computing expected results with production logic.
- Keep tests independent of execution order and shared mutable state. Release test-owned clients, servers, and other resources, and cancel pending render tasks even when an assertion fails, using `finally` or lifecycle hooks.

### Multiplatform and asynchronous tests

- Put platform-independent behavior in `commonTest`; use `jvmTest`, `iosTest`, and `jsTest` for platform-specific behavior.
- Write shared coroutine tests as `fun ...() = runTest { ... }` so the test result is returned immediately on JavaScript as well.
- Use `StandardTestDispatcher(testScheduler)` and virtual time for deterministic coroutine unit tests. Share one scheduler and use `runCurrent`, `advanceTimeBy`, or `advanceUntilIdle` according to the behavior being checked.
- Do not use real sleeps to coordinate coroutine unit tests. Real-thread concurrency and real-transport tests may use platform facilities, with bounded waits and reliable cleanup; virtual time does not replace those tests.
- Use Ktor `MockEngine` for HTTP request/response contracts and local fixtures for actual transport behavior. Do not call staging or production services from the unit suite.
- Run `./gradlew jvmTest jsNodeTest jsBrowserTest iosSimulatorArm64Test --no-daemon` after shared behavior changes. Apple targets require macOS and Xcode, and browser tests require Chrome.

## References

- [ktlint Gradle plugin](https://github.com/JLLeitschuh/ktlint-gradle)
- [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [KDoc syntax](https://kotlinlang.org/docs/kotlin-doc.html)
- [Kotlin library documentation guidelines](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html)
- [Google code review guidance on comments](https://google.github.io/eng-practices/review/reviewer/looking-for.html#comments)
- [Google unit testing guidance](https://abseil.io/resources/swe-book/html/ch12.html)
- [Kotlin Multiplatform testing](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)
- [Coroutine test API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Ktor client testing](https://ktor.io/docs/client-testing.html)
