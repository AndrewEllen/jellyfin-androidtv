package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeFragmentGenreRecommendationsRow(
    private val lifecycleScope: LifecycleCoroutineScope,
) : HomeFragmentRow, KoinComponent {
    private val api by inject<ApiClient>()

    override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val genres = runCatching {
                api.genresApi.getGenres(sortBy = setOf(ItemSortBy.SORT_NAME)).content.items
            }.getOrNull().orEmpty()
                .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
                .shuffled()
                .take(GENRE_ROW_COUNT)

            if (genres.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                for (genre in genres) {
                    val request = GetItemsRequest(
                        fields = ItemRepository.itemFields,
                        includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        genres = setOf(genre),
                        recursive = true,
                        sortBy = setOf(ItemSortBy.RANDOM),
                        startIndex = 0,
                        limit = ITEM_LIMIT,
                        imageTypeLimit = 1,
                        enableTotalRecordCount = false,
                    )

                    val rowDef = BrowseRowDef(
                        genre,
                        request,
                        0,
                        false,
                        false,
                        arrayOf(ChangeTriggerType.LibraryUpdated),
                    )

                    HomeFragmentBrowseRowDefRow(rowDef).addToRowsAdapter(context, cardPresenter, rowsAdapter)
                }
            }
        }
    }

    companion object {
        private const val GENRE_ROW_COUNT = 3
        private const val ITEM_LIMIT = 30
    }
}
