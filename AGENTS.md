# AGENTS.md

This file defines repository conventions for coding agents.

## Language

- All repository documentation, code comments, identifiers, and test names must be written in English. This includes this file, README files, KDoc, and assertion messages.
- Non-English text is allowed only when required as test data, such as Unicode, encoding, or localization fixtures. Preserve those fixtures instead of translating them.
- This rule applies to repository content, not the language of conversations with the user.

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
