// SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package com.seemoremagnifier.data.camera

import android.os.SystemClock
import android.util.Log
import com.seemoremagnifier.BuildConfig
import java.util.concurrent.atomic.AtomicLong

/** Debug-only monotonic timing traces for comparing freeze/capture cycles on-device. */
internal object FreezeTiming {
    private const val TAG = "FreezeTiming"
    private val nextId = AtomicLong()

    class Trace internal constructor(
        val id: Long,
        internal val startedAtNs: Long,
    )

    fun start(): Trace {
        if (!BuildConfig.DEBUG) return Trace(0L, 0L)
        val trace = Trace(nextId.incrementAndGet(), SystemClock.elapsedRealtimeNanos())
        mark(trace, "tap")
        return trace
    }

    fun mark(trace: Trace, event: String) {
        if (!BuildConfig.DEBUG || trace.id == 0L) return
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - trace.startedAtNs) / NANOS_PER_MILLI
        Log.d(TAG, "id=${trace.id} +${elapsedMs}ms $event")
    }

    fun elapsedMsSince(startedAtNs: Long): Long =
        if (BuildConfig.DEBUG) {
            (SystemClock.elapsedRealtimeNanos() - startedAtNs) / NANOS_PER_MILLI
        } else {
            0L
        }

    private const val NANOS_PER_MILLI = 1_000_000L
}