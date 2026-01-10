package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.HeroCardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.request.GetItemsRequest

class FeaturedListRow(adapter: ObjectAdapter) : ListRow(null, adapter)

class HomeFragmentFeaturedRow(
    private val section: HomeSection,
    private val query: GetItemsRequest,
    private val onPlay: (BaseItemDto) -> Unit,
) : HomeFragmentRow {
    override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
        val heroPresenter = HeroCardPresenter(section.title, onPlay)
        val rowAdapter = ItemRowAdapter(
            context,
            query,
            0,
            true,
            true,
            heroPresenter,
            rowsAdapter,
        )
        rowAdapter.setReRetrieveTriggers(arrayOf(ChangeTriggerType.LibraryUpdated))

        val row = FeaturedListRow(rowAdapter)
        rowAdapter.setRow(row)
        rowAdapter.Retrieve()
        rowsAdapter.add(0, row)
    }
}
