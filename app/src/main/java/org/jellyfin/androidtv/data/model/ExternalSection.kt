package org.jellyfin.androidtv.data.model

enum class ExternalMediaType {
    MOVIE,
    SHOW,
    UNKNOWN,
}

enum class ExternalSectionType {
    DISCOVER_MOVIES,
    DISCOVER_SHOWS,
    MY_REQUESTS,
    UPCOMING_MOVIES,
    UPCOMING_SHOWS,
    CUSTOM,
}

data class ExternalSectionItem(
    val id: String,
    val name: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaType: ExternalMediaType,
    val inLibrary: Boolean,
    val requestable: Boolean,
)

data class ExternalSection(
    val id: String,
    val title: String?,
    val type: ExternalSectionType,
    val items: List<ExternalSectionItem>,
)
