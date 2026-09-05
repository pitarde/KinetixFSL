package com.example.kinetixfsl.community

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/** `#word` — letters, digits and underscore, at least 2 characters after the '#'. */
private val HASHTAG_REGEX = Regex("#([\\p{L}\\p{N}_]{2,50})")
private const val HASHTAG_TAG = "hashtag"

/**
 * Every #hashtag across [texts] (typically a post's title and body), lowercased
 * and de-duplicated in first-seen order, with the '#' stripped. Saved onto the
 * post at write time — see [CommunityRepository.createPost] and
 * `updatePost` — so the feed's search filter can match a tag exactly rather
 * than only ever substring-matching the raw text.
 */
internal fun extractHashtags(vararg texts: String): List<String> =
    texts.asSequence()
        .flatMap { HASHTAG_REGEX.findAll(it) }
        .map { it.groupValues[1].lowercase() }
        .distinct()
        .toList()

/**
 * Renders [text] with every #hashtag colored and bold, YouTube/TikTok-style.
 *
 * When [onHashtagClick] is null (most contexts — the post detail screen, the
 * immersive viewer, a profile's own post list, comments), this is a plain
 * [Text]: the hashtags still read as styled, but nothing intercepts the tap,
 * so it keeps behaving exactly like the body text always has — the ancestor
 * card's own `clickable` still opens the post.
 *
 * When [onHashtagClick] is set (the feed contexts that own a search bar), this
 * switches to [ClickableText] so a tap on a hashtag can open search for it.
 * [onBodyClick] is the fallback for a tap that lands on *non*-hashtag text —
 * without it, swapping in a tap-detecting ClickableText would silently swallow
 * the tap that used to bubble up to the card's own click (open the post).
 */
@Composable
internal fun HashtagText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onHashtagClick: ((String) -> Unit)? = null,
    onBodyClick: (() -> Unit)? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val hashtagColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, hashtagColor) {
        buildAnnotatedString {
            var last = 0
            HASHTAG_REGEX.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                pushStringAnnotation(HASHTAG_TAG, match.groupValues[1].lowercase())
                withStyle(SpanStyle(color = hashtagColor, fontWeight = FontWeight.Bold)) {
                    append(match.value)
                }
                pop()
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    if (onHashtagClick != null) {
        ClickableText(
            text = annotated,
            modifier = modifier,
            style = style.copy(color = color),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onTextLayout,
            onClick = { offset ->
                val hit = annotated.getStringAnnotations(HASHTAG_TAG, offset, offset).firstOrNull()
                if (hit != null) onHashtagClick(hit.item) else onBodyClick?.invoke()
            },
        )
    } else {
        Text(
            text = annotated,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onTextLayout,
            modifier = modifier,
        )
    }
}

/** Turns a hashtag search into the text the query box shows — "#tag". */
internal fun hashtagQueryText(tag: String): String = "#$tag"
