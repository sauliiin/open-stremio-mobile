package com.mdblisthub.tv.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.core.content.ContextCompat
import com.mdblisthub.tv.R
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android's floating-window mode, for the mobile build.
 *
 * **Why this is not simply a call to `enterPictureInPictureMode`.** The mode is
 * entered by the *system*, at moments this app does not control — the home
 * gesture, the recents gesture, a notification tapped from the shade — and it
 * reshapes the window from whatever [PictureInPictureParams] the activity is
 * holding at that instant. Params assembled in response to leaving are already
 * too late on Android 12 and later, where the transition is seamless precisely
 * because the system does not wait to ask. So the params are kept current
 * throughout playback and the mode is something the app has *consented to* in
 * advance, rather than something it performs.
 *
 * Three things follow from that, and they are the whole design here:
 *
 * - The aspect ratio has to be the film's, published from the decoder rather
 *   than measured off the surface — see [PlaybackState.videoWidth].
 * - The play/pause control inside the window is a [RemoteAction], not a
 *   composable: the window is a scaled-down rendering of the activity and does
 *   not receive touches. Its button talks back through a broadcast.
 * - Everything the app draws over the video has to get out of the way, because
 *   at a couple of hundred dp there is room for the picture and nothing else.
 */
/**
 * What the player route needs from whichever activity is hosting it.
 *
 * Both halves of picture-in-picture live on the Activity and neither can move:
 * `onUserLeaveHint` is the only notice Android gives before the home gesture
 * completes, and the mode change arrives as an Activity callback. The player
 * is a composable several layers inside a nav graph, so this is the seam
 * between them — narrow on purpose, so the screen depends on two facts rather
 * than on `MainActivity`.
 */
interface PipHost {

    /** True while the app is drawing into the floating window. */
    val inPipMode: StateFlow<Boolean>

    /**
     * Invoked as the user leaves, and only then.
     *
     * Set by the player while it is on screen and cleared when it leaves, so
     * that leaving any *other* screen behaves as it always did. Returning true
     * means the floating window was entered.
     */
    var onLeaveHint: (() -> Boolean)?
}

object PictureInPicture {

    /** Broadcast action carrying a [RemoteAction] press back to the activity. */
    private const val ACTION_CONTROL = "com.mdblisthub.tv.PIP_CONTROL"
    private const val EXTRA_CONTROL = "control"
    private const val CONTROL_TOGGLE_PLAY = 1

    /**
     * Android refuses a ratio outside 1:2.39–2.39:1 by throwing, so a film
     * wider than scope — or a vertical clip — has to be clamped rather than
     * passed through.
     *
     * The bound here is deliberately a hair *inside* the platform's, and that
     * margin is not decoration. The ratio is rebuilt from integers over a
     * fixed denominator, so a clamp landing exactly on 1/2.39 cannot be
     * represented and lands just outside it instead — which is how clamping
     * to the legal minimum produced an illegal value, and a crash on the one
     * shape the clamp existed to rescue.
     */
    private const val MAX_RATIO = 2.38
    private const val MIN_RATIO = 1.0 / 2.38

    /** Whether this device offers the mode at all; a tablet or phone usually does. */
    fun isAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * A window shape, as plain integers.
     *
     * Not `android.util.Rational`, deliberately. The clamp below is the part
     * that can be wrong in a way nothing on screen would reveal — an
     * out-of-range ratio does not letterbox, it throws
     * `IllegalArgumentException` out of `setAspectRatio`, and only for the
     * handful of releases wide enough to reach it. Keeping the arithmetic free
     * of platform types is what lets that be checked on the JVM rather than
     * discovered by a viewer with an unusually wide remux.
     */
    data class Shape(val numerator: Int, val denominator: Int)

    /**
     * The window shape for a frame of [width] x [height], clamped to what
     * Android accepts, or null when the decoder has not reported a size yet.
     */
    fun aspectRatio(width: Int, height: Int): Shape? {
        if (width <= 0 || height <= 0) return null
        val ratio = width.toDouble() / height.toDouble()
        val clamped = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        // Scaled to a fixed denominator rather than handed the raw pixel
        // counts: a clamped ratio is by definition no longer expressible as
        // the original integers, and passing those would hand Android back the
        // out-of-range value the clamp just removed.
        // Rounded, not truncated: truncation only ever moves the value
        // downward, which is the wrong direction at the lower bound.
        val numerator = max(1, (clamped * DENOMINATOR).roundToInt())
        return Shape(numerator, DENOMINATOR)
    }

