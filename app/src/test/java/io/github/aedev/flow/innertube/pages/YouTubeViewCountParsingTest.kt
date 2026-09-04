package io.github.aedev.flow.innertube.pages

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.UNKNOWN_VIEW_COUNT
import org.junit.Test

class YouTubeViewCountParsingTest {
    @Test
    fun `exact and abbreviated english labels parse to numbers`() {
        assertThat(parseYouTubeViewCount("331,224,211 views")).isEqualTo(331_224_211L)
        assertThat(parseYouTubeViewCount("68M views")).isEqualTo(68_000_000L)
        assertThat(parseYouTubeViewCount("1.2K views")).isEqualTo(1_200L)
        assertThat(parseYouTubeViewCount("12 views")).isEqualTo(12L)
    }

    @Test
    fun `a missing label is unknown rather than zero`() {
        assertThat(parseYouTubeViewCount(null)).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(parseYouTubeViewCount("")).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(parseYouTubeViewCount("   ")).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    @Test
    fun `a label without any number is unknown`() {
        // Premieres, members-only items and localized wording YouTube renders with no digits.
        assertThat(parseYouTubeViewCount("Premieres soon")).isEqualTo(UNKNOWN_VIEW_COUNT)
        assertThat(parseYouTubeViewCount("Recommended for you")).isEqualTo(UNKNOWN_VIEW_COUNT)
    }

    @Test
    fun `only the explicit no-views label is zero`() {
        assertThat(parseYouTubeViewCount("No views")).isEqualTo(0L)
        assertThat(parseYouTubeViewCount("0 views")).isEqualTo(0L)
    }

    @Test
    fun `localized labels that carry digits still parse`() {
        assertThat(parseYouTubeViewCount("1.234 Aufrufe")).isEqualTo(1_234L)
        assertThat(parseYouTubeViewCount("12 345 vues")).isEqualTo(12_345L)
    }
}
