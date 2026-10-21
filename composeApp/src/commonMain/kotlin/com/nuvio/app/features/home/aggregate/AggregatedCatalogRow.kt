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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
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
    state: AggregatedCatalogUiState,
    modifier: Modifier = Modifier,
    sectionPadding: Dp,
    onViewAllClick: (() -> Unit)? = null,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    // Adult titles stay reachable via the See-All 18+ filter but never lead on Home.
    val content = state.items.filterNot { it.isAdult }
    val busy = state.isLoading || state.isEnriching
    if (content.isEmpty() && !busy) return
    val previewLimit = if (isDesktop) ROW_PREVIEW_LIMIT_DESKTOP else ROW_PREVIEW_LIMIT_MOBILE
    val entries = content.take(previewLimit)
    val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
    val title = stringResource(Res.string.aggregated_latest_title)

    Column(modifier = modifier.fillMaxWidth()) {
        if (busy) {
            StatusBanner(
                title = title,
                state = state,
                sectionPadding = sectionPadding,
            )
        }
        if (content.isNotEmpty()) {
            NuvioShelfSection(
                title = title,
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
        } else {
            if (!busy) return
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sectionPadding)
                    .padding(bottom = 8.dp),
            )
            com.nuvio.app.features.home.components.HomeSkeletonRow(
                horizontalPadding = sectionPadding,
            )
        }
    }
}

/** Title + progress + caption so the row's processing state is always obvious. */
@Composable
private fun StatusBanner(
    title: String,
    state: AggregatedCatalogUiState,
    sectionPadding: Dp,
) {
    val determinate = state.isEnriching && state.enrichTotal > 0
    val caption = when {
        state.isEnriching && state.enrichTotal > 0 ->
            stringResource(Res.string.aggregated_enrich_progress, state.enrichResolved, state.enrichTotal)
        state.items.isEmpty() -> stringResource(Res.string.aggregated_status_fetching)
        else -> stringResource(Res.string.aggregated_status_refreshing)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sectionPadding)
            .padding(bottom = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
    }
    if (determinate) {
        LinearProgressIndicator(
            progress = { (state.enrichResolved.toFloat() / state.enrichTotal).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionPadding)
                .padding(bottom = 4.dp),
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionPadding)
                .padding(bottom = 4.dp),
        )
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
