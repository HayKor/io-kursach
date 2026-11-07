package ru.haykor.sudv.server.core.http

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.haykor.sudv.server.apiTest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val webDir = createTempDirectory("sudv-web").also { dir ->
    dir.resolve("index.html").writeText("<html>SUDV</html>")
    dir.resolve("web-app.mjs").writeText("export {}")
    dir.resolve("web-app.wasm").writeText("wasm")
}

private val webConfig = mapOf("web.dir" to webDir.toString())

class WebClientRoutesTest {
    @Test
    fun clientScreensGetTheIndexPage() = apiTest(config = webConfig) {
        for (path in listOf("/", "/penalties", "/penalties/42", "/login")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertEquals("<html>SUDV</html>", response.bodyAsText(), path)
        }
    }

    @Test
    fun scriptsAndWasmHaveContentTypesBrowsersAccept() = apiTest(config = webConfig) {
        assertEquals(ContentType.Text.JavaScript, client.get("/web-app.mjs").contentType()?.withoutParameters())
        assertEquals(ContentType("application", "wasm"), client.get("/web-app.wasm").contentType()?.withoutParameters())
    }

    @Test
    fun serverPathsAreNotTakenOver() = apiTest(config = webConfig) {
        assertEquals("OK", client.get("/health").bodyAsText())
        val unknownApi = client.get("/api/no-such-route")
        assertEquals(HttpStatusCode.NotFound, unknownApi.status)
        assertTrue("SUDV" !in unknownApi.bodyAsText())
    }

    @Test
    fun withoutAWebDirTheServerOnlyServesTheApi() = apiTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/penalties").status)
    }
}
