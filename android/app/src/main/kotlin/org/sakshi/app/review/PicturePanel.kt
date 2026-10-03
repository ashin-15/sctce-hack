package org.sakshi.app.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.QuietTextButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.text
import org.sakshi.app.ui.theme.Spacing

private val INLINE_MAX_HEIGHT = 240.dp
private val HALO_WIDTH = 5.dp
private val OUTLINE_WIDTH = 2.5.dp
private const val MIN_ZOOM = 1
private const val MAX_ZOOM = 3

/**
 * The saved picture above the text read from it: a bounded preview with the lines the underlined words came from
 * outlined, a button to open it larger, and a note saying whether any outline could be placed.
 */
@Composable
fun PictureSection(state: EventReviewState, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader(stringResource(R.string.review_picture_heading))
        when (val picture = state.picture) {
            PictureState.None -> Unit
            PictureState.Loading -> SupportingText(stringResource(R.string.review_picture_loading))
            is PictureState.Ready -> {
                val ratio = picture.image.bitmap.width.toFloat() / picture.image.bitmap.height
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PictureWithOutlines(
                        picture,
                        Modifier.heightIn(max = INLINE_MAX_HEIGHT).aspectRatio(ratio).clickable(onClickLabel = stringResource(R.string.review_picture_open), onClick = onOpen),
                    )
                }
                OutlineCaption(state, picture)
                SecondaryButton(stringResource(R.string.review_picture_open), onOpen, Modifier.fillMaxWidth())
            }
            is PictureState.Unavailable -> StatusNote(NoteKind.Info, picture.message.text())
        }
    }
}

/** Says what the outlines mean, or that none could be placed. Outlines are never the only carrier of meaning. */
@Composable
private fun OutlineCaption(state: EventReviewState, picture: PictureState.Ready) {
    when {
        picture.outlines.isNotEmpty() -> SupportingText(stringResource(R.string.review_picture_outlined))
        state.marks.isNotEmpty() -> SupportingText(stringResource(R.string.review_picture_no_outline))
    }
}

/** The picture fitted into its box with each outline drawn over it, as a pale halo under a dark line so it shows on any picture. */
@Composable
private fun PictureWithOutlines(picture: PictureState.Ready, modifier: Modifier = Modifier) {
    val bitmap = picture.image.bitmap
    if (bitmap.isRecycled) return
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val description = stringResource(R.string.review_picture_description)
    val halo = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.primary
    Box(modifier) {
        Image(imageBitmap, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            picture.outlines.forEach { outline ->
                val corners = RegionGeometry.toView(outline.points, bitmap.width, bitmap.height, size.width, size.height)
                if (corners.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(corners.first().x, corners.first().y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, halo, style = Stroke(width = HALO_WIDTH.toPx(), join = StrokeJoin.Round))
                drawPath(path, ink, style = Stroke(width = OUTLINE_WIDTH.toPx(), join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * The same picture as the whole screen, wider than the screen when zoomed and scrolled in both directions. Zooming is
 * by buttons, so it works with TalkBack and without a pinch. Back closes it and returns to the review.
 */
@Composable
fun PictureLargeView(state: EventReviewState, picture: PictureState.Ready, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var zoom by rememberSaveable { mutableIntStateOf(MIN_ZOOM) }
    BackHandler(onBack = onClose)
    SakshiScaffold(stringResource(R.string.review_picture_heading), modifier, onBack = onClose) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(stringResource(R.string.review_picture_zoom_out), { zoom-- }, Modifier.weight(1f), enabled = zoom > MIN_ZOOM)
                SecondaryButton(stringResource(R.string.review_picture_zoom_in), { zoom++ }, Modifier.weight(1f), enabled = zoom < MAX_ZOOM)
            }
            OutlineCaption(state, picture)
            SupportingText(stringResource(R.string.review_picture_scroll_hint))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                val ratio = picture.image.bitmap.width.toFloat() / picture.image.bitmap.height
                val zoomedWidth = maxWidth * zoom
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        PictureWithOutlines(picture, Modifier.width(zoomedWidth).aspectRatio(ratio))
                    }
                }
            }
            QuietTextButton(stringResource(R.string.review_picture_close), onClose, Modifier.fillMaxWidth())
        }
    }
}
