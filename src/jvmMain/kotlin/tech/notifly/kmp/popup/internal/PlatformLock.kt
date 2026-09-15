package tech.notifly.kmp.popup.internal

internal actual class PlatformLock {
    private val monitor = Any()
    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
