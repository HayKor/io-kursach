package ru.haykor.sudv

import ru.haykor.sudv.api.PluginOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginInstallerTest {
    @Test
    fun everyPluginOrderHasExactlyOneInstaller() = apiTest {
        assertEquals(PluginOrder.entries, appGraph().plugins.map { it.order }.sorted())
    }
}
