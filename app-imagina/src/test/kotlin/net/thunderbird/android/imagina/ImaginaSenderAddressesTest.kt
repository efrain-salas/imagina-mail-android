package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * Imagina does not support sending from aliases (IMAGINA.md, «Cambios en ficheros de Thunderbird»): a mailbox only
 * sends as itself, so the app turns off the switch that Thunderbird's «Manage identities» and the sender address of
 * the composition defaults read. A Thunderbird update that drops either hook brings the options back.
 */
class ImaginaSenderAddressesTest {
    @Test
    fun `Imagina Mail turns off sending from other addresses`() {
        val bools = ImaginaProjectFiles.bools(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))

        assertThat(bools["sender_addresses_editable"]).isEqualTo("false")
    }

    @Test
    fun `Thunderbird's identities and sender address follow the switch`() {
        val settings = ImaginaProjectFiles.root("legacy/ui/legacy/src/main/res/xml/account_settings.xml").readText()
        val identities = settings.substringAfter("android:key=\"manage_identities\"").substringBefore("/>")
        val composition = ImaginaProjectFiles
            .root("legacy/ui/legacy/src/main/java/com/fsck/k9/activity/setup/AccountSetupComposition.kt")
            .readText()

        assertThat(identities).contains("app:isPreferenceVisible=\"@bool/sender_addresses_editable\"")
        assertThat(composition).contains("isReadOnly = !booleanResource(id = R.bool.sender_addresses_editable)")
    }
}
