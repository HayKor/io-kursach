package ru.haykor.sudv.server.auth

import ru.haykor.sudv.server.auth.domain.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    @Test
    fun verifyAcceptsCorrectPasswordAndRejectsWrongOne() {
        val hash = PasswordHasher.hash("correct-password", iterations = 1000)
        assertTrue(PasswordHasher.verify("correct-password", hash))
        assertFalse(PasswordHasher.verify("wrong-password", hash))
    }

    @Test
    fun samePasswordHashesDifferentlyEachTime() {
        val first = PasswordHasher.hash("same-password", iterations = 1000)
        val second = PasswordHasher.hash("same-password", iterations = 1000)
        assertNotEquals(first, second, "a random salt must make each hash unique")
        assertTrue(PasswordHasher.verify("same-password", first))
        assertTrue(PasswordHasher.verify("same-password", second))
    }
}
