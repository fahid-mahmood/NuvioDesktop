package com.nuvio.app.features.home.aggregate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    var selectedIndustry by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var selectedGenre by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val industries = androidx.compose.runtime.remember(uiState.items) {
        uiState.items.mapNotNull { it.industry }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key }
    }
    val genres = androidx.compose.runtime.remember(uiState.items) {
        uiState.items.flatMap { it.genres }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.isNotBlank() }
    }
    val visibleItems = androidx.compose.runtime.remember(uiState.items, selectedIndustry, selectedGenre) {
        uiState.items.filter { item ->
            (selectedIndustry == null || item.industry == selectedIndustry) &&
                (selectedGenre == null || item.genres.any { it.equals(selectedGenre, ignoreCase = true) })
        }
    }

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
            if (industries.isNotEmpty() || genres.isNotEmpty()) {
                item(key = "aggregated_filters", span = { GridItemSpan(maxLineSpan) }) {
                    AggregatedFilterBar(
                        industries = industries,
                        genres = genres,
                        selectedIndustry = selectedIndustry,
                        selectedGenre = selectedGenre,
                        onIndustrySelected = { selectedIndustry = if (selectedIndustry == it) null else it },
                        onGenreSelected = { selectedGenre = if (selectedGenre == it) null else it },
                    )
                }
            }
            items(
                items = visibleItems,
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
    val yearPrefix = item.releaseYear?.let { "$it · " }.orEmpty()
    return "$yearPrefix$addedLabel · $sourcesLabel"
}

/** Localized display label for an industry bucket key. */
@Composable
internal fun industryLabel(industry: String): String = when (industry) {
    INDUSTRY_BOLLYWOOD -> stringResource(Res.string.aggregated_industry_bollywood)
    INDUSTRY_HOLLYWOOD -> stringResource(Res.string.aggregated_industry_hollywood)
    INDUSTRY_SOUTH_INDIAN -> stringResource(Res.string.aggregated_industry_south_indian)
    INDUSTRY_KOREAN -> stringResource(Res.string.aggregated_industry_korean)
    INDUSTRY_ANIME -> stringResource(Res.string.aggregated_industry_anime)
    INDUSTRY_CHINESE -> stringResource(Res.string.aggregated_industry_chinese)
    else -> stringResource(Res.string.aggregated_industry_international)
}

private fun PosterShape.toAggregatedPosterShape(): NuvioPosterShape =
    when (this) {
        PosterShape.Poster -> NuvioPosterShape.Poster
        PosterShape.Square -> NuvioPosterShape.Square
        PosterShape.Landscape -> NuvioPosterShape.Landscape
    }


@Composable
private fun AggregatedFilterBar(
    industries: List<String>,
    genres: List<String>,
    selectedIndustry: String?,
    selectedGenre: String?,
    onIndustrySelected: (String) -> Unit,
    onGenreSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel = stringResource(Res.string.aggregated_filter_all)
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (industries.isNotEmpty()) {
            FilterChipRow {
                FilterChip(
                    selected = selectedIndustry == null,
                    onClick = { if (selectedIndustry != null) onIndustrySelected(selectedIndustry) },
                    label = { Text(allLabel) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
                industries.forEach { industry ->
                    FilterChip(
                        selected = selectedIndustry == industry,
                        onClick = { onIndustrySelected(industry) },
                        label = { Text(industryLabel(industry)) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
        if (genres.isNotEmpty()) {
            FilterChipRow(topPadding = 6.dp) {
                FilterChip(
                    selected = selectedGenre == null,
                    onClick = { if (selectedGenre != null) onGenreSelected(selectedGenre) },
                    label = { Text(allLabel) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
                genres.take(18).forEach { genre ->
                    FilterChip(
                        selected = selectedGenre.equals(genre, ignoreCase = true),
                        onClick = { onGenreSelected(genre) },
                        label = { Text(genre) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = topPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
