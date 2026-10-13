package net.thunderbird.android.provider

import app.k9mail.core.android.common.provider.NotificationIconResourceProvider
import net.thunderbird.android.R

/**
 * The small icon of the service notifications (Thunderbird draws its bird here): Imagina's envelope with the dot,
 * generated from imagina/brand/imagina-mail-notification.svg (IMAGINA.md, «Imágenes de marca»).
 */
class TbAppIconNotificationProvider : NotificationIconResourceProvider {
    override val pushNotificationIcon: Int
        get() = R.drawable.ic_imagina_notification
}
