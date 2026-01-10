package org.jellyfin.androidtv.ui.browsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType

class LibraryBrowseFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val folder = rememberFolder()
        val activeButton = when (folder?.collectionType) {
            CollectionType.MOVIES -> MainToolbarActiveButton.Movies
            CollectionType.TVSHOWS -> MainToolbarActiveButton.Shows
            CollectionType.BOXSETS -> MainToolbarActiveButton.Collections
            else -> MainToolbarActiveButton.None
        }

        JellyfinTheme {
            Column {
                MainToolbar(activeButton)
                AndroidFragment<BrowseGridFragment>(
                    modifier = Modifier.fillMaxSize(),
                    arguments = arguments,
                )
            }
        }
    }

    @Composable
    private fun rememberFolder(): BaseItemDto? {
        val folderJson = arguments?.getString(Extras.Folder) ?: return null
        return remember(folderJson) { Json.decodeFromString<BaseItemDto>(folderJson) }
    }
}
