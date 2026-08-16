package com.mdblisthub.tv.ui.player

import com.mdblisthub.tv.player.PlaybackPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePreparingScreenTest {
    @Test
    fun `offline route owns its very first frame`() {
        assertTrue(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.IDLE,
                noAddons = false,
                missingImdbId = false,
            ),
        )
        assertFalse(
            shouldShowOfflinePreparing(
                downloadOffline = false,
                phase = PlaybackPhase.IDLE,
                noAddons = false,
                missingImdbId = false,
            ),
        )
    }

    @Test
    fun `offline preparation never covers failures or source selection`() {
        assertFalse(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.IDLE,
                noAddons = true,
                missingImdbId = false,
            ),
        )
        assertFalse(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.SELECTING,
                noAddons = false,
                missingImdbId = false,
            ),
        )
        assertFalse(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.FAILED,
                noAddons = false,
                missingImdbId = false,
            ),
        )
        assertFalse(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.IDLE,
                noAddons = false,
                missingImdbId = true,
            ),
        )
    }

    @Test
    fun `offline preparation remains visible while resolving`() {
        assertTrue(
            shouldShowOfflinePreparing(
                downloadOffline = true,
                phase = PlaybackPhase.RESOLVING,
                noAddons = false,
                missingImdbId = false,
            ),
        )
    }
}
