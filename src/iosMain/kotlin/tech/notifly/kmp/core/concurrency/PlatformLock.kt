package tech.notifly.kmp.core.concurrency

import platform.Foundation.NSRecursiveLock

internal actual class PlatformLock {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
