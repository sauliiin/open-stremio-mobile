package com.mdblisthub.tv.ui.player

import com.mdblisthub.tv.core.model.PlayableStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList

internal const val MAX_AUTOMATIC_OFFLINE_BYTES = 5L * 1024 * 1024 * 1024

/**
 * Runs offline source discovery in two closed phases.
 *
 * The full flow is collected before either callback can run. This matters for
 * addons that answer late: a small valid source must not arrive after the app
 * has already fallen back to a manual picker.
 */
internal suspend fun runOfflineSourceFlow(
    candidates: Flow<PlayableStream>,
    validateAutomatic: suspend (List<PlayableStream>) -> Boolean,
    validateFallback: suspend (List<PlayableStream>) -> Unit,
) {
    val allSources = candidates.toList()
    val automaticSources = allSources.filter(PlayableStream::isAutomaticOfflineCandidate)

    val automaticSucceeded = automaticSources.isNotEmpty() &&
        validateAutomatic(automaticSources)
    if (automaticSucceeded) return

    // Automatic candidates have already failed the complete player check.
    // Repeating or re-querying them would put rejected links back in the UI.
    val fallbackSources = allSources.filterNot(PlayableStream::isAutomaticOfflineCandidate)
    validateFallback(fallbackSources)
}

private fun PlayableStream.isAutomaticOfflineCandidate(): Boolean =
    sizeBytes?.let { it in 1L..MAX_AUTOMATIC_OFFLINE_BYTES } == true
