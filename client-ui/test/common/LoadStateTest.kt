package ru.haykor.sudv.client.ui.common

import kotlinx.coroutines.test.runTest
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.domain.NetworkException
import ru.haykor.sudv.shared.contract.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadStateTest {
    @Test
    fun resultIsLoaded() = runTest {
        assertEquals(LoadState.Loaded(42), load { 42 })
    }

    @Test
    fun clientFailuresKeepTheirMessage() = runTest {
        assertEquals(
            LoadState.Failed("Недостаточно прав"),
            load { throw ApiException(ErrorCode.FORBIDDEN, "Недостаточно прав") },
        )
        assertEquals(LoadState.Failed("Нет связи с сервером"), load { throw NetworkException(RuntimeException()) })
    }
}
