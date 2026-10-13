package app.k9mail.core.android.common.camera

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import app.k9mail.core.android.common.R
import app.k9mail.core.android.common.camera.CameraCaptureHandler.Companion.STATE_KEY_CAPTURED_IMAGE_URI
import app.k9mail.core.android.common.camera.io.CaptureImageFileWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

// Coverage for the process-death regression tracked in issue #11296:
// the captured-image URI was held in memory only, so an activity result
// arriving after process recreation threw
// UninitializedPropertyAccessException. These tests lock in the nullable
// getter and the save/restore round-trip that keeps the URI alive.
@RunWith(RobolectricTestRunner::class)
internal class CameraCaptureHandlerTest {

    private val fileWriter: CaptureImageFileWriter = mock()

    @Test
    fun `getCapturedImageUri returns null before openCamera has been called`() {
        val handler = CameraCaptureHandler(fileWriter)

        assertNull(handler.getCapturedImageUri())
    }

    @Test
    fun `getCapturedImageUri returns null after restoreInstanceState with a null bundle`() {
        val handler = CameraCaptureHandler(fileWriter)

        handler.restoreInstanceState(savedInstanceState = null)

        assertNull(handler.getCapturedImageUri())
    }

    @Test
    fun `restoreInstanceState rehydrates the URI a previous handler wrote out`() {
        val savedUri = Uri.parse("content://k9.provider/captured/IMG_42.jpg")
        val bundle = Bundle().apply {
            putParcelable(STATE_KEY_CAPTURED_IMAGE_URI, savedUri)
        }
        val handler = CameraCaptureHandler(fileWriter)

        handler.restoreInstanceState(bundle)

        assertEquals(savedUri, handler.getCapturedImageUri())
    }

    @Test
    fun `saveInstanceState omits the URI when nothing has been captured`() {
        val handler = CameraCaptureHandler(fileWriter)
        val outState = Bundle()

        handler.saveInstanceState(outState)

        assertNull(outState.getParcelable<Uri>(STATE_KEY_CAPTURED_IMAGE_URI))
    }

    @Test
    fun `hasCameraPermission asks the system when the app declares the permission`() {
        val handler = CameraCaptureHandler(fileWriter)

        assertTrue(handler.hasCameraPermission(context(permissionRequired = true, granted = true)))
        assertFalse(handler.hasCameraPermission(context(permissionRequired = true, granted = false)))
    }

    @Test
    fun `hasCameraPermission has nothing to ask when the app does not declare the permission`() {
        val handler = CameraCaptureHandler(fileWriter)

        assertTrue(handler.hasCameraPermission(context(permissionRequired = false, granted = false)))
    }

    private fun context(permissionRequired: Boolean, granted: Boolean): Context {
        val resources: Resources = mock {
            on { getBoolean(R.bool.camera_permission_required) } doReturn permissionRequired
        }
        return mock {
            on { this.resources } doReturn resources
            on { checkPermission(any(), any(), any()) } doReturn
                if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }
    }
}
