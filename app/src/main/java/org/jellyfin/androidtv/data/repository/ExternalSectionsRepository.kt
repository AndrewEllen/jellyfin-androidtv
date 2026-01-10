package org.jellyfin.androidtv.data.repository

import org.jellyfin.androidtv.data.model.ExternalSection

interface ExternalSectionsRepository {
    val isConfigured: Boolean

    suspend fun loadHomeSections(): List<ExternalSection>
    suspend fun loadDiscoverSections(): List<ExternalSection>
}

class ExternalSectionsRepositoryImpl : ExternalSectionsRepository {
    override val isConfigured: Boolean = false

    override suspend fun loadHomeSections(): List<ExternalSection> = emptyList()

    override suspend fun loadDiscoverSections(): List<ExternalSection> = emptyList()
}
