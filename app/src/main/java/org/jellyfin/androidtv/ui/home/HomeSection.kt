package org.jellyfin.androidtv.ui.home

import org.jellyfin.sdk.model.api.BaseItemDto

enum class HomeSectionKind {
    FEATURED,
    CONTINUE_WATCHING,
    NEXT_UP,
    LATEST_MOVIES,
    LATEST_SHOWS,
    CUSTOM,
}

data class HomeSection(
    val id: String,
    val title: String?,
    val type: HomeSectionKind,
    val items: List<BaseItemDto>,
)
