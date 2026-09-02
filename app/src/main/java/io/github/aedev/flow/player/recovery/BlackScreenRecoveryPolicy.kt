package io.github.aedev.flow.player.recovery

import androidx.media3.common.Player

/**
 * Escalation policy for a player that is READY and playing but has rendered no video frame —
 * the classic "black screen while the playhead and audio keep moving" failure.
 *
 * Recovery is a bounded escalation, one action per [evaluate] call:
 *  1. [Action.REATTACH_SURFACE] — rebind the video output, which recovers a codec/surface pair that
 *     got stuck (surface recreation, rotation, screen-off/on) without producing frames.
 *  2. [Action.REFRESH_STREAM]  — tear the media item down and reload it at the current position,
 *     which recovers a stream that no longer delivers decodable video.
 *  3. [Action.GIVE_UP]         — the escalation ran its course; stop and leave the existing
 *     error/retry paths in charge.
 *
 * Every grace window is measured in *continuous READY-and-playing time*: the clock only advances
 * while playback is actually READY (so a slow buffer is never mistaken for a black screen) and it
 * restarts whenever playback re-enters READY. A rendered frame at any point resets the whole
 * escalation, because it proves the pipeline is healthy again.
 *
 * The policy owns no player and no coroutine — the caller (the player manager) performs the
 * mutations and feeds [evaluate] one snapshot per poll tick, keeping the state machine pure and
 * unit-testable.
 */
class BlackScreenRecoveryPolicy(
    private val readyNoFrameGraceMs: Long = DEFAULT_READY_NO_FRAME_GRACE_MS,
    private val reattachGraceMs: Long = DEFAULT_REATTACH_GRACE_MS,
    private val refreshGraceMs: Long = DEFAULT_REFRESH_GRACE_MS,
    private val maxEscalations: Int = DEFAULT_MAX_ESCALATIONS,
    private val clockMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    enum class Action {
        NONE,
        REATTACH_SURFACE,
        REFRESH_STREAM,
        GIVE_UP,
    }

    private enum class Stage {
        WAITING,
        AFTER_REATTACH,
        AFTER_REFRESH,
        GAVE_UP,
    }

    private var videoId: String? = null
    private var stage = Stage.WAITING
    private var escalations = 0
    private var graceStartedAtMs = 0L
    private var wasReady = false

    /**
     * Evaluate the current player snapshot. Call once per poll tick.
     *
     * @param videoId the id of the media item currently loaded (null when nothing is loaded).
     * @param playbackState the ExoPlayer playback state.
     * @param playWhenReady whether playback is expected to be running.
     * @param firstFrameRendered whether a video frame has rendered since the last media load or
     *   surface reattach.
     * @param audioOnly whether video output is disabled (background audio-only playback).
     */
    fun evaluate(
        videoId: String?,
        playbackState: Int,
        playWhenReady: Boolean,
        firstFrameRendered: Boolean,
        audioOnly: Boolean,
    ): Action {
        if (videoId == null) {
            reset(videoId)
            return Action.NONE
        }
        if (this.videoId != videoId) reset(videoId)
        if (stage == Stage.GAVE_UP) return Action.NONE

        // A rendered frame heals every in-flight stage.
        if (firstFrameRendered) {
            stage = Stage.WAITING
            wasReady = false
            graceStartedAtMs = 0L
            return Action.NONE
        }

        // No video output is attached, so a missing frame is expected, not a fault.
        if (audioOnly) {
            wasReady = false
            graceStartedAtMs = 0L
            return Action.NONE
        }

        val ready = playbackState == Player.STATE_READY && playWhenReady
        if (!ready) {
            wasReady = false
            return Action.NONE
        }
        if (!wasReady) {
            // Playback just entered READY — (re)arm the grace clock for the current stage.
            wasReady = true
            graceStartedAtMs = clockMs()
            return Action.NONE
        }

        val elapsed = clockMs() - graceStartedAtMs
        return when (stage) {
            Stage.WAITING ->
                if (elapsed >= readyNoFrameGraceMs) {
                    advance(Stage.AFTER_REATTACH, Action.REATTACH_SURFACE)
                } else {
                    Action.NONE
                }

            Stage.AFTER_REATTACH ->
                if (elapsed >= reattachGraceMs) {
                    advance(Stage.AFTER_REFRESH, Action.REFRESH_STREAM)
                } else {
                    Action.NONE
                }

            Stage.AFTER_REFRESH ->
                if (elapsed >= refreshGraceMs) {
                    escalations++
                    if (escalations >= maxEscalations) {
                        stage = Stage.GAVE_UP
                        Action.GIVE_UP
                    } else {
                        advance(Stage.AFTER_REATTACH, Action.REATTACH_SURFACE)
                    }
                } else {
                    Action.NONE
                }

            Stage.GAVE_UP -> Action.NONE
        }
    }

    fun reset(videoId: String?) {
        this.videoId = videoId
        stage = Stage.WAITING
        escalations = 0
        graceStartedAtMs = 0L
        wasReady = false
    }

    private fun advance(nextStage: Stage, action: Action): Action {
        stage = nextStage
        wasReady = false
        graceStartedAtMs = 0L
        return action
    }

    companion object {
        const val DEFAULT_READY_NO_FRAME_GRACE_MS = 4_000L
        const val DEFAULT_REATTACH_GRACE_MS = 3_000L
        const val DEFAULT_REFRESH_GRACE_MS = 6_000L
        const val DEFAULT_MAX_ESCALATIONS = 2
    }
}
