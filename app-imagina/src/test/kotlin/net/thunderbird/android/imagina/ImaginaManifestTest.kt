package net.thunderbird.android.imagina

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAtLeast
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * What Imagina Mail takes out of the merged manifest because nothing it opens uses it (IMAGINA.md, «Lo propio de
 * Imagina»): biometrics, the CAMERA permission and the queries of other mail apps. «Take photo» in the attachments
 * keeps working without CAMERA because the system camera app takes the photo through an intent.
 */
class ImaginaManifestTest {
    @Test
    fun `the manifest removes the biometric and camera permissions and the queries of other mail apps`() {
        val manifest = ImaginaProjectFiles.module("src/main/AndroidManifest.xml").readText()

        assertThat(removedNames(manifest, "uses-permission")).containsAtLeast(
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "android.permission.CAMERA",
        )
        assertThat(removedNames(manifest, "package")).containsOnly(
            "com.fsck.k9",
            "net.thunderbird.android",
            "net.thunderbird.android.beta",
            "net.thunderbird.android.daily",
        )
    }

    @Test
    fun `without the CAMERA permission the app does not ask for it before opening the system camera`() {
        val app = ImaginaProjectFiles.bools(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))
        val thunderbird = ImaginaProjectFiles.bools(
            ImaginaProjectFiles.root("core/android/common/src/main/res/values/constants.xml"),
        )
        val handler = ImaginaProjectFiles
            .root("core/android/common/src/main/kotlin/app/k9mail/core/android/common/camera/CameraCaptureHandler.kt")
            .readText()

        assertThat(app["camera_permission_required"]).isEqualTo("false")
        assertThat(thunderbird["camera_permission_required"]).isEqualTo("true")
        assertThat(handler).contains("R.bool.camera_permission_required")
        assertThat(handler).contains("Intent(MediaStore.ACTION_IMAGE_CAPTURE)")
    }

    /** The `android:name` of every `<tag .. tools:node="remove" />` of the manifest. */
    private fun removedNames(manifest: String, tag: String): Set<String> =
        Regex("<$tag\\s+([^>]*?)/>", RegexOption.DOT_MATCHES_ALL).findAll(manifest)
            .map { it.groupValues[1] }
            .filter { "tools:node=\"remove\"" in it }
            .mapNotNull { Regex("android:name=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            .toSet()
}
