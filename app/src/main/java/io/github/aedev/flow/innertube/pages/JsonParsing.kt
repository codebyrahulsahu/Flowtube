package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.UNKNOWN_VIEW_COUNT
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.schabi.newpipe.extractor.utils.Utils

internal fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

/** Reads the primitive and structured text shapes used by YouTube renderers and entity payloads. */
internal fun JsonElement?.youtubeText(): String? {
    stringOrNull()?.let { return it }
    val value = objectOrNull() ?: return null
    value["simpleText"].stringOrNull()?.let { return it }
    value["content"].stringOrNull()?.let { return it }
    return value["runs"].arrayOrNull()
        ?.joinToString("") { run ->
            val runValue = run.objectOrNull()
            runValue?.get("text").stringOrNull()
                ?: runValue?.get("content").stringOrNull().orEmpty()
        }
        ?.takeIf { it.isNotBlank() }
}

/**
 * Parses a YouTube view-count label ("1,234 views", "68M views", "No views", "12 Mio. Aufrufe").
 *
 * Returns [UNKNOWN_VIEW_COUNT] when the label is missing or carries no number; 0 is reserved for
 * the label YouTube renders for a genuinely unwatched video (NewPipe draws the same line). Callers
 * used to coerce every miss to 0, which the cards then printed as a literal `0 views`.
 */
internal fun parseYouTubeViewCount(text: String?): Long {
    if (text.isNullOrBlank()) return UNKNOWN_VIEW_COUNT
    val hasAbbreviatedSuffix = Regex("""\d[\d.,]*\s*[KkMmBb]\b""").containsMatchIn(text)
    val exactDigits = Utils.removeNonDigitCharacters(text)
    if (exactDigits.isEmpty()) {
        return if (text.contains("no views", ignoreCase = true)) 0L else UNKNOWN_VIEW_COUNT
    }
    if (!hasAbbreviatedSuffix && exactDigits.length >= 4) {
        exactDigits.toLongOrNull()?.let { return it }
    }
    return runCatching { Utils.mixedNumberWordToLong(text) }
        .getOrElse {
            val match = Regex("""([\d.,]+)\s*([KkMmBb])?""").find(text) ?: return UNKNOWN_VIEW_COUNT
            val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return UNKNOWN_VIEW_COUNT
            val multiplier = when (match.groupValues[2].lowercase()) {
                "k" -> 1_000.0
                "m" -> 1_000_000.0
                "b" -> 1_000_000_000.0
                else -> 1.0
            }
            (number * multiplier).toLong()
        }
}
