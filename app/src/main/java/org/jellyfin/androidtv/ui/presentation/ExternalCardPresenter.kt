package org.jellyfin.androidtv.ui.presentation

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.Presenter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.model.ExternalSectionItem
import org.jellyfin.androidtv.ui.card.LegacyImageCardView
import org.jellyfin.androidtv.util.ImageHelper

class ExternalCardPresenter(
    private val imageAspect: Double = ImageHelper.ASPECT_RATIO_2_3,
    private val cardHeight: Int = DEFAULT_CARD_HEIGHT,
) : Presenter() {
    private class ViewHolder(view: LegacyImageCardView) : Presenter.ViewHolder(view) {
        val cardView: LegacyImageCardView = view
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val cardView = LegacyImageCardView(parent.context, true)
        cardView.setFocusable(true)
        cardView.setFocusableInTouchMode(true)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as? ViewHolder ?: return
        val externalItem = item as? ExternalSectionItem ?: return

        val cardWidth = (imageAspect * cardHeight).toInt().coerceAtLeast(1)
        holder.cardView.setMainImageDimensions(cardWidth, cardHeight)
        holder.cardView.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER)
        holder.cardView.setTitleText(externalItem.name)
        holder.cardView.setContentText(externalItem.year?.toString().orEmpty())

        val placeholder = ContextCompat.getDrawable(holder.cardView.context, R.drawable.tile_port_video)
        val imageUrl = externalItem.posterUrl ?: externalItem.backdropUrl
        holder.cardView.getMainImageView().load(
            url = imageUrl,
            blurHash = null,
            placeholder = placeholder,
            aspectRatio = imageAspect,
            blurHashResolution = 16,
        )
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as? ViewHolder ?: return
        holder.cardView.getMainImageView().setImageDrawable(null)
        holder.cardView.setTitleText("")
        holder.cardView.setContentText("")
    }

    private companion object {
        private const val DEFAULT_CARD_HEIGHT = 180
    }
}
