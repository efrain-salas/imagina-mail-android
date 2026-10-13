package app.k9mail.legacy.ui.folder

import android.content.res.Resources
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

class FolderNameFormatter(private val resources: Resources) {
    /**
     * Imagina (IMAGINA.md): whether the special folders of a server are shown with the name of their type in the
     * language of the app. False in Thunderbird and K-9 Mail, where they keep the name the server gave them.
     */
    private val specialFolderNamesLocalized: Boolean by lazy {
        resources.getBoolean(R.bool.folder_special_names_localized)
    }

    fun displayName(folder: Folder): String {
        return if (folder.isLocalOnly) {
            localFolderDisplayName(folder)
        } else {
            remoteFolderDisplayName(folder)
        }
    }

    private fun localFolderDisplayName(folder: Folder) = when (folder.type) {
        FolderType.OUTBOX -> resources.getString(R.string.special_mailbox_name_outbox)
        FolderType.DRAFTS -> resources.getString(R.string.special_mailbox_name_drafts)
        FolderType.SENT -> resources.getString(R.string.special_mailbox_name_sent)
        FolderType.TRASH -> resources.getString(R.string.special_mailbox_name_trash)
        else -> folder.name
    }

    private fun remoteFolderDisplayName(folder: Folder) = when (folder.type) {
        FolderType.INBOX -> resources.getString(R.string.special_mailbox_name_inbox)
        else -> localizedSpecialFolderName(folder.type) ?: folder.name
    }

    fun displayName(folder: RemoteFolder) = when (folder.type) {
        FolderType.INBOX -> resources.getString(R.string.special_mailbox_name_inbox)
        else -> localizedSpecialFolderName(folder.type) ?: folder.name
    }

    private fun localizedSpecialFolderName(type: FolderType): String? {
        if (!specialFolderNamesLocalized) return null

        return when (type) {
            FolderType.DRAFTS -> resources.getString(R.string.special_mailbox_name_drafts)
            FolderType.SENT -> resources.getString(R.string.special_mailbox_name_sent)
            FolderType.TRASH -> resources.getString(R.string.special_mailbox_name_trash)
            FolderType.SPAM -> resources.getString(R.string.special_mailbox_name_spam)
            FolderType.ARCHIVE -> resources.getString(R.string.special_mailbox_name_archive)
            else -> null
        }
    }
}
