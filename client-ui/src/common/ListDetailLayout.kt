package ru.haykor.sudv.client.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.router.stack.ChildStack

/** From this width (Material's «expanded» window class) the list stays visible next to the open screen. */
private val TwoPaneMinWidth = 840.dp

/**
 * A section's stack where the bottom child is the list. Narrow: one screen at a time. Wide: the list on the left and
 * the top of the stack on the right ([placeholder] while only the list is open). The layout is done here rather than
 * with Decompose Child Panels, whose web navigation replaces the browser history entry instead of pushing one.
 */
@Composable
fun <T : Any> ListDetailLayout(
    stack: ChildStack<*, T>,
    placeholder: String,
    list: @Composable (Modifier) -> Unit,
    detail: @Composable (child: T, twoPane: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listChild = stack.items.first().instance
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width(400.dp).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val active = stack.active.instance
                    if (active === listChild) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center))
                    } else {
                        detail(active, true)
                    }
                }
            }
        } else {
            Children(stack = stack) { child ->
                if (child.instance === listChild) list(Modifier) else detail(child.instance, false)
            }
        }
    }
}
