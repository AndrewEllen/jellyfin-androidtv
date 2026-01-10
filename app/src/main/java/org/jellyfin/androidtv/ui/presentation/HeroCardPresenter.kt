package org.jellyfin.androidtv.ui.presentation

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.androidtv.util.sdk.canPlay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale
import kotlin.math.ceil

class HeroCardPresenter(
    private val labelText: String?,
    private val onPlay: (BaseItemDto) -> Unit,
) : Presenter(), KoinComponent {
    private val imageHelper by inject<ImageHelper>()

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_card_hero, parent, false)
        return HeroViewHolder(view, onPlay)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as? HeroViewHolder ?: return
        val rowItem = item as? BaseRowItem
        holder.bind(rowItem?.baseItem, labelText, imageHelper)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        (viewHolder as? HeroViewHolder)?.unbind()
    }

    private class HeroViewHolder(
        view: View,
        private val onPlay: (BaseItemDto) -> Unit,
    ) : Presenter.ViewHolder(view) {
        private val imageView = view.findViewById<AsyncImageView>(R.id.hero_image)
        private val focusRing = view.findViewById<View>(R.id.hero_focus_ring)
        private val labelView = view.findViewById<TextView>(R.id.hero_label)
        private val titleView = view.findViewById<TextView>(R.id.hero_title)
        private val metaView = view.findViewById<TextView>(R.id.hero_meta)
        private val genresView = view.findViewById<TextView>(R.id.hero_genres)
        private val overviewView = view.findViewById<TextView>(R.id.hero_overview)
        private val playView = view.findViewById<TextView>(R.id.hero_play)

        private var boundItem: BaseItemDto? = null

        init {
            view.setOnFocusChangeListener { _, hasFocus ->
                focusRing.isVisible = hasFocus
            }
            view.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
                if (keyCode != KeyEvent.KEYCODE_MEDIA_PLAY && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    return@setOnKeyListener false
                }

                val item = boundItem ?: return@setOnKeyListener false
                if (!item.canPlay()) return@setOnKeyListener false

                onPlay(item)
                true
            }
        }

        fun bind(item: BaseItemDto?, labelText: String?, imageHelper: ImageHelper) {
            boundItem = item

            if (item == null) {
                labelView.isVisible = false
                titleView.text = ""
                metaView.isVisible = false
                genresView.isVisible = false
                overviewView.isVisible = false
                playView.isVisible = false
                imageView.setImageDrawable(null)
                return
            }

            labelView.isVisible = !labelText.isNullOrBlank()
            labelView.text = labelText ?: ""

            titleView.text = item.name.orEmpty()

            val metaText = buildMetaText(view.context, item)
            metaView.isVisible = !metaText.isNullOrBlank()
            metaView.text = metaText

            val genres = item.genres?.filterNot { it.isNullOrBlank() }?.take(2)?.joinToString(" - ")
            genresView.isVisible = !genres.isNullOrBlank()
            genresView.text = genres.orEmpty()

            val overview = item.overview?.trim().orEmpty()
            overviewView.isVisible = overview.isNotEmpty()
            overviewView.text = overview

            playView.isVisible = item.canPlay()

            val width = view.resources.getDimensionPixelSize(R.dimen.home_hero_card_width)
            val height = view.resources.getDimensionPixelSize(R.dimen.home_hero_card_height)
            val placeholder = ContextCompat.getDrawable(view.context, R.drawable.tile_land_tv)

            val heroImage = item.itemBackdropImages.firstOrNull()
                ?: item.parentBackdropImages.firstOrNull()
                ?: item.itemImages[ImageType.THUMB]
                ?: item.itemImages[ImageType.PRIMARY]

            val imageUrl = heroImage?.let { imageHelper.getImageUrl(it, width, height) }
                ?: imageHelper.getThumbImageUrl(item, width, height)

            imageView.load(
                url = imageUrl,
                blurHash = heroImage?.blurHash,
                placeholder = placeholder,
                aspectRatio = ImageHelper.ASPECT_RATIO_16_9,
                blurHashResolution = 32,
            )
        }

        fun unbind() {
            boundItem = null
            imageView.setImageDrawable(null)
        }

        private fun buildMetaText(context: Context, item: BaseItemDto): String {
            val parts = mutableListOf<String>()

            val rating = item.communityRating?.let { String.format(Locale.US, "%.1f", it) }
            if (!rating.isNullOrBlank()) {
                parts.add(rating)
            }

            val year = item.productionYear ?: item.premiereDate?.year
            if (year != null) {
                parts.add(year.toString())
            }

            val officialRating = item.officialRating
            if (!officialRating.isNullOrBlank()) {
                parts.add(officialRating)
            }

            val runtime = formatRuntime(context, item.runTimeTicks)
            if (!runtime.isNullOrBlank()) {
                parts.add(runtime)
            }

            return parts.joinToString(" - ")
        }

        private fun formatRuntime(context: Context, runtimeTicks: Long?): String? {
            if (runtimeTicks == null || runtimeTicks <= 0) return null

            val totalMinutes = ceil(runtimeTicks / 600000000.0).toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60

            return if (hours > 0) {
                context.getString(R.string.runtime_hours_minutes, hours, minutes)
            } else {
                context.getString(R.string.runtime_minutes, minutes)
            }
        }
    }
}
