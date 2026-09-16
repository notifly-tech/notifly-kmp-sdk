# AGENTS.md

이 파일은 이 저장소에서 작업하는 에이전트의 주석 작성 기준을 정의합니다.

## 주석 컨벤션

### 설명 위치와 목적

- 코드 주석은 영어로 작성합니다.
- 설명은 기본적으로 함수, 클래스, 인터페이스 등 선언 바로 위에 작성합니다. Kotlin에서는 KDoc(`/** ... */`)을 사용합니다.
- 공개 API에는 목적과 사용 계약을 설명합니다. 필요한 경우 입력 조건, 결과, 실패 동작, 취소, 자원 수명, 스레드 또는 콜백 실행 관련 보장을 포함합니다.
- 내부 함수도 의도나 전제 조건이 이름과 코드만으로 명확하지 않으면 함수 위에 짧게 설명합니다. 모든 단순 함수에 형식적인 주석을 추가하지는 않습니다.
- 함수 전체에 적용되는 설계 이유나 제약은 함수 위에 모읍니다. 긴 설명이 필요하면 문단을 나누되 구현 과정을 줄마다 나열하지 않습니다.

### KDoc 형식

- 첫 문단은 역할을 짧고 명확하게 요약합니다. 추가 사용 조건이나 주의사항은 빈 줄 뒤에 작성합니다.
- 한 줄에 충분히 들어가는 설명은 `/** Short description. */` 형식을 사용할 수 있습니다. 여러 줄이면 `/**`와 `*/`를 별도 줄에 두고 각 본문 줄을 ` * `로 시작합니다.
- 함수, 타입, 파라미터를 참조할 때는 `[render]`, `[input]` 같은 KDoc 링크를 사용합니다. 문자열 값이나 코드 표현에는 백틱을 사용합니다.
- 파라미터와 반환값은 가능하면 본문에서 설명합니다. 본문 흐름에 넣기 어려운 긴 설명에만 `@param`과 `@return`을 사용하고, 빈 태그나 이름을 반복하는 설명은 작성하지 않습니다.
- 공통 계약은 공통 선언이나 인터페이스에 작성합니다. 동일한 내용을 모든 override나 플랫폼 구현에 복사하지 말고, 플랫폼별 차이가 있을 때만 해당 선언 위에 보완합니다.

예를 들어 `PopupRenderer.close()` 위에는 다음처럼 사용 계약을 설명합니다.

```kotlin
/**
 * Cancels pending renders and releases owned resources.
 *
 * Subsequent calls to [render] fail with `renderer_closed`.
 * Repeated calls to this method have no additional effect.
 */
```

### 함수 내부 주석 최소화

- 함수 내부의 `//` 및 `/* ... */` 주석은 최소화합니다. 이름 개선이나 함수 분리로 의도를 표현할 수 있는지 먼저 검토합니다.
- 코드가 무엇을 하는지 그대로 반복하는 주석, 실행 단계를 번호로 나열하는 주석, 장식용 구분선은 추가하지 않습니다.
- 특정 코드 위치에 있어야 오해를 막을 수 있는 예외적인 이유나 제약만 내부 주석으로 남깁니다. 예를 들어 동시성 불변 조건, 플랫폼 버그 우회, 보안상 필요한 처리의 이유가 해당합니다.
- 내부 주석이 꼭 필요하면 관련 코드 바로 위에 짧게 작성합니다. 실제 위험을 설명하는 유용한 주석을 줄 수를 줄이기 위해 무조건 삭제하지는 않습니다.
- 향후 작업은 필요한 경우에만 `TODO`로 남기고, 실제 이슈 번호나 링크를 함께 적습니다.

예를 들어 단순한 `pending.remove(state)`에 `// Remove the request.`를 붙이지 않습니다. 반면 잠금 소유 조건이 필요한 내부 함수라면 함수 위에 다음처럼 설명할 수 있습니다.

```kotlin
/** Settles a request while the caller holds the renderer lock. */
```

### 유지보수와 다른 언어

- 동작을 변경할 때 관련 주석도 함께 수정합니다. 주석에는 현재 구현이 실제로 보장하는 동작만 적습니다.
- JS, Swift, Ruby, 셸 코드에도 선언 위 설명과 내부 주석 최소화 원칙을 적용하되, 주석 문법은 해당 언어에 맞춥니다.

## 참고 자료

- [Kotlin documentation comments](https://kotlinlang.org/docs/coding-conventions.html#documentation-comments)
- [KDoc syntax](https://kotlinlang.org/docs/kotlin-doc.html)
- [Kotlin library documentation guidelines](https://kotlinlang.org/docs/api-guidelines-informative-documentation.html)
- [Google code review guidance on comments](https://google.github.io/eng-practices/review/reviewer/looking-for.html#comments)
