package ru.haykor.sudv.client.ui.common

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

@Composable
actual fun rememberFileOutput(): FileOutput {
    // The saver is an activity result launcher, so it has to be registered in composition; deliver() awaits its answer.
    val pending = remember { AtomicReference<CompletableDeferred<PlatformFile?>?>(null) }
    val saver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { pending.getAndSet(null)?.complete(it) }
    return remember(saver) {
        object : FileOutput {
            override val canOpen = true

            override suspend fun deliver(delivery: FileDelivery): String? {
                val file = delivery.file
                return when (delivery.action) {
                    FileAction.SAVE -> {
                        val result = CompletableDeferred<PlatformFile?>().also(pending::set)
                        saver.launch(file.fileName.substringBeforeLast('.'), file.fileName.substringAfterLast('.', "").ifEmpty { null })
                        result.await()?.write(file.bytes)
                        null
                    }
                    FileAction.OPEN -> {
                        // Another app reads it through FileKit's FileProvider, which shares the app's cache directory.
                        val copy = PlatformFile(FileKit.cacheDir, file.fileName)
                        copy.write(file.bytes)
                        try {
                            FileKit.openFileWithDefaultApplication(copy)
                            null
                        } catch (e: ActivityNotFoundException) {
                            "Нет приложения, чтобы открыть этот файл. Сохраните его и откройте вручную"
                        }
                    }
                }
            }
        }
    }
}
