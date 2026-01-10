package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalSection
import org.jellyfin.androidtv.data.model.ExternalSectionType
import org.jellyfin.androidtv.data.repository.ExternalSectionsRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.ExternalCardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.TextItemPresenter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HomeFragmentExternalSectionsRow(
    private val lifecycleScope: LifecycleCoroutineScope,
) : HomeFragmentRow, KoinComponent {
    private val externalSectionsRepository by inject<ExternalSectionsRepository>()

    override fun addToRowsAdapter(
        context: Context,
        cardPresenter: CardPresenter,
        rowsAdapter: MutableObjectAdapter<Row>
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val sections = externalSectionsRepository.loadHomeSections()
            if (sections.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                for (section in sections) {
                    addSectionRow(context, rowsAdapter, section)
                }
            }
        }
    }

    private fun addSectionRow(
        context: Context,
        rowsAdapter: MutableObjectAdapter<Row>,
        section: ExternalSection,
    ) {
        val title = section.title?.takeIf { it.isNotBlank() } ?: resolveTitle(context, section)

        if (section.items.isEmpty()) {
            val emptyAdapter = ArrayObjectAdapter(TextItemPresenter())
            emptyAdapter.add(context.getString(R.string.lbl_no_items))
            rowsAdapter.add(ListRow(HeaderItem(title), emptyAdapter))
            return
        }

        val rowAdapter = ArrayObjectAdapter(ExternalCardPresenter())
        section.items.forEach { item ->
            rowAdapter.add(item)
        }

        rowsAdapter.add(ListRow(HeaderItem(title), rowAdapter))
    }

    private fun resolveTitle(context: Context, section: ExternalSection): String {
        return when (section.type) {
            ExternalSectionType.DISCOVER_MOVIES -> context.getString(R.string.home_discover_movies)
            ExternalSectionType.DISCOVER_SHOWS -> context.getString(R.string.home_discover_shows)
            ExternalSectionType.MY_REQUESTS -> context.getString(R.string.home_my_requests)
            ExternalSectionType.UPCOMING_MOVIES -> context.getString(R.string.home_upcoming_movies)
            ExternalSectionType.UPCOMING_SHOWS -> context.getString(R.string.home_upcoming_shows)
            ExternalSectionType.CUSTOM -> section.title ?: context.getString(R.string.lbl_discover)
        }
    }
}
