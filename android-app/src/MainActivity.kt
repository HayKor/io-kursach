package ru.haykor.sudv.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialExpressiveTheme
import com.arkivanov.decompose.retainedComponent
import ru.haykor.sudv.client.ui.RootContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val graph = (applicationContext as SudvApplication).graph
        val root = retainedComponent { ctx ->
            graph.rootFactory.create(ctx)
        }
        setContent {
            MaterialExpressiveTheme {
                RootContent(root)
            }
        }
    }
}
