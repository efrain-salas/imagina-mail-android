package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * The email signature is Imagina's (IMAGINA.md, «Cambios en ficheros de Thunderbird»): the app turns off the switch
 * that Thunderbird's composition defaults read to let a person write it, and says in every language where it is
 * managed. A Thunderbird update that drops the hook brings the editable signature back.
 */
class ImaginaSignatureTest {
    @Test
    fun `Imagina Mail turns off writing the signature`() {
        val bools = ImaginaProjectFiles.bools(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))

        assertThat(bools["signature_editable"]).isEqualTo("false")
    }

    @Test
    fun `Thunderbird's signature follows the switch and shows the notice`() {
        val content = ImaginaProjectFiles
            .root("legacy/ui/legacy/src/main/java/com/fsck/k9/activity/setup/signature/SignatureContent.kt")
            .readText()

        assertThat(content).contains("booleanResource(id = R.bool.signature_editable)")
        assertThat(content).contains("stringResource(R.string.signature_managed_notice)")
        assertThat(content).contains("isReadOnly = !isEditable")
    }

    @Test
    fun `Thunderbird and K-9 keep an editable signature and no notice`() {
        val constants = ImaginaProjectFiles.root("legacy/ui/legacy/src/main/res/values/constants.xml")

        assertThat(ImaginaProjectFiles.bools(constants)["signature_editable"]).isEqualTo("true")
        assertThat(ImaginaProjectFiles.strings(constants)["signature_managed_notice"]).isEqualTo("")
    }

    @Test
    fun `the notice is in every language of Imagina and names the account`() {
        val problems = LANGUAGE_FOLDERS.mapNotNull { folder ->
            val strings = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/$folder/strings.xml"))
            val notice = strings["signature_managed_notice"]
            when {
                notice.isNullOrBlank() -> "$folder has no signature_managed_notice"
                "Imagina" !in notice -> "$folder does not name Imagina: $notice"
                else -> null
            }
        }

        assertThat(problems).isEmpty()
    }

    private companion object {
        val LANGUAGE_FOLDERS = listOf("values", "values-es", "values-de", "values-fr", "values-it", "values-pt-rPT")
    }
}
