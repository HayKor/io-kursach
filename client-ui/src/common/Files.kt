package ru.haykor.sudv.client.ui.common

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import ru.haykor.sudv.client.domain.FileContent

/** A file the user picked. Its size is known before the content is read, so an oversized file is never loaded. */
class PickedFile(val name: String, val size: Long, val read: suspend () -> ByteArray)

/** What to do with a downloaded file. */
enum class FileAction {
    /** Android: the system «Save as» dialog; web: the browser's download. */
    SAVE,

    /** Android only: view the file in another app. */
    OPEN,
}

/** A downloaded file on its way to the platform. */
class FileDelivery(val file: FileContent, val action: FileAction)

/** The platform side of downloaded files. */
interface FileOutput {
    /** Whether [FileAction.OPEN] is offered; in the browser a downloaded file is opened from the browser itself. */
    val canOpen: Boolean

    /** Returns a message for the user when the file could not be saved or opened, `null` on success or cancel. */
    suspend fun deliver(delivery: FileDelivery): String?
}

@Composable
expect fun rememberFileOutput(): FileOutput

/** Returns a function that opens the system file picker limited to [extensions]. */
@Composable
fun rememberFilePicker(extensions: Set<String>, onPicked: (PickedFile) -> Unit): () -> Unit {
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(extensions)) { file ->
        if (file != null) onPicked(PickedFile(file.name, file.size()) { file.readBytes() })
    }
    return { launcher.launch() }
}
