package ru.haykor.sudv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.extensions.compose.stack.Children

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(stack = component.stack, modifier = modifier) { child ->
        when (child.instance) {
            RootComponent.Child.Main -> MainScreen()
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val model = Model(id = 1, name = "Arthur")
        Text(text = "Hello, $model!")
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    MaterialExpressiveTheme {
        MainScreen()
    }
}
