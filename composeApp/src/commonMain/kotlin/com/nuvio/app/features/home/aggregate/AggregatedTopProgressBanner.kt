package com.nuvio.app.features.home.aggregate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.aggregated_enrich_progress
import nuvio.composeapp.generated.resources.aggregated_status_fetching
import nuvio.composeapp.generated.resources.aggregated_status_refreshing
import org.jetbrains.compose.resources.stringResource

/**
 * Slim overlay pinned to the top of Home while the Latest Additions pipeline runs,
 * so its state is visible regardless of scroll position. Renders nothing once done.
 */
@Composable
fun AggregatedTopProgressBanner(
    state: AggregatedCatalogUiState,
    modifier: Modifier = Modifier,
) {
    val busy = state.isLoading || state.isEnriching
    if (!busy) return

    val caption = when {
        state.isEnriching && state.enrichTotal > 0 ->
            stringResource(Res.string.aggregated_enrich_progress, state.enrichResolved, state.enrichTotal)
        state.items.isEmpty() -> stringResource(Res.string.aggregated_status_fetching)
        else -> stringResource(Res.string.aggregated_status_refreshing)
    }
    val determinate = state.isEnriching && state.enrichTotal > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f))
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (determinate) {
            LinearProgressIndicator(
                progress = { (state.enrichResolved.toFloat() / state.enrichTotal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
