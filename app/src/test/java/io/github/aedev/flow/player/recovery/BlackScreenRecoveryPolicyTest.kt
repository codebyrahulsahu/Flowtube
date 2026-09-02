package io.github.aedev.flow.player.recovery

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlackScreenRecoveryPolicyTest {
    private class ManualClock {
        var now = 0L
    }

    private fun policy(clock: ManualClock = ManualClock()) = BlackScreenRecoveryPolicy(clockMs = { clock.now })

    @Test
    fun `rendered frame keeps the watchdog idle`() {
        val clock = ManualClock()
        val p = policy(clock)

        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, true, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `ready without a frame escalates to surface reattach after the grace period`() {
        val clock = ManualClock()
        val p = policy(clock)

        // First READY tick arms the grace clock.
        clock.now = 1_000L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)

        // Just inside the grace window.
        clock.now = 1_000L + BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS - 1
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)

        // Grace elapsed -> reattach.
        clock.now = 1_000L + BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.REATTACH_SURFACE)
    }

    @Test
    fun `still frameless after reattach escalates to stream refresh`() {
        val clock = ManualClock()
        val p = policy(clock)

        clock.now = 1_000L
        p.evaluate("v1", Player.STATE_READY, true, false, false)
        clock.now = 1_000L + BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS
        p.evaluate("v1", Player.STATE_READY, true, false, false) // -> REATTACH_SURFACE

        // Re-arm the grace clock after the reattach.
        clock.now += 1L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)

        clock.now += BlackScreenRecoveryPolicy.DEFAULT_REATTACH_GRACE_MS
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.REFRESH_STREAM)
    }

    @Test
    fun `a rendered frame after reattach resets the escalation`() {
        val clock = ManualClock()
        val p = policy(clock)

        clock.now = 1_000L
        p.evaluate("v1", Player.STATE_READY, true, false, false)
        clock.now = 1_000L + BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS
        p.evaluate("v1", Player.STATE_READY, true, false, false) // -> REATTACH_SURFACE

        // Frame renders after the reattach.
        clock.now += 1_000L
        p.evaluate("v1", Player.STATE_READY, true, true, false)

        // Escalation reset: a fresh waiting grace begins, so still NONE here.
        clock.now += 10_000L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `buffering does not consume the grace budget`() {
        val clock = ManualClock()
        val p = policy(clock)

        clock.now += 2_000L
        p.evaluate("v1", Player.STATE_READY, true, false, false)

        // A long buffer pause resets the continuous-READY clock.
        clock.now += 60_000L
        p.evaluate("v1", Player.STATE_BUFFERING, true, false, false)

        clock.now += 1_000L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `audio-only playback never triggers recovery`() {
        val clock = ManualClock()
        val p = policy(clock)

        clock.now += BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS + 1_000L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, audioOnly = true),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `switching video resets the escalation state`() {
        val clock = ManualClock()
        val p = policy(clock)

        clock.now = BlackScreenRecoveryPolicy.DEFAULT_READY_NO_FRAME_GRACE_MS
        p.evaluate("v1", Player.STATE_READY, true, false, false)
        clock.now += 1_000L
        p.evaluate("v1", Player.STATE_READY, true, false, false)

        // A new video starts over from the waiting stage.
        clock.now += 1_000L
        assertThat(
            p.evaluate("v2", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `escalation is bounded and ends in give up`() {
        val clock = ManualClock()
        val p = policy(clock)

        var action = BlackScreenRecoveryPolicy.Action.NONE
        var ticks = 0
        while (action != BlackScreenRecoveryPolicy.Action.GIVE_UP && ticks < 100) {
            clock.now += 500L
            action = p.evaluate("v1", Player.STATE_READY, true, false, false)
            ticks++
        }

        assertThat(action).isEqualTo(BlackScreenRecoveryPolicy.Action.GIVE_UP)

        // After giving up, the watchdog stays silent until reset.
        clock.now += 60_000L
        assertThat(
            p.evaluate("v1", Player.STATE_READY, true, false, false),
        ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
    }

    @Test
    fun `give up stays terminal for the same video across repeated polls`() {
        val clock = ManualClock()
        val p = policy(clock)

        var action = BlackScreenRecoveryPolicy.Action.NONE
        var ticks = 0
        while (action != BlackScreenRecoveryPolicy.Action.GIVE_UP && ticks < 100) {
            clock.now += 500L
            action = p.evaluate("v1", Player.STATE_READY, true, false, false)
            ticks++
        }
        assertThat(action).isEqualTo(BlackScreenRecoveryPolicy.Action.GIVE_UP)

        // A give-up must never re-arm for the same media item: calling evaluate in a loop (as the
        // watchdog poll does) has to keep returning NONE, otherwise recovery would restart forever.
        repeat(200) {
            clock.now += 1_000L
            assertThat(
                p.evaluate("v1", Player.STATE_READY, true, false, false),
            ).isEqualTo(BlackScreenRecoveryPolicy.Action.NONE)
        }
    }
}
