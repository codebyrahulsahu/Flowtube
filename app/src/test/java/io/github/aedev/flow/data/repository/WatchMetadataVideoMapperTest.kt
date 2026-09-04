package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.UNKNOWN_VIEW_COUNT
import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import org.junit.Test

class WatchMetadataVideoMapperTest {

    @Test
    fun `related mapper preserves byline channel id when compact video exposes one`() {
        val response = watchMetadataResponse(
            compactVideo(
                videoId = "related-video",
                channelId = "UC1234567890",
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related).hasSize(1)
        assertThat(related.single().channelId).isEqualTo("UC1234567890")
    }

    @Test
    fun `related mapper leaves channel id blank when byline browse id is not a channel`() {
        val response = watchMetadataResponse(
            compactVideo(
                videoId = "related-video",
                channelId = "VLPL1234567890",
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related).hasSize(1)
        assertThat(related.single().channelId).isEmpty()
    }

    @Test
    fun `related mapper leaves the view count unknown when the renderer has none`() {
        val response = watchMetadataResponse(compactVideo(videoId = "related-video", channelId = "UC1234567890"))

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        // Home hides the views line for a negative count; a 0 here rendered as "0 views".
        assertThat(related.single().viewCount).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    @Test
    fun `related mapper parses a lockup view count`() {
        val response = watchMetadataResponse(
            WatchMetadataResponse.SecondaryItem(
                lockupViewModel = lockup(videoId = "lockup-video", metadataTexts = listOf("Flow Channel", "1.2M views", "3 days ago"))
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related.single().viewCount).isEqualTo(1_200_000L)
    }

    @Test
    fun `related mapper leaves a lockup without a view part unknown`() {
        val response = watchMetadataResponse(
            WatchMetadataResponse.SecondaryItem(
                lockupViewModel = lockup(videoId = "lockup-video", metadataTexts = listOf("Flow Channel", "Premieres soon"))
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related.single().viewCount).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    private fun watchMetadataResponse(
        video: WatchMetadataResponse.CompactVideo
    ) = watchMetadataResponse(WatchMetadataResponse.SecondaryItem(compactVideoRenderer = video))

    private fun lockup(
        videoId: String,
        metadataTexts: List<String>,
    ) = WatchMetadataResponse.LockupViewModel(
        contentId = videoId,
        contentType = "LOCKUP_CONTENT_TYPE_VIDEO",
        metadata = WatchMetadataResponse.LockupMetadataWrap(
            lockupMetadataViewModel = WatchMetadataResponse.LockupMetadataViewModel(
                title = WatchMetadataResponse.LockupText(content = "Lockup Video"),
                metadata = WatchMetadataResponse.LockupContentMetadataWrap(
                    contentMetadataViewModel = WatchMetadataResponse.ContentMetadataViewModel(
                        metadataRows = metadataTexts.map { text ->
                            WatchMetadataResponse.MetadataRow(
                                metadataParts = listOf(
                                    WatchMetadataResponse.MetadataPart(text = WatchMetadataResponse.LockupText(content = text))
                                )
                            )
                        }
                    )
                )
            )
        )
    )

    private fun watchMetadataResponse(
        item: WatchMetadataResponse.SecondaryItem
    ) = WatchMetadataResponse(
        contents = WatchMetadataResponse.Contents(
            twoColumnWatchNextResults = WatchMetadataResponse.TwoColumn(
                secondaryResults = WatchMetadataResponse.SecondaryWrap(
                    secondaryResults = WatchMetadataResponse.SecondaryInner(
                        results = listOf(item)
                    )
                )
            )
        )
    )

    private fun compactVideo(
        videoId: String,
        channelId: String,
    ) = WatchMetadataResponse.CompactVideo(
        videoId = videoId,
        title = WatchMetadataResponse.SimpleText(simpleText = "Related Video"),
        longBylineText = WatchMetadataResponse.Runs(
            runs = listOf(
                WatchMetadataResponse.Runs.Run(
                    text = "Flow Channel",
                    navigationEndpoint = WatchMetadataResponse.NavEndpoint(
                        browseEndpoint = WatchMetadataResponse.NavEndpoint.BrowseEndpoint(
                            browseId = channelId
                        )
                    )
                )
            )
        ),
    )
}
