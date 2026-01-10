package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalSection
import org.jellyfin.androidtv.data.model.ExternalSectionItem
import org.jellyfin.androidtv.data.model.ExternalSectionType
import org.jellyfin.androidtv.data.repository.ExternalSectionsRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.ExternalCardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.ui.presentation.TextItemPresenter
import org.jellyfin.androidtv.util.Utils
import org.koin.android.ext.android.inject

class DiscoverRowsFragment : RowsSupportFragment() {
    private val externalSectionsRepository by inject<ExternalSectionsRepository>()
    private val navigationRepository by inject<NavigationRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())
        onItemViewClickedListener = ExternalItemClickedListener()

        lifecycleScope.launch(Dispatchers.IO) {
            val sections = externalSectionsRepository.loadDiscoverSections()

            withContext(Dispatchers.Main) {
                if (sections.isEmpty()) {
                    val message = if (externalSectionsRepository.isConfigured) {
                        getString(R.string.lbl_no_items)
                    } else {
                        getString(R.string.discover_requires_jellyseerr)
                    }
                    addPlaceholderRow(message)
                } else {
                    sections.forEach { section ->
                        addSectionRow(section)
                    }
                }
            }
        }
    }

    private fun addPlaceholderRow(message: String) {
        val rowAdapter = ArrayObjectAdapter(TextItemPresenter())
        rowAdapter.add(message)

        val row = ListRow(HeaderItem(getString(R.string.lbl_discover)), rowAdapter)
        (adapter as? MutableObjectAdapter<Row>)?.add(row)
    }

    private fun addSectionRow(section: ExternalSection) {
        val title = section.title?.takeIf { it.isNotBlank() } ?: resolveTitle(section)

        if (section.items.isEmpty()) {
            val emptyAdapter = ArrayObjectAdapter(TextItemPresenter())
            emptyAdapter.add(getString(R.string.lbl_no_items))
            (adapter as? MutableObjectAdapter<Row>)?.add(ListRow(HeaderItem(title), emptyAdapter))
            return
        }

        val rowAdapter = ArrayObjectAdapter(ExternalCardPresenter())
        section.items.forEach { item ->
            rowAdapter.add(item)
        }

        (adapter as? MutableObjectAdapter<Row>)?.add(ListRow(HeaderItem(title), rowAdapter))
    }


    private inner class ExternalItemClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder?,
            row: Row?,
        ) {
            val externalItem = item as? ExternalSectionItem ?: return

            externalItem.jellyfinId?.let { jellyfinId ->
                navigationRepository.navigate(Destinations.itemDetails(jellyfinId))
                return
            }

            if (!externalItem.requestable) {
                Utils.showToast(requireContext(), R.string.external_item_unavailable)
                return
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val success = externalSectionsRepository.requestItem(externalItem)
                val message = if (success) R.string.external_request_success else R.string.external_request_failed
                withContext(Dispatchers.Main) {
                    Utils.showToast(requireContext(), message)
                }
            }
        }
    }

    private fun resolveTitle(section: ExternalSection): String {
        return when (section.type) {
            ExternalSectionType.DISCOVER_MOVIES -> getString(R.string.home_discover_movies)
            ExternalSectionType.DISCOVER_SHOWS -> getString(R.string.home_discover_shows)
            ExternalSectionType.MY_REQUESTS -> getString(R.string.home_my_requests)
            ExternalSectionType.UPCOMING_MOVIES -> getString(R.string.home_upcoming_movies)
            ExternalSectionType.UPCOMING_SHOWS -> getString(R.string.home_upcoming_shows)
            ExternalSectionType.CUSTOM -> section.title ?: getString(R.string.lbl_discover)
        }
    }
}
