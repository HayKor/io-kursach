package ru.haykor.sudv.server.core.http

import ru.haykor.sudv.server.apiTest
import ru.haykor.sudv.server.appGraph
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginInstallerTest {
    @Test
    fun everyPluginOrderHasExactlyOneInstaller() = apiTest {
        assertEquals(PluginOrder.entries, appGraph().plugins.map { it.order }.sorted())
    }
}
