package io.github.aedev.flow.player.surface

import android.os.Build
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoSurfacePolicyTest {
    @Test
    fun `surface view restore waits for a valid holder`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = false
            )
        ).isFalse()
    }

    @Test
    fun `surface view restores once its holder is valid`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = true
            )
        ).isTrue()
    }

    @Test
    fun `texture view restore does not require a surface holder`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isDisplayInteractive = true,
                isSurfaceValid = false
            )
        ).isTrue()
    }

    @Test
    fun `video output stays disabled while display is not interactive`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = false,
                isSurfaceValid = true
            )
        ).isFalse()
    }

    @Test
    fun `paused ready playback resyncs after a surface reattach`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_READY
            )
        ).isTrue()
    }

    @Test
    fun `paused buffering playback resyncs after a surface reattach`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_BUFFERING
            )
        ).isTrue()
    }

    @Test
    fun `active playback is not interrupted by a resync seek`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = true,
                isLive = false,
                playbackState = Player.STATE_READY
            )
        ).isFalse()
    }

    @Test
    fun `live streams never resync via seek`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = true,
                playbackState = Player.STATE_READY
            )
        ).isFalse()
    }

    @Test
    fun `idle and ended players are not resynced`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_IDLE
            )
        ).isFalse()
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_ENDED
            )
        ).isFalse()
    }

    @Test
    fun `paused playback resyncs when video output is restored`() {
        // Re-enabling the video renderer while paused creates a decoder nothing feeds — the
        // "audio but black until play/pause" report. A same-position seek pushes a frame through.
        assertThat(
            VideoSurfacePolicy.shouldResyncOnVideoRestore(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_READY
            )
        ).isTrue()
    }

    @Test
    fun `playing and live restores are left alone`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnVideoRestore(
                playWhenReady = true,
                isLive = false,
                playbackState = Player.STATE_READY
            )
        ).isFalse()
        assertThat(
            VideoSurfacePolicy.shouldResyncOnVideoRestore(
                playWhenReady = false,
                isLive = true,
                playbackState = Player.STATE_READY
            )
        ).isFalse()
    }

    @Test
    fun `a normal load re-enables video tracks`() {
        assertThat(
            VideoSurfacePolicy.shouldEnableVideoTracksOnLoad(
                audioOnlyLoad = false,
                audioOnlyModeActive = false
            )
        ).isTrue()
    }

    @Test
    fun `audio-only loads keep video tracks disabled`() {
        assertThat(
            VideoSurfacePolicy.shouldEnableVideoTracksOnLoad(
                audioOnlyLoad = true,
                audioOnlyModeActive = false
            )
        ).isFalse()
        // A queue advance while backgrounded must stay audio-only or playback stalls on a
        // surface that is not there.
        assertThat(
            VideoSurfacePolicy.shouldEnableVideoTracksOnLoad(
                audioOnlyLoad = false,
                audioOnlyModeActive = true
            )
        ).isFalse()
    }
}
