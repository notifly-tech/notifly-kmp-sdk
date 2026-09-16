package tech.notifly.kmp.popup.internal

/** Guards synchronous state transitions without holding a lock across coroutine suspension. */
internal expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
