package net.thunderbird.android.imagina

import android.content.res.Resources
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import app.k9mail.legacy.ui.folder.R
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Migadu names the special folders of its mailboxes in English (Archive, Drafts, Junk, Sent, Trash), so Imagina Mail
 * shows them by their type with the name in the language of the person (IMAGINA.md, «Carpetas especiales»). The
 * switch lives in Thunderbird's folder formatter and is off there: Thunderbird and K-9 Mail keep the server's names.
 */
class ImaginaFolderNamesTest {
    @Test
    fun `Imagina Mail turns on the names of the special folders and Thunderbird keeps them off`() {
        val app = ImaginaProjectFiles.bools(ImaginaProjectFiles.module("src/main/res/values/constants.xml"))
        val thunderbird = ImaginaProjectFiles.bools(
            ImaginaProjectFiles.root("legacy/ui/folder/src/main/res/values/constants.xml"),
        )

        assertThat(app[SWITCH]).isEqualTo("true")
        assertThat(thunderbird[SWITCH]).isEqualTo("false")
    }

    @Test
    fun `every language of Imagina names the five special folders and the server's names are not shown`() {
        val problems = EXPECTED_NAMES.flatMap { (folder, expected) ->
            val names = expected.keys.associateWith { effectiveString("special_mailbox_name_$it", folder) }
            expected.mapNotNull { (key, name) ->
                if (names[key] == name) null else "$folder $key: ${names[key]} (expected $name)"
            }
        }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `with the switch on the special folders of a server show the name of their type and the rest their own`() {
        val formatter = FolderNameFormatter(resources(localized = true))

        val shown = SERVER_FOLDERS.associate { (name, type) ->
            val remote = RemoteFolder(id = 1, serverId = name, name = name, type = type)
            val folder = Folder(id = 1, name = name, type = type, isLocalOnly = false)
            assertThat(formatter.displayName(remote)).isEqualTo(formatter.displayName(folder))
            name to formatter.displayName(folder)
        }

        assertThat(shown).isEqualTo(
            mapOf(
                "INBOX" to "Bandeja de entrada",
                "Drafts" to "Borradores",
                "Sent" to "Enviados",
                "Trash" to "Papelera",
                "Junk" to "Spam",
                "Archive" to "Archivo",
                "Clientes" to "Clientes",
            ),
        )
    }

    @Test
    fun `with the switch off the folders of a server keep the name the server gave them`() {
        val formatter = FolderNameFormatter(resources(localized = false))

        SERVER_FOLDERS.forEach { (name, type) ->
            val shown = formatter.displayName(Folder(id = 1, name = name, type = type, isLocalOnly = false))
            val expected = if (type == FolderType.INBOX) "Bandeja de entrada" else name
            assertThat(shown).isEqualTo(expected)
            assertThat(formatter.displayName(RemoteFolder(id = 1, serverId = name, name = name, type = type)))
                .isEqualTo(expected)
        }
    }

    @Test
    fun `a folder that only looks like a special folder by its name keeps it`() {
        val formatter = FolderNameFormatter(resources(localized = true))

        val sent = Folder(id = 1, name = "Sent", type = FolderType.REGULAR, isLocalOnly = false)
        val junk = RemoteFolder(id = 2, serverId = "Junk", name = "Junk", type = FolderType.REGULAR)

        assertThat(formatter.displayName(sent)).isEqualTo("Sent")
        assertThat(formatter.displayName(junk)).isEqualTo("Junk")
    }

    /**
     * What Android resolves for [name] in a language folder of the app: the app's own value if it has one, else the one
     * of the folder module of Thunderbird in the same language.
     */
    private fun effectiveString(name: String, folder: String): String? {
        val app = ImaginaProjectFiles.strings(ImaginaProjectFiles.module("src/main/res/$folder/strings.xml"))
        val library = ImaginaProjectFiles.root("legacy/ui/folder/src/main/res/$folder/strings.xml")
            .takeIf { it.exists() }
            ?.let { ImaginaProjectFiles.strings(it) }
            .orEmpty()

        return app[name] ?: library[name]
    }

    /** The folder module's resources, in Spanish, as a device set to Spanish with Imagina's switch would give them. */
    private fun resources(localized: Boolean): Resources = mock {
        on { getBoolean(R.bool.folder_special_names_localized) } doReturn localized
        on { getString(R.string.special_mailbox_name_inbox) } doReturn "Bandeja de entrada"
        on { getString(R.string.special_mailbox_name_outbox) } doReturn "Bandeja de salida"
        on { getString(R.string.special_mailbox_name_drafts) } doReturn "Borradores"
        on { getString(R.string.special_mailbox_name_sent) } doReturn "Enviados"
        on { getString(R.string.special_mailbox_name_trash) } doReturn "Papelera"
        on { getString(R.string.special_mailbox_name_spam) } doReturn "Spam"
        on { getString(R.string.special_mailbox_name_archive) } doReturn "Archivo"
    }

    private companion object {
        const val SWITCH = "folder_special_names_localized"

        /** The folders of a Migadu mailbox, with the type its SPECIAL-USE flags give them. */
        val SERVER_FOLDERS = listOf(
            "INBOX" to FolderType.INBOX,
            "Drafts" to FolderType.DRAFTS,
            "Sent" to FolderType.SENT,
            "Trash" to FolderType.TRASH,
            "Junk" to FolderType.SPAM,
            "Archive" to FolderType.ARCHIVE,
            "Clientes" to FolderType.REGULAR,
        )

        val EXPECTED_NAMES = mapOf(
            "values" to names("Drafts", "Sent", "Trash", "Spam", "Archive"),
            "values-en-rGB" to names("Drafts", "Sent", "Bin", "Spam", "Archive"),
            "values-es" to names("Borradores", "Enviados", "Papelera", "Spam", "Archivo"),
            "values-de" to names("Entwürfe", "Gesendet", "Papierkorb", "Spam", "Archiv"),
            "values-fr" to names("Brouillons", "Envoyés", "Corbeille", "Spam", "Archives"),
            "values-it" to names("Bozze", "Posta inviata", "Cestino", "Spam", "Archivio"),
            "values-pt-rPT" to names("Rascunhos", "Enviados", "Lixo", "Spam", "Arquivo"),
        )

        fun names(drafts: String, sent: String, trash: String, spam: String, archive: String) = mapOf(
            "drafts" to drafts,
            "sent" to sent,
            "trash" to trash,
            "spam" to spam,
            "archive" to archive,
        )
    }
}
