package com.mdblisthub.tv.ui.player

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The two halves of picture-in-picture the platform owns.
 *
 * Neither can be answered off-device, and neither fails in a way a viewer
 * would report usefully: an out-of-range aspect ratio throws out of
 * `setAspectRatio` rather than letterboxing, and a broadcast the platform
 * declines to deliver produces a button that simply does nothing. Tapping a
 * floating window by coordinate is not a reliable way to find either out —
 * its controls fade on their own timer, so a tap that lands late only wakes
 * them, and the test passes or fails on timing rather than on the code.
 */
@RunWith(AndroidJUnit4::class)
class PictureInPictureDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theModeIsAvailableOnThisDevice() {
        assertTrue(
            "the mobile build targets devices that offer the floating window",
            PictureInPicture.isAvailable(context),
        )
    }

    /**
     * The clamp, verified against the platform rather than against this app's
     * reading of it. Every shape below is handed to a real
     * [PictureInPictureParams.Builder]; an illegal ratio throws here exactly
     * as it would on the home gesture.
     */
    @Test
    fun everyShapeThisAppProducesIsOneAndroidAccepts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val frames = listOf(
            3840 to 2160, 1920 to 1080, 1280 to 720, 640 to 480,
            // The ends of the range, which is where the clamp is load-bearing.
            2760 to 1000, 4096 to 1716, 10000 to 1, 1 to 10000, 100 to 1000,
            1080 to 1920, 1 to 1,
        )
        for ((width, height) in frames) {
            val shape = PictureInPicture.aspectRatio(width, height)!!
            PictureInPictureParams.Builder()
                .setAspectRatio(Rational(shape.numerator, shape.denominator))
                .build()
        }
    }

    /**
     * The window's play/pause button, end to end minus the finger.
     *
     * The receiver is non-exported and the intent is package-scoped, so what
     * is being checked is precisely the thing that silently does not happen
     * when either of those is wrong: that a broadcast sent with this app's own
     * identity — which is what the `RemoteAction`'s `PendingIntent` does —
     * reaches the receiver and carries the extra the receiver matches on.
     */
    @Test
    fun theWindowsButtonReachesTheApp() {
        val pressed = CountDownLatch(1)
        val receiver = PictureInPicture.registerControls(context) { pressed.countDown() }
        try {
            context.sendBroadcast(PictureInPicture.controlIntent(context))
            assertTrue(
                "the floating window's button never reached the app",
                pressed.await(5, TimeUnit.SECONDS),
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /** An intent missing the extra must not toggle anything. */
    @Test
    fun anUnrelatedBroadcastIsIgnored() {
        val pressed = CountDownLatch(1)
        val receiver = PictureInPicture.registerControls(context) { pressed.countDown() }
        try {
            val stripped = PictureInPicture.controlIntent(context).apply { removeExtra("control") }
            context.sendBroadcast(stripped)
            assertEquals(
                "a broadcast without the control extra must do nothing",
                1L,
                pressed.count,
            )
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
