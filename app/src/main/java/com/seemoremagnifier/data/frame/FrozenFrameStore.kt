package com.seemoremagnifier.data.frame

/** Owns the latest analysis candidate until it is transferred to the frozen UI. */
class LatestFrameStore<T>(private val release: (T) -> Unit) {
    private var candidate: T? = null
    private var paused = false
    private var closed = false

    @Synchronized
    fun offer(frame: T) {
        if (closed || paused) {
            release(frame)
            return
        }
        candidate?.let(release)
        candidate = frame
    }

    @Synchronized
    fun freezeLatest(): T? {
        if (closed) return null
        val latest = candidate ?: return null
        candidate = null
        paused = true
        return latest
    }

    @Synchronized
    fun resume() {
        if (closed) return
        paused = false
    }

    @Synchronized
    fun isPaused(): Boolean = paused || closed

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        paused = true
        candidate?.let(release)
        candidate = null
    }
}