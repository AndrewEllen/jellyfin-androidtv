package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class HomeFragmentRecentlyAddedRow : HomeFragmentRow {
    override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
        val moviesRequest = GetItemsRequest(
            fields = ItemRepository.itemFields,
            includeItemTypes = setOf(BaseItemKind.MOVIE),
            recursive = true,
            sortBy = setOf(ItemSortBy.DATE_CREATED),
            sortOrder = setOf(SortOrder.DESCENDING),
            imageTypeLimit = 1,
            limit = ITEM_LIMIT,
            enableTotalRecordCount = false,
        )

        val showsRequest = GetItemsRequest(
            fields = ItemRepository.itemFields,
            includeItemTypes = setOf(BaseItemKind.SERIES),
            recursive = true,
            sortBy = setOf(ItemSortBy.DATE_CREATED),
            sortOrder = setOf(SortOrder.DESCENDING),
            imageTypeLimit = 1,
            limit = ITEM_LIMIT,
            enableTotalRecordCount = false,
        )

        val rows = listOf(
            BrowseRowDef(context.getString(R.string.home_recent_movies), moviesRequest, 0, false, false, arrayOf(ChangeTriggerType.LibraryUpdated)),
            BrowseRowDef(context.getString(R.string.home_recent_shows), showsRequest, 0, false, false, arrayOf(ChangeTriggerType.LibraryUpdated)),
        )

        rows.forEach { row ->
            HomeFragmentBrowseRowDefRow(row).addToRowsAdapter(context, cardPresenter, rowsAdapter)
        }
    }

    companion object {
        private const val ITEM_LIMIT = 50
    }
}
