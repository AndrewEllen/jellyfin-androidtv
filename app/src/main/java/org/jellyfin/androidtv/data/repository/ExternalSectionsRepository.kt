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
import org.jellyfin.androidtv.preference.ExternalServicesPreferences
import org.jellyfin.sdk.api.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import java.time.LocalDate
import java.util.UUID

interface ExternalSectionsRepository {
    val isConfigured: Boolean

    suspend fun loadHomeSections(): List<ExternalSection>
    suspend fun loadDiscoverSections(): List<ExternalSection>
    suspend fun requestItem(item: ExternalSectionItem): Boolean
}

class ExternalSectionsRepositoryImpl(
    private val externalServicesPreferences: ExternalServicesPreferences,
    okHttpFactory: OkHttpFactory,
    httpClientOptions: HttpClientOptions,
) : ExternalSectionsRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = okHttpFactory.createClient(httpClientOptions)

    override val isConfigured: Boolean
        get() = hasJellyseerr() || hasRadarr() || hasSonarr()

    override suspend fun loadHomeSections(): List<ExternalSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<ExternalSection>()
        val jellyseerrBase = jellyseerrBaseUrl()
        if (jellyseerrBase != null) {
            loadJellyseerrDiscover(
                baseUrl = jellyseerrBase,
                type = ExternalSectionType.DISCOVER_MOVIES,
                path = "discover/movies",
                mediaType = ExternalMediaType.MOVIE,
            )?.let(sections::add)

            loadJellyseerrDiscover(
                baseUrl = jellyseerrBase,
                type = ExternalSectionType.DISCOVER_SHOWS,
                path = "discover/tv",
                mediaType = ExternalMediaType.SHOW,
            )?.let(sections::add)

            loadJellyseerrRequests(jellyseerrBase)?.let(sections::add)
        }

        loadSonarrUpcoming()?.let(sections::add)
        loadRadarrUpcoming()?.let(sections::add)

        sections
    }

    override suspend fun loadDiscoverSections(): List<ExternalSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<ExternalSection>()
        val jellyseerrBase = jellyseerrBaseUrl()
        if (jellyseerrBase != null) {
            loadJellyseerrDiscover(
                baseUrl = jellyseerrBase,
                type = ExternalSectionType.DISCOVER_MOVIES,
                path = "discover/movies",
                mediaType = ExternalMediaType.MOVIE,
            )?.let(sections::add)

            loadJellyseerrDiscover(
                baseUrl = jellyseerrBase,
                type = ExternalSectionType.DISCOVER_SHOWS,
                path = "discover/tv",
                mediaType = ExternalMediaType.SHOW,
            )?.let(sections::add)

            loadJellyseerrRequests(jellyseerrBase)?.let(sections::add)
        }

        sections
    }

    override suspend fun requestItem(item: ExternalSectionItem): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = jellyseerrBaseUrl() ?: return@withContext false
        val mediaId = item.id.toIntOrNull() ?: return@withContext false
        val mediaType = when (item.mediaType) {
            ExternalMediaType.MOVIE -> "movie"
            ExternalMediaType.SHOW -> "tv"
            else -> return@withContext false
        }

        val url = jellyseerrApiUrl(baseUrl, "request")
        val payload = buildJsonObject {
            put("mediaType", JsonPrimitive(mediaType))
            put("mediaId", JsonPrimitive(mediaId))
        }

        val request = Request.Builder()
            .url(url)
            .headers(jellyseerrHeaders())
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching { client.newCall(request).execute() }
            .getOrNull()
            ?.use { response -> response.isSuccessful }
            ?: false
    }

    private fun loadJellyseerrDiscover(
        baseUrl: HttpUrl,
        type: ExternalSectionType,
        path: String,
        mediaType: ExternalMediaType,
    ): ExternalSection? {
        val url = jellyseerrApiUrl(baseUrl, path).newBuilder()
            .addQueryParameter("take", DEFAULT_SECTION_LIMIT.toString())
            .build()

        val payload = fetchJson(url, jellyseerrHeaders()) ?: return null
        val items = parseJellyseerrItems(payload, baseUrl, mediaType)
        if (items.isEmpty()) return null

        return ExternalSection(
            id = type.name.lowercase(),
            title = null,
            type = type,
            items = items,
        )
    }

    private fun loadJellyseerrRequests(baseUrl: HttpUrl): ExternalSection? {
        val url = jellyseerrApiUrl(baseUrl, "request").newBuilder()
            .addQueryParameter("take", DEFAULT_SECTION_LIMIT.toString())
            .build()

        val payload = fetchJson(url, jellyseerrHeaders()) ?: return null
        val items = parseJellyseerrItems(payload, baseUrl, ExternalMediaType.UNKNOWN)
            .map { it.copy(requestable = false) }

        if (items.isEmpty()) return null

        return ExternalSection(
            id = ExternalSectionType.MY_REQUESTS.name.lowercase(),
            title = null,
            type = ExternalSectionType.MY_REQUESTS,
            items = items,
        )
    }

    private fun loadRadarrUpcoming(): ExternalSection? {
        val baseUrl = radarrBaseUrl() ?: return null
        val apiKey = externalServicesPreferences[ExternalServicesPreferences.radarrApiKey].trim()
        if (apiKey.isBlank()) return null

        val dateRange = calendarRange()
        val url = arrCalendarUrl(baseUrl, dateRange)
        val payload = fetchJson(url, arrHeaders(apiKey)) ?: return null
        val items = parseRadarrItems(payload, baseUrl, apiKey)
        if (items.isEmpty()) return null

        return ExternalSection(
            id = ExternalSectionType.UPCOMING_MOVIES.name.lowercase(),
            title = null,
            type = ExternalSectionType.UPCOMING_MOVIES,
            items = items,
        )
    }

    private fun loadSonarrUpcoming(): ExternalSection? {
        val baseUrl = sonarrBaseUrl() ?: return null
        val apiKey = externalServicesPreferences[ExternalServicesPreferences.sonarrApiKey].trim()
        if (apiKey.isBlank()) return null

        val dateRange = calendarRange()
        val url = arrCalendarUrl(baseUrl, dateRange)
        val payload = fetchJson(url, arrHeaders(apiKey)) ?: return null
        val items = parseSonarrItems(payload, baseUrl, apiKey)
        if (items.isEmpty()) return null

        return ExternalSection(
            id = ExternalSectionType.UPCOMING_SHOWS.name.lowercase(),
            title = null,
            type = ExternalSectionType.UPCOMING_SHOWS,
            items = items,
        )
    }

    private fun parseJellyseerrItems(
        payload: JsonElement,
        baseUrl: HttpUrl,
        defaultMediaType: ExternalMediaType,
    ): List<ExternalSectionItem> {
        val root = payload.jsonObject
        val results = root["results"]?.jsonArray ?: root["items"]?.jsonArray ?: return emptyList()

        return results.mapNotNull { element ->
            val obj = element.jsonObject
            val media = obj["media"]?.jsonObject
            val data = media ?: obj

            val id = data.int("id")
                ?: data.int("tmdbId")
                ?: obj.int("mediaId")
                ?: obj.int("id")
                ?: return@mapNotNull null

            val name = data.string("title")
                ?: data.string("name")
                ?: obj.string("title")
                ?: obj.string("name")
                ?: return@mapNotNull null

            val mediaTypeValue = data.string("mediaType") ?: obj.string("mediaType")
            val mediaType = when (mediaTypeValue?.lowercase()) {
                "movie" -> ExternalMediaType.MOVIE
                "tv" -> ExternalMediaType.SHOW
                else -> defaultMediaType
            }

            val overview = data.string("overview") ?: obj.string("overview")
            val posterPath = data.string("posterPath") ?: obj.string("posterPath")
            val backdropPath = data.string("backdropPath") ?: obj.string("backdropPath")
            val year = data.int("year")
                ?: obj.int("year")
                ?: parseYear(data.string("releaseDate"))
                ?: parseYear(data.string("firstAirDate"))
                ?: parseYear(obj.string("releaseDate"))
                ?: parseYear(obj.string("firstAirDate"))

            val mediaInfo = data["mediaInfo"]?.jsonObject ?: obj["mediaInfo"]?.jsonObject
            val status = mediaInfo?.string("status")
            val jellyfinId = parseUuid(mediaInfo?.string("jellyfinId"))

            val isRequested = obj.bool("requested") == true
                || obj.int("requestCount")?.let { it > 0 } == true
                || mediaInfo?.string("requestStatus") != null

            val inLibrary = status == "AVAILABLE" || status == "PARTIALLY_AVAILABLE" || jellyfinId != null
            val requestable = !inLibrary && !isRequested

            ExternalSectionItem(
                id = id.toString(),
                name = name,
                year = year,
                overview = overview,
                posterUrl = buildJellyseerrImageUrl(baseUrl, posterPath, POSTER_WIDTH),
                backdropUrl = buildJellyseerrImageUrl(baseUrl, backdropPath, BACKDROP_WIDTH),
                mediaType = mediaType,
                inLibrary = inLibrary,
                requestable = requestable,
                jellyfinId = jellyfinId,
            )
        }
    }

    private fun parseRadarrItems(
        payload: JsonElement,
        baseUrl: HttpUrl,
        apiKey: String,
    ): List<ExternalSectionItem> {
        val array = payload as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj.string("title") ?: return@mapNotNull null
            val year = obj.int("year")
            val overview = obj.string("overview")
            val hasFile = obj.bool("hasFile") ?: false
            val posterUrl = parseArrImage(obj["images"], baseUrl, apiKey, "poster")
            val backdropUrl = parseArrImage(obj["images"], baseUrl, apiKey, "fanart")
            val id = obj.int("tmdbId") ?: obj.int("id") ?: return@mapNotNull null

            ExternalSectionItem(
                id = id.toString(),
                name = title,
                year = year,
                overview = overview,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                mediaType = ExternalMediaType.MOVIE,
                inLibrary = hasFile,
                requestable = false,
            )
        }
    }

    private fun parseSonarrItems(
        payload: JsonElement,
        baseUrl: HttpUrl,
        apiKey: String,
    ): List<ExternalSectionItem> {
        val array = payload as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val series = obj["series"]?.jsonObject
            val title = series?.string("title") ?: obj.string("seriesTitle") ?: return@mapNotNull null
            val year = series?.int("year")
            val overview = series?.string("overview") ?: obj.string("overview")
            val hasFile = obj.bool("hasFile") ?: false
            val posterUrl = parseArrImage(series?.get("images"), baseUrl, apiKey, "poster")
            val backdropUrl = parseArrImage(series?.get("images"), baseUrl, apiKey, "fanart")
            val id = series?.int("tvdbId") ?: series?.int("id") ?: return@mapNotNull null

            ExternalSectionItem(
                id = id.toString(),
                name = title,
                year = year,
                overview = overview,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                mediaType = ExternalMediaType.SHOW,
                inLibrary = hasFile,
                requestable = false,
            )
        }
    }

    private fun fetchJson(url: HttpUrl, headers: Headers): JsonElement? {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return null
        response.use { result ->
            if (!result.isSuccessful) return null
            val body = result.body?.string()?.trim().orEmpty()
            if (body.isBlank()) return null
            return runCatching { json.parseToJsonElement(body) }.getOrNull()
        }
    }

    private fun buildJellyseerrImageUrl(baseUrl: HttpUrl, path: String?, width: Int): String? {
        if (path.isNullOrBlank()) return null
        val url = jellyseerrApiUrl(baseUrl, "image").newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("width", width.toString())
            .build()
        return url.toString()
    }

    private fun parseArrImage(
        images: JsonElement?,
        baseUrl: HttpUrl,
        apiKey: String,
        coverType: String,
    ): String? {
        val array = images as? JsonArray ?: return null
        val match = array.firstOrNull { element ->
            element.jsonObject.string("coverType")?.equals(coverType, ignoreCase = true) == true
        }?.jsonObject ?: return null

        val remoteUrl = match.string("remoteUrl")
        if (!remoteUrl.isNullOrBlank()) return remoteUrl

        val url = match.string("url") ?: return null
        val resolved = baseUrl.resolve(url)?.newBuilder()?.addQueryParameter("apikey", apiKey)?.build()
        return resolved?.toString()
    }

    private fun jellyseerrBaseUrl(): HttpUrl? = normalizeUrl(
        externalServicesPreferences[ExternalServicesPreferences.jellyseerrUrl]
    )

    private fun radarrBaseUrl(): HttpUrl? = normalizeUrl(
        externalServicesPreferences[ExternalServicesPreferences.radarrUrl]
    )

    private fun sonarrBaseUrl(): HttpUrl? = normalizeUrl(
        externalServicesPreferences[ExternalServicesPreferences.sonarrUrl]
    )

    private fun jellyseerrApiUrl(baseUrl: HttpUrl, path: String): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegments("api/v1")
            .addPathSegments(path)
            .build()
    }

    private fun arrCalendarUrl(baseUrl: HttpUrl, dateRange: DateRange): HttpUrl {
        return baseUrl.newBuilder()
            .addPathSegments("api/v3/calendar")
            .addQueryParameter("start", dateRange.start.toString())
            .addQueryParameter("end", dateRange.end.toString())
            .build()
    }

    private fun calendarRange(): DateRange {
        val start = LocalDate.now().minusDays(1)
        val end = LocalDate.now().plusDays(30)
        return DateRange(start, end)
    }

    private fun jellyseerrHeaders(): Headers {
        val apiKey = externalServicesPreferences[ExternalServicesPreferences.jellyseerrApiKey].trim()
        return if (apiKey.isNotBlank()) {
            Headers.headersOf("X-Api-Key", apiKey)
        } else {
            Headers.headersOf()
        }
    }

    private fun arrHeaders(apiKey: String): Headers = Headers.headersOf("X-Api-Key", apiKey)

    private fun hasJellyseerr(): Boolean =
        externalServicesPreferences[ExternalServicesPreferences.jellyseerrUrl].isNotBlank()

    private fun hasRadarr(): Boolean =
        externalServicesPreferences[ExternalServicesPreferences.radarrUrl].isNotBlank() &&
            externalServicesPreferences[ExternalServicesPreferences.radarrApiKey].isNotBlank()

    private fun hasSonarr(): Boolean =
        externalServicesPreferences[ExternalServicesPreferences.sonarrUrl].isNotBlank() &&
            externalServicesPreferences[ExternalServicesPreferences.sonarrApiKey].isNotBlank()

    private fun normalizeUrl(rawUrl: String): HttpUrl? {
        val cleaned = rawUrl.trim().trimEnd('/')
        return if (cleaned.isBlank()) null else cleaned.toHttpUrlOrNull()
    }

    private fun parseYear(value: String?): Int? = value?.take(4)?.toIntOrNull()

    private fun parseUuid(value: String?): UUID? = try {
        if (value.isNullOrBlank()) null else UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private data class DateRange(val start: LocalDate, val end: LocalDate)

    private companion object {
        private const val DEFAULT_SECTION_LIMIT = 20
        private const val POSTER_WIDTH = 500
        private const val BACKDROP_WIDTH = 1280
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
