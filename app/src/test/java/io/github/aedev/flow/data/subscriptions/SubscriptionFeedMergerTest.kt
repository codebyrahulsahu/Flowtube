package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.UNKNOWN_VIEW_COUNT
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedMerger.preservingEnrichedMetadata
import org.junit.Test

/**
 * RSS entries without `<media:statistics>` used to arrive as 0 and, via `maxOf`, beat the -1 a
 * NewPipe channel tab reports for the same video — and once cached, that 0 was served to Home
 * until the next full refresh.
 */
class SubscriptionFeedMergerTest {
    private val now = 1_700_000_000_000L

    private fun video(
        viewCount: Long,
        uploadDate: String = "2 days ago",
    ) = Video(
        id = "video",
        title = "Title",
        channelName = "Channel",
        channelId = "UC123",
        thumbnailUrl = "",
        duration = 120,
        viewCount = viewCount,
        uploadDate = uploadDate,
        timestamp = now - 2L * 24L * 60L * 60L * 1000L,
    )

    @Test
    fun `duplicate merge keeps the known count over an unknown one`() {
        val merged =
            SubscriptionFeedMerger.mergeDuplicates(
                candidates = listOf(video(viewCount = UNKNOWN_VIEW_COUNT), video(viewCount = 4_242L)),
                now = now,
            )

        assertThat(merged.viewCount).isEqualTo(4_242L)
    }

    @Test
    fun `duplicate merge stays unknown when no source knows the count`() {
        val merged =
            SubscriptionFeedMerger.mergeDuplicates(
                candidates = listOf(video(viewCount = UNKNOWN_VIEW_COUNT), video(viewCount = UNKNOWN_VIEW_COUNT)),
                now = now,
            )

        assertThat(merged.viewCount).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    @Test
    fun `a refresh without statistics keeps the count a previous pass resolved`() {
        val fresh = video(viewCount = UNKNOWN_VIEW_COUNT)
        val prior = video(viewCount = 9_001L)

        assertThat(fresh.preservingEnrichedMetadata(prior).viewCount).isEqualTo(9_001L)
    }

    @Test
    fun `a fresh known count replaces an unknown prior one`() {
        val fresh = video(viewCount = 15L)
        val prior = video(viewCount = UNKNOWN_VIEW_COUNT)

        assertThat(fresh.preservingEnrichedMetadata(prior).viewCount).isEqualTo(15L)
    }
}
