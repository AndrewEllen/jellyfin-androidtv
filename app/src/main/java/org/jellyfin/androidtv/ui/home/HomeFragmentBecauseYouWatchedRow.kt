package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeFragmentBecauseYouWatchedRow(
    private val lifecycleScope: LifecycleCoroutineScope,
) : HomeFragmentRow, KoinComponent {
    private val api by inject<ApiClient>()

    override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val lastPlayed = runCatching {
                api.itemsApi.getItems(
                    GetItemsRequest(
                        fields = ItemRepository.itemFields,
                        includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        mediaTypes = setOf(MediaType.VIDEO),
                        filters = setOf(ItemFilter.IS_PLAYED),
                        sortBy = setOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = setOf(SortOrder.DESCENDING),
                        limit = 1,
                        enableTotalRecordCount = false,
                    )
                ).content.items.firstOrNull()
            }.getOrNull() ?: return@launch

            val queryType = if (lastPlayed.type == BaseItemKind.MOVIE) QueryType.SimilarMovies else QueryType.SimilarSeries
            val similarQuery = GetSimilarItemsRequest(
                itemId = lastPlayed.id,
                fields = ItemRepository.itemFields,
                limit = ITEM_LIMIT,
            )

            withContext(Dispatchers.Main) {
                val rowAdapter = ItemRowAdapter(
                    context,
                    similarQuery,
                    queryType,
                    cardPresenter,
                    rowsAdapter,
                )
                rowAdapter.setReRetrieveTriggers(arrayOf(ChangeTriggerType.MoviePlayback, ChangeTriggerType.TvPlayback))

                val row = ListRow(HeaderItem(context.getString(R.string.home_because_you_watched)), rowAdapter)
                rowAdapter.setRow(row)
                rowAdapter.Retrieve()
                rowsAdapter.add(row)
            }
        }
    }

    companion object {
        private const val ITEM_LIMIT = 30
    }
}
