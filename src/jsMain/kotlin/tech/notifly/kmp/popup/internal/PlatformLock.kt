package tech.notifly.kmp.popup.internal

/** Runs transitions directly because non-suspending code cannot interleave within one JS runtime. */
internal actual class PlatformLock {
    actual fun <T> withLock(block: () -> T): T = block()
}
