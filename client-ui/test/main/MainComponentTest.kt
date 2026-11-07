package ru.haykor.sudv.client.ui.main

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.TEST_USER
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class MainComponentTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun main(role: UserRole = UserRole.HR, deepLink: DeepLink = DeepLink.None) = DefaultMainComponent(
        DefaultComponentContext(LifecycleRegistry()),
        FakeApiClient(signedIn = true),
        TEST_USER.copy(role = role),
        deepLink,
    )

    private val MainComponent.active get() = stack.value.active.instance

    @Test
    fun startsOnTheRolesFirstSection() {
        assertEquals(Section.PENALTIES, main(UserRole.HR).active.section)
        assertEquals(Section.USERS, main(UserRole.ADMIN).active.section)
    }

    @Test
    fun deepLinkPicksTheSection() {
        assertEquals(Section.REPORTS, main(UserRole.HR, DeepLink.parse("/reports")).active.section)
    }

    @Test
    fun deepLinkToASectionTheRoleCannotOpenFallsBackToTheStart() {
        assertEquals(Section.PENALTIES, main(UserRole.MANAGER, DeepLink.parse("/reports")).active.section)
    }

    @Test
    fun aSectionKeepsItsComponentWhileAnotherIsOpen() {
        val main = main()
        val penalties = main.active

        main.onSectionClick(Section.EMPLOYEES)
        assertEquals(Section.EMPLOYEES, main.active.section)
        main.onSectionClick(Section.PENALTIES)

        assertSame(penalties, main.active)
    }

    @Test
    fun aSectionOutsideTheRoleIsIgnored() {
        val main = main(UserRole.MANAGER)

        main.onSectionClick(Section.USERS)

        assertEquals(Section.PENALTIES, main.active.section)
    }

    @Test
    fun theUrlShowsOnlyTheOpenSection() {
        val main = main()

        main.onSectionClick(Section.EMPLOYEES)
        main.onSectionClick(Section.REPORTS)

        assertEquals(listOf("reports"), main.webNavigation.history.value.map { it.path })
    }
}
