package tech.notifly.kmp.popup.internal

internal expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
