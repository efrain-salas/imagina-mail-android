package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import net.thunderbird.android.BuildConfig
import org.w3c.dom.Element

/**
 * The version of Imagina Mail is the one of the Thunderbird it is made from, with Imagina's suffix (IMAGINA.md,
 * «Versión»), and the changelog that the About screen shows has a release for it.
 */
class ImaginaVersionTest {
    private val thunderbirdBuild = ImaginaProjectFiles.root("app-thunderbird/build.gradle.kts").readText()
    private val thunderbirdVersionCode = Regex(
        """versionCode\s*=\s*(\d+)""",
    ).find(thunderbirdBuild)?.groupValues?.get(1)
    private val thunderbirdVersionName = Regex(
        """versionName\s*=\s*"([^"]+)"""",
    ).find(thunderbirdBuild)?.groupValues?.get(1)

    @Test
    fun `the version code is the one of Thunderbird`() {
        assertThat(thunderbirdVersionCode).isNotNull()

        assertThat(BuildConfig.VERSION_CODE).isEqualTo(thunderbirdVersionCode!!.toInt())
    }

    @Test
    fun `the version name is the one of Thunderbird with the suffix imagina`() {
        assertThat(thunderbirdVersionName).isNotNull()

        // The debug build adds -SNAPSHOT
        assertThat(BuildConfig.VERSION_NAME).startsWith("$thunderbirdVersionName-imagina")
    }

    @Test
    fun `the changelog of the release has a release for the current version`() {
        val releases = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ImaginaProjectFiles.module("src/release/res/raw/changelog_master.xml"))
            .getElementsByTagName("release")
        val versionCodes = (0 until releases.length).map { (releases.item(it) as Element).getAttribute("versioncode") }

        assertThat(versionCodes.contains(BuildConfig.VERSION_CODE.toString())).isEqualTo(true)
    }
}
