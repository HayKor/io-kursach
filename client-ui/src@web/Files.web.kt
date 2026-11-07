package ru.haykor.sudv.client.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download

@Composable
actual fun rememberFileOutput(): FileOutput = remember {
    object : FileOutput {
        override val canOpen = false

        // The browser decides where the file goes (usually Downloads) and offers to open it.
        override suspend fun deliver(delivery: FileDelivery): String? {
            FileKit.download(delivery.file.bytes, delivery.file.fileName)
            return null
        }
    }
}
