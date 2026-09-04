package io.github.aedev.flow.data.model

/**
 * The sentinel for "the source did not tell us" in [Video.viewCount].
 *
 * The cards hide the views line for negative counts and print `0 views` for zero, and
 * NewPipe already reports -1 for premieres and creator-hidden counts, so any producer that
 * defaults an absent count to 0 turns a gap into a wrong number.
 */
const val UNKNOWN_VIEW_COUNT = -1L

fun Long?.orUnknownViewCount(): Long = this ?: UNKNOWN_VIEW_COUNT

/** Keeps NewPipe's -1 rather than clamping it to a literal zero. */
fun Long.asKnownOrUnknownViewCount(): Long = if (this >= 0L) this else UNKNOWN_VIEW_COUNT

/**
 * Picks the count to keep when the same video arrives from several sources. Unknown values
 * never beat a known one, so an RSS row that lacks `<media:statistics>` cannot erase the count
 * a channel tab or the player already supplied.
 */
fun Iterable<Long>.bestKnownViewCount(): Long = filter { it >= 0L }.maxOrNull() ?: UNKNOWN_VIEW_COUNT

fun bestKnownViewCount(
    first: Long,
    second: Long,
): Long = listOf(first, second).bestKnownViewCount()
