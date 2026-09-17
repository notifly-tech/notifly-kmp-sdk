package tech.notifly.kmp.core.concurrency

internal actual class PlatformLock {
    private val monitor = Any()

    actual fun <T> withLock(block: () -> T): T = synchronized(monitor, block)
}
