package net.thunderbird.android

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.android.provider.TbAppIconNotificationProvider

class TbAppIconNotificationProviderTest {
    @Test
    fun `provides Imagina's notification icon, not Thunderbird's logo`() {
        val provider = TbAppIconNotificationProvider()
        val icon = provider.pushNotificationIcon

        assertThat(icon).isEqualTo(R.drawable.ic_imagina_notification)
    }
}
