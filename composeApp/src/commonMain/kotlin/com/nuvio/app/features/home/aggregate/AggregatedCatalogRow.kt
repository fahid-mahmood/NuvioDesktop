package com.nuvio.app.features.home.aggregate

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.NuvioViewAllPillSize
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val ROW_PREVIEW_LIMIT_DESKTOP = 40
private const val ROW_PREVIEW_LIMIT_MOBILE = 18

/** The pinned "Latest Additions" shelf on the home screen. */
@Composable
fun AggregatedCatalogRowSection(
    items: List<AggregatedCatalogItem>,
    modifier: Modifier = Modifier,
    sectionPadding: Dp,
    isBusy: Boolean = false,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    // Adult titles stay reachable via the See-All 18+ filter but never lead on Home.
    val content = items.filterNot { it.isAdult }
    if (content.isEmpty()) return
    val previewLimit = if (isDesktop) ROW_PREVIEW_LIMIT_DESKTOP else ROW_PREVIEW_LIMIT_MOBILE
    val entries = content.take(previewLimit)
    val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()

    Column(modifier = modifier.fillMaxWidth()) {
        if (isBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sectionPadding)
                    .padding(bottom = 4.dp),
            )
        }
        NuvioShelfSection(
            title = stringResource(Res.string.aggregated_latest_title),
            entries = entries,
            modifier = Modifier.fillMaxWidth(),
            headerHorizontalPadding = sectionPadding,
            rowContentPadding = PaddingValues(horizontal = sectionPadding),
            onViewAllClick = onViewAllClick,
            onTitleClick = onViewAllClick?.takeIf { isDesktop },
            viewAllPillSize = NuvioViewAllPillSize.Compact,
            key = { item -> item.matchKey },
        ) { item ->
            AggregatedPosterCard(
                item = item,
                nowEpochMs = nowEpochMs,
                onClick = onPosterClick?.let { callback -> { callback(item.preview) } },
                onLongClick = onPosterLongClick?.let { callback -> { callback(item.preview) } },
            )
        }
    }
}


@Composable
private fun AggregatedPosterCard(
    item: AggregatedCatalogItem,
    nowEpochMs: Long,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val preview = item.preview
    val imageUrl = preview.poster
    NuvioPosterCard(
        title = preview.name,
        imageUrl = imageUrl,
        fallbackImageUrl = preview.rawPosterUrl,
        basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp),
        shape = preview.posterShape.toNuvioPosterShape(),
        detailLine = addedSourceLine(item = item, nowEpochMs = nowEpochMs),
        showTitleBelow = !posterCardStyle.hideLabelsEnabled,
        isWatched = false,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

private fun PosterShape.toNuvioPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
