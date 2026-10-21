package com.nuvio.app.features.home.aggregate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.NuvioPosterCard
import com.nuvio.app.core.ui.NuvioPosterShape
import com.nuvio.app.core.ui.catalogPosterBaseWidthDp
import com.nuvio.app.navigation.LocalUseNativeNavigation
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Full "See all" grid for the aggregated Latest Additions row. */
@Composable
fun AggregatedCatalogScreen(
    onBack: () -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by AggregatedCatalogRepository.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs()
    val posterCardStyle = com.nuvio.app.core.ui.rememberPosterCardStyleUiState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val pageHorizontalPadding = if (isDesktop) {
            desktopPageHorizontalPaddingForWidth(maxWidth.value)
        } else {
            16.dp
        }
        val posterBaseWidth = catalogPosterBaseWidthDp(posterCardStyle.widthDp)

        LazyVerticalGrid(
            columns = GridCells.FixedSize(posterBaseWidth.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = pageHorizontalPadding,
                end = pageHorizontalPadding,
                bottom = nuvioSafeBottomPadding(28.dp),
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "aggregated_header", span = { GridItemSpan(maxLineSpan) }) {
                AggregatedCatalogHeader(
                    sourceCount = uiState.sourceCount,
                    onBack = onBack,
                )
            }
            items(
                items = uiState.items,
                key = { item -> item.matchKey },
            ) { item ->
                AggregatedGridCard(
                    item = item,
                    nowEpochMs = nowEpochMs,
                    onClick = { onPosterClick(item.preview) },
                )
            }
        }

        if (isDesktop) {
            NuvioDesktopVerticalScrollbar(
                state = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun AggregatedCatalogHeader(
    sourceCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalUseNativeNavigation.current) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(44.dp),
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .then(if (isDesktop) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .padding(top = if (isDesktop) 32.dp else 52.dp, bottom = 12.dp),
    ) {
        NuvioBackButton(
            onClick = onBack,
            modifier = Modifier.size(if (isDesktop) 48.dp else 40.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            iconSize = 24.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.aggregated_latest_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.aggregated_latest_subtitle, sourceCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AggregatedGridCard(
    item: AggregatedCatalogItem,
    nowEpochMs: Long,
    onClick: () -> Unit,
) {
    val preview = item.preview
    NuvioPosterCard(
        title = preview.name,
        imageUrl = preview.poster,
        fallbackImageUrl = preview.rawPosterUrl,
        basePosterWidthDp = null,
        shape = preview.posterShape.toAggregatedPosterShape(),
        detailLine = addedSourceLine(item = item, nowEpochMs = nowEpochMs),
        showTitleBelow = true,
        isWatched = false,
        onClick = onClick,
    )
}

@Composable
internal fun addedSourceLine(item: AggregatedCatalogItem, nowEpochMs: Long): String {
    val days = item.relativeAddedDays(nowEpochMs)
    val addedLabel = when {
        days <= 0L -> stringResource(Res.string.aggregated_added_today)
        days == 1L -> stringResource(Res.string.aggregated_added_yesterday)
        days <= 7L -> stringResource(Res.string.aggregated_added_days_ago, days.toInt())
        else -> stringResource(Res.string.aggregated_added_on_date, item.firstSeenEpochMs.toIsoDateLabel())
    }
    val sourcesLabel = if (item.sourceCount == 1) {
        stringResource(Res.string.aggregated_sources_count_one)
    } else {
        stringResource(Res.string.aggregated_sources_count, item.sourceCount)
    }
    return "$addedLabel · $sourcesLabel"
}

private fun PosterShape.toAggregatedPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }
