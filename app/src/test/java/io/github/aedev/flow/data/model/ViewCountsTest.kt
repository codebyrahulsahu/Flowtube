package io.github.aedev.flow.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The cards print `0 views` for zero and hide the line for negative counts, so the merge must
 * never let an unknown source flatten a known count to zero — the exact bug users saw on Home.
 */
class ViewCountsTest {
    @Test
    fun `absent counts map to the unknown sentinel`() {
        assertThat(null.orUnknownViewCount()).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(1_234L.orUnknownViewCount()).isEqualTo(1_234L)
    }

    @Test
    fun `newpipe's negative sentinel is kept instead of clamped to zero`() {
        assertThat((-1L).asKnownOrUnknownViewCount()).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(0L.asKnownOrUnknownViewCount()).isEqualTo(0L)
        assertThat(42L.asKnownOrUnknownViewCount()).isEqualTo(42L)
    }

    @Test
    fun `a known count wins over unknown duplicates`() {
        assertThat(listOf(UNKNOWN_VIEW_COUNT, 4_242L, UNKNOWN_VIEW_COUNT).bestKnownViewCount()).isEqualTo(4_242L)
        assertThat(bestKnownViewCount(UNKNOWN_VIEW_COUNT, 7L)).isEqualTo(7L)
        assertThat(bestKnownViewCount(7L, UNKNOWN_VIEW_COUNT)).isEqualTo(7L)
    }

    @Test
    fun `a genuine zero is a known count`() {
        assertThat(bestKnownViewCount(0L, UNKNOWN_VIEW_COUNT)).isEqualTo(0L)
    }

    @Test
    fun `all unknown stays unknown`() {
        assertThat(listOf(UNKNOWN_VIEW_COUNT, UNKNOWN_VIEW_COUNT).bestKnownViewCount()).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(emptyList<Long>().bestKnownViewCount()).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    @Test
    fun `the larger of two known counts is kept`() {
        assertThat(bestKnownViewCount(10L, 12L)).isEqualTo(12L)
    }
}
