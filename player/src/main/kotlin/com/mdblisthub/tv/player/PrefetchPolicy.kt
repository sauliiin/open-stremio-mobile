package com.mdblisthub.tv.player

/**
 * Who owns the link right now — the player, or the worker filling the disk
 * cache ahead of it.
 *
 * Split out of [MediaPrefetcher] for the same reason [StallTracker] was split
 * out of the stall watch: the rule is a decision over a *series* of samples,
 * and a series is what cannot be checked by watching a film play. The failure
 * it guards against — a second HTTP download holding a slow link while the
 * player drains toward empty — looks, from the sofa, exactly like the mirror
 * being slow.
 *
 * **The rule is unchanged; what was missing was when it is consulted.** A
 * player is starving when its own loader wants bytes *and* it is holding less
 * than [MediaPrefetcher.STARVING_BUFFER_MS] of film. Both halves matter, and
 * the reasoning for that is documented on the constant — dropping either one
 * switched this class off on the files it exists for, or left it competing on
 * the ones it must not.
 *
 * Two things it now answers that it did not before:
 *
 * - **Whether a download already in flight has to be abandoned.** Standing
 *   down only ever stopped the *next* chunk; a `CacheWriter` copying its 16MB
 *   would hold the second connection open until that whole read finished,
 *   which is exactly long enough to keep a starving player starving.
 * - **Immediately, rather than on the next position tick.** That tick runs
 *   every few seconds, which is an age to spend sharing a link the player has
 *   already run out of road on.
 */
internal class PrefetchPolicy(
    private val starvingBufferMs: Long,
) {

    /** What the caller must do to the download in flight, if anything. */
    enum class Effect { NONE, CANCEL_IN_FLIGHT }

    /** False while the player is starving; see the class note. */
    @Volatile
    var mayStartChunk: Boolean = false
        private set

    /**
     * The last buffer reading seen, so a signal that carries none — a loading
     * change is news about the loader, not a measurement of the buffer — can
     * still be judged against something real rather than against a guess.
     */
    private var lastBufferedMs = UNSET

    /** A periodic sample: where the playhead is and what the player is doing. */
    fun onSample(loading: Boolean, bufferedAheadMs: Long): Effect {
        lastBufferedMs = bufferedAheadMs
        return apply(starving = loading && bufferedAheadMs < starvingBufferMs)
    }

    /**
     * The player's loader started or stopped wanting bytes.
     *
     * Judged against the buffer last measured, because that is a real reading
     * seconds old rather than an invented one — and until a sample says
     * otherwise, a player that has just started loading with a thin buffer is
     * starving now, not in four seconds.
     */
    fun onLoadingChanged(loading: Boolean): Effect {
        val buffered = lastBufferedMs
        if (buffered == UNSET) return apply(starving = loading)
        return apply(starving = loading && buffered < starvingBufferMs)
    }

    /**
     * The picture has actually stopped — `STATE_BUFFERING`.
     *
     * No reading needed and none trusted: a stopped picture *is* the evidence,
     * and it arrives as an event rather than waiting for the next tick to
     * notice.
     */
    fun onPlayerStalled(): Effect {
        lastBufferedMs = UNSET
        return apply(starving = true)
    }

    /**
     * The viewer moved the playhead, so the bytes in flight were fetched for
     * somewhere they have left. The buffer history goes with them: a reading
     * taken at the old position says nothing about the new one.
     */
    fun onSeek(): Effect {
        lastBufferedMs = UNSET
        return Effect.CANCEL_IN_FLIGHT
    }

    /** Between sources there is nothing to work ahead of and no history worth keeping. */
    fun reset() {
        mayStartChunk = false
        lastBufferedMs = UNSET
    }

    private fun apply(starving: Boolean): Effect {
        mayStartChunk = !starving
        return if (starving) Effect.CANCEL_IN_FLIGHT else Effect.NONE
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE
    }
}
