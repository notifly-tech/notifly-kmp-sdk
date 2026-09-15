package tech.notifly.kmp.popup.internal

internal actual class PlatformLock {
    actual fun <T> withLock(block: () -> T): T = block()
}
