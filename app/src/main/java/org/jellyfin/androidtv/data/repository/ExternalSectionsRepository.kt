package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.androidtv.data.model.ExternalMediaType
import org.jellyfin.androidtv.data.model.ExternalSection
import org.jellyfin.androidtv.data.model.ExternalSectionItem
import org.jellyfin.androidtv.data.model.ExternalSectionType
import org.jellyfin.androidtv.util.sdk.isUsable
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import java.util.UUID

interface ExternalSectionsRepository {
    val isConfigured: Boolean
    val lastError: String?

    suspend fun loadHomeSections(): List<ExternalSection>
    suspend fun loadDiscoverSections(): List<ExternalSection>
    suspend fun requestItem(item: ExternalSectionItem): Boolean
}

class ExternalSectionsRepositoryImpl(
    private val api: ApiClient,
    okHttpFactory: OkHttpFactory,
    httpClientOptions: HttpClientOptions,
) : ExternalSectionsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = okHttpFactory.createClient(httpClientOptions)
    @Volatile
    private var lastErrorMessage: String? = null
    @Volatile
    private var pluginCapabilities: PluginCapabilities? = null

    override val isConfigured: Boolean
        get() = pluginCapabilities?.jellyseerrConfigured ?: false
    override val lastError: String?
        get() = lastErrorMessage

    override suspend fun loadHomeSections(): List<ExternalSection> = withContext(Dispatchers.IO) {
        val capabilities = ensureCapabilities() ?: return@withContext emptyList()
        val sections = mutableListOf<ExternalSection>()
        if (capabilities.jellyseerrConfigured) {
            sections += loadPluginSections("Discover")
        }
        if (capabilities.radarrConfigured || capabilities.sonarrConfigured) {
            sections += loadPluginSections("Upcoming")
        }
        sections
    }

    override suspend fun loadDiscoverSections(): List<ExternalSection> = withContext(Dispatchers.IO) {
        val capabilities = ensureCapabilities() ?: return@withContext emptyList()
        if (!capabilities.jellyseerrConfigured) {
            return@withContext emptyList()
        }
        loadPluginSections("Discover")
    }

    override suspend fun requestItem(item: ExternalSectionItem): Boolean = withContext(Dispatchers.IO) {
        val capabilities = ensureCapabilities() ?: return@withContext false
        if (!capabilities.jellyseerrConfigured) {
            return@withContext false
        }

        val baseUrl = pluginBaseUrl() ?: return@withContext false
        val mediaId = item.id.toIntOrNull() ?: return@withContext false
        val mediaType = when (item.mediaType) {
            ExternalMediaType.MOVIE -> "movie"
            ExternalMediaType.SHOW -> "tv"
            else -> return@withContext false
        }

        val url = pluginUrl(baseUrl, "Request")
        val payload = buildJsonObject {
            put("mediaType", JsonPrimitive(mediaType))
            put("mediaId", JsonPrimitive(mediaId))
        }

        val request = Request.Builder()
            .url(url)
            .headers(jellyfinHeaders())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching { client.newCall(request).execute() }
            .getOrNull()
            ?.use { response -> response.isSuccessful }
            ?: false
    }

    private suspend fun ensureCapabilities(): PluginCapabilities? {
        val baseUrl = pluginBaseUrl() ?: run {
            pluginCapabilities = null
            return null
        }
        val url = pluginUrl(baseUrl, "Capabilities")
        val payload = fetchJson(url, jellyfinHeaders()) ?: run {
            pluginCapabilities = null
            return null
        }
        val root = payload.jsonObject

        val capabilities = PluginCapabilities(
            jellyseerrConfigured = root.bool("jellyseerrConfigured") == true,
            radarrConfigured = root.bool("radarrConfigured") == true,
            sonarrConfigured = root.bool("sonarrConfigured") == true,
        )
        pluginCapabilities = capabilities
        return capabilities
    }

    private fun loadPluginSections(path: String): List<ExternalSection> {
        val baseUrl = pluginBaseUrl() ?: return emptyList()
        val payload = fetchJson(pluginUrl(baseUrl, path), jellyfinHeaders()) ?: return emptyList()
        return parsePluginSections(payload)
    }

    private fun fetchJson(url: HttpUrl, headers: Headers): JsonElement? {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return null
        response.use { result ->
            if (!result.isSuccessful) {
                lastErrorMessage = parseErrorMessage(result.body?.string())
                return null
            }
            val body = result.body?.string()?.trim().orEmpty()
            if (body.isBlank()) return null
            val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
            if (parsed != null) {
                lastErrorMessage = null
            }
            return parsed
        }
    }

    private fun parseUuid(value: String?): UUID? = try {
        if (value.isNullOrBlank()) null else UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parsePluginSections(payload: JsonElement): List<ExternalSection> {
        val array = payload as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.string("id") ?: return@mapNotNull null
            val type = parseSectionType(obj.string("type")) ?: return@mapNotNull null
            val items = obj["items"]?.jsonArray?.mapNotNull { itemElement ->
                val item = itemElement.jsonObject
                val itemId = item.string("id") ?: return@mapNotNull null
                val name = item.string("name") ?: return@mapNotNull null
                val mediaType = parseMediaType(item.string("mediaType"))

                ExternalSectionItem(
                    id = itemId,
                    name = name,
                    year = item.int("year"),
                    overview = item.string("overview"),
                    posterUrl = item.string("posterUrl"),
                    backdropUrl = item.string("backdropUrl"),
                    mediaType = mediaType,
                    inLibrary = item.bool("inLibrary") ?: false,
                    requestable = item.bool("requestable") ?: false,
                    jellyfinId = parseUuid(item.string("jellyfinId")),
                )
            } ?: emptyList()

            val sectionItems = if (type == ExternalSectionType.UPCOMING_MOVIES || type == ExternalSectionType.UPCOMING_SHOWS) {
                items.filterNot { it.inLibrary }
            } else {
                items
            }

            ExternalSection(
                id = id,
                title = obj.string("title"),
                type = type,
                items = sectionItems,
            )
        }
    }

    private fun parseSectionType(value: String?): ExternalSectionType? = when (value?.uppercase()) {
        "DISCOVER_MOVIES" -> ExternalSectionType.DISCOVER_MOVIES
        "DISCOVER_SHOWS" -> ExternalSectionType.DISCOVER_SHOWS
        "MY_REQUESTS" -> ExternalSectionType.MY_REQUESTS
        "UPCOMING_MOVIES" -> ExternalSectionType.UPCOMING_MOVIES
        "UPCOMING_SHOWS" -> ExternalSectionType.UPCOMING_SHOWS
        "CUSTOM" -> ExternalSectionType.CUSTOM
        else -> null
    }

    private fun parseMediaType(value: String?): ExternalMediaType = when (value?.uppercase()) {
        "MOVIE" -> ExternalMediaType.MOVIE
        "SHOW", "TV" -> ExternalMediaType.SHOW
        else -> ExternalMediaType.UNKNOWN
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun pluginBaseUrl(): HttpUrl? {
        if (!api.isUsable) return null
        val baseUrl = api.baseUrl?.trim()?.trimEnd('/') ?: return null
        return baseUrl.toHttpUrlOrNull()
    }

    private fun pluginUrl(baseUrl: HttpUrl, path: String): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegments("Plugins/AndroidTvSeerAar/External")
            .addPathSegments(path)
            .build()
    }

    private fun jellyfinHeaders(): Headers {
        val accessToken = api.accessToken ?: return Headers.headersOf()
        val header = AuthorizationHeaderBuilder.buildHeader(
            api.clientInfo.name,
            api.clientInfo.version,
            api.deviceInfo.id,
            api.deviceInfo.name,
            accessToken,
        )
        return Headers.headersOf(
            "Authorization", header,
            "X-Emby-Authorization", header,
            "X-Emby-Token", accessToken,
        )
    }

    private data class PluginCapabilities(
        val jellyseerrConfigured: Boolean,
        val radarrConfigured: Boolean,
        val sonarrConfigured: Boolean,
    )

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun parseErrorMessage(body: String?): String? {
        val payload = body?.trim().orEmpty()
        if (payload.isBlank()) return "External services error."
        val parsed = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
        return parsed?.string("message") ?: "External services error."
    }
}
