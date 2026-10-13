package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.util.Properties
import kotlin.test.Test

/**
 * What Imagina Mail says to the people and to the servers it talks to is Imagina's, not Thunderbird's or Mozilla's
 * (IMAGINA.md, «Textos y enlaces»): the privacy policy that Google Play asks for inside the app, the name the SMTP
 * client presents itself with, and the one link of Thunderbird's screens that was not in `constants.xml`.
 *
 * Thunderbird and K-9 Mail keep what they had: the hooks are switches whose default is Thunderbird's.
 */
class ImaginaPrivacyAndHostsTest {
    private val thunderbirdConstants = ImaginaProjectFiles.strings(
        ImaginaProjectFiles.root("legacy/ui/legacy/src/main/res/values/constants.xml"),
    )
    private val imaginaConstants = ImaginaProjectFiles.strings(
        ImaginaProjectFiles.module("src/main/res/values/constants.xml"),
    )

    @Test
    fun `the privacy policy is Imagina's page and Thunderbird shows none`() {
        assertThat(imaginaConstants["app_privacy_url"]).isEqualTo("https://imagina.build/privacy")
        assertThat(thunderbirdConstants["app_privacy_url"]).isEqualTo("")
    }

    @Test
    fun `the label of the privacy policy is in every language of Imagina`() {
        val missing = LANGUAGE_FOLDERS.filter { folder ->
            val strings = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/$folder/strings.xml"))
            strings["about_privacy_policy"].isNullOrBlank()
        }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `About shows the privacy policy only when the app gives one, and the sign-in screen links to it`() {
        val about = ImaginaProjectFiles
            .root("legacy/ui/legacy/src/main/java/com/fsck/k9/ui/settings/AboutFragment.kt")
            .readText()
        val signIn = ImaginaProjectFiles.module(
            "src/main/kotlin/net/thunderbird/android/imagina/ImaginaSignInScreen.kt",
        ).readText()

        assertThat(about).contains("val privacyUrl = stringResource(R.string.app_privacy_url)")
        assertThat(about).contains("if (privacyUrl.isNotBlank())")
        assertThat(about).contains("getString(R.string.app_privacy_url)")
        assertThat(signIn).contains("stringResource(R.string.app_privacy_url)")
        assertThat(signIn).contains("stringResource(R.string.about_privacy_policy)")
    }

    @Test
    fun `the Autocrypt link to the blog of K-9 Mail is off in Imagina Mail and on in Thunderbird`() {
        val thunderbird = ImaginaProjectFiles.bools(
            ImaginaProjectFiles.root("legacy/ui/legacy/src/main/res/values/constants.xml"),
        )
        val imagina = ImaginaProjectFiles.bools(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))
        val dialog = ImaginaProjectFiles
            .root("$SETTINGS_ACCOUNT/AutocryptPreferEncryptDialogFragment.kt")
            .readText()

        assertThat(imagina["autocrypt_learn_more_available"]).isEqualTo("false")
        assertThat(thunderbird["autocrypt_learn_more_available"]).isEqualTo("true")
        assertThat(dialog).contains("R.bool.autocrypt_learn_more_available")
    }

    @Test
    fun `the SMTP client presents itself as imagina build and the backend factories read the name`() {
        val parameters = Properties().apply {
            ImaginaProjectFiles.root("imagina/imagina.properties").inputStream().use { load(it) }
        }
        val imaginaModule = ImaginaProjectFiles.module(
            "src/main/kotlin/net/thunderbird/android/imagina/ImaginaModule.kt",
        ).readText()
        val backends = ImaginaProjectFiles
            .root("legacy/common/src/main/java/com/fsck/k9/backends/KoinModule.kt")
            .readText()

        assertThat(parameters.getProperty("IMAGINA_SMTP_HELLO_NAME")).isEqualTo("imagina.build")
        assertThat(imaginaModule).contains(
            "single(named(\"SmtpHelloName\")) { BuildConfig.IMAGINA_SMTP_HELLO_NAME }",
        )
        assertThat(backends).contains("getOrNull(named(\"SmtpHelloName\")) ?: SMTP_HELLO_NAME")
        assertThat(backends.split("smtpHelloName = smtpHelloName()").size - 1).isEqualTo(2)
    }

    @Test
    fun `no text of Imagina links to a site of Thunderbird, Mozilla or K-9 Mail`() {
        val resources = ImaginaProjectFiles.module("src/main/res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.parentFile.name.startsWith("values") }
            .toList()
        val links = resources.flatMap { file ->
            LINK.findAll(file.readText()).map { "${file.parentFile.name}/${file.name}: ${it.value}" }.toList()
        }

        assertThat(resources.size > 6).isEqualTo(true)
        assertThat(links).isEmpty()
    }

    private companion object {
        const val SETTINGS_ACCOUNT = "legacy/ui/legacy/src/main/java/com/fsck/k9/ui/settings/account"
        val LANGUAGE_FOLDERS = listOf("values", "values-es", "values-de", "values-fr", "values-it", "values-pt-rPT")
        val LINK = Regex("""https?://[^\s<"']*(thunderbird|mozilla|k9mail|mzla)[^\s<"']*""", RegexOption.IGNORE_CASE)
    }
}
