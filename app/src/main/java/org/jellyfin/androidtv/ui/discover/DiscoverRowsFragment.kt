package org.jellyfin.androidtv.ui.discover

import android.os.Bundle
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalSection
import org.jellyfin.androidtv.data.model.ExternalSectionType
import org.jellyfin.androidtv.data.repository.ExternalSectionsRepository
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.ui.presentation.TextItemPresenter
import org.koin.android.ext.android.inject

class DiscoverRowsFragment : RowsSupportFragment() {
    private val externalSectionsRepository by inject<ExternalSectionsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = MutableObjectAdapter<Row>(PositionableListRowPresenter())

        lifecycleScope.launch(Dispatchers.IO) {
            val sections = externalSectionsRepository.loadDiscoverSections()

            withContext(Dispatchers.Main) {
                if (sections.isEmpty()) {
                    addPlaceholderRow()
                } else {
                    sections.forEach { section ->
                        addSectionRow(section)
                    }
                }
            }
        }
    }

    private fun addPlaceholderRow() {
        val rowAdapter = ArrayObjectAdapter(TextItemPresenter())
        rowAdapter.add(getString(R.string.discover_requires_jellyseerr))

        val row = ListRow(HeaderItem(getString(R.string.lbl_discover)), rowAdapter)
        (adapter as? MutableObjectAdapter<Row>)?.add(row)
    }

    private fun addSectionRow(section: ExternalSection) {
        val title = section.title?.takeIf { it.isNotBlank() } ?: resolveTitle(section)
        val rowAdapter = ArrayObjectAdapter(TextItemPresenter())

        if (section.items.isEmpty()) {
            rowAdapter.add(getString(R.string.discover_requires_jellyseerr))
        } else {
            section.items.forEach { item ->
                rowAdapter.add(item.name)
            }
        }

        (adapter as? MutableObjectAdapter<Row>)?.add(ListRow(HeaderItem(title), rowAdapter))
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