    private const val DENOMINATOR = 10_000

    /**
     * Rebuilds the params and hands them to the activity.
     *
     * Called on every change worth reflecting — the video size arriving, the
     * film being paused or resumed — so that whenever the system decides to
     * shrink the window, what it finds is already right. On Android 12+ this
     * also carries the standing consent to auto-enter, which is what makes the
     * home gesture continue the film instead of stopping it.
     */
    fun update(activity: Activity, width: Int, height: Int, playing: Boolean, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val params = buildParams(activity, width, height, playing, autoEnter = enabled) ?: return
        runCatching { activity.setPictureInPictureParams(params) }
    }

    /**
     * Enters the mode now, for the moments the system will not do it itself.
     *
     * Reached from the OSD button, and from `onUserLeaveHint` below Android 12
     * where there is no standing consent to give. Returns whether the mode was
     * actually entered: the system refuses while a dialog is up, in split
     * screen, or when the user has turned the mode off for this app — none of
     * which is an error worth surfacing, but all of which mean the caller's
     * assumption that the film carries on is wrong.
     *
     * Takes the same shape and action as [update] rather than an empty builder.
     * An empty `PictureInPictureParams` is not "keep what you had"; it is a
     * window with no declared aspect ratio and no controls, which is how a
     * correctly-shaped film ends up in a default-shaped window the moment the
     * viewer uses the button instead of the gesture.
     */
    fun enter(activity: Activity, width: Int, height: Int, playing: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val params = buildParams(activity, width, height, playing, autoEnter = true) ?: return false
        return runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    private fun buildParams(
        activity: Activity,
        width: Int,
        height: Int,
        playing: Boolean,
        autoEnter: Boolean,
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder()
            .setActions(listOf(toggleAction(activity, playing)))
        aspectRatio(width, height)?.let { builder.setAspectRatio(Rational(it.numerator, it.denominator)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The seamless path. Without it, Android 12+ still enters the mode
            // from the leave hint, but with a visible stutter as the activity
            // is stopped and re-laid-out rather than scaled.
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    /**
     * The exact intent the window's button fires.
     *
     * One function rather than an inline builder in [toggleAction], because
     * the receiver below matches on action *and* extra: if those two ever
     * drift apart the button silently does nothing, which is a failure with no
     * symptom to notice and nothing in a log to find. Sharing the construction
     * also gives a test something real to send.
     */
    internal fun controlIntent(context: Context): Intent = Intent(ACTION_CONTROL)
        // Package-scoped, so it is not an implicit broadcast the platform
        // would refuse to deliver on modern API levels.
        .setPackage(context.packageName)
        .putExtra(EXTRA_CONTROL, CONTROL_TOGGLE_PLAY)

    /**
     * Listens for the window's own play/pause button.
     *
     * Takes the action rather than the player: what happens when the button is
     * pressed is the caller's business, and keeping the player out of this file
     * is what lets the delivery path be tested without building one.
     *
     * Registered only while the player route is on screen and unregistered
     * with it — a receiver outliving the controller it drives would be holding
     * a released player.
     */
    fun registerControls(context: Context, onTogglePlay: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_CONTROL) return
                when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                    CONTROL_TOGGLE_PLAY -> onTogglePlay()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_CONTROL),
            // The broadcast never leaves this app, and on API 33+ an
            // unspecified export flag is a crash rather than a default.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    private fun toggleAction(context: Context, playing: Boolean): RemoteAction {
        val label = context.getString(
            if (playing) R.string.player_pause else R.string.player_play,
        )
        val icon = Icon.createWithResource(
            context,
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val pending = PendingIntent.getBroadcast(
            context,
            CONTROL_TOGGLE_PLAY,
            controlIntent(context),
            // Mutable would let another app rewrite the extra; immutable is
            // required outright from API 31 and correct before it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(icon, label, label, pending)
    }
}
