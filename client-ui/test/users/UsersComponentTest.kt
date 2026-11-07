package ru.haykor.sudv.client.ui.users

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.account
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.employee
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UsersComponentTest {
    private val api = FakeApiClient(signedIn = true)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun section(deepLink: String? = null) = DefaultUsersComponent(
        DefaultComponentContext(LifecycleRegistry()),
        api,
        currentUserId = UserId(1),
        deepLink = DeepLink.parse(deepLink),
    )

    private val UsersComponent.active get() = stack.value.active.instance
    private val UsersComponent.list get() = (stack.value.items.first().instance as UsersComponent.Child.List).component
    private val UserListComponent.users get() = (state.value.users as LoadState.Loaded).data

    @Test
    fun blockingAsksFirstAndUpdatesTheRow() {
        val list = section().list
        val hr = list.users.single { it.login == "hr1" }

        list.onBlockClick(hr)
        assertTrue(api.blockCalls.isEmpty())
        list.onBlockConfirm()

        assertEquals(listOf(UserId(2) to true), api.blockCalls)
        assertTrue(list.users.single { it.id == UserId(2) }.isBlocked)
        assertNull(list.state.value.confirmingBlock)
    }

    @Test
    fun unblockingNeedsNoConfirmation() {
        api.onUsers = { listOf(account(1, "admin", UserRole.ADMIN), account(2, "hr1", isBlocked = true)) }
        val list = section().list

        list.onUnblockClick(list.users.single { it.login == "hr1" })

        assertEquals(listOf(UserId(2) to false), api.blockCalls)
        assertEquals(false, list.users.single { it.id == UserId(2) }.isBlocked)
    }

    @Test
    fun serverRefusalIsShown() {
        api.onSetBlocked = { _, _ -> throw ApiException(ErrorCode.VALIDATION_FAILED, "Нельзя блокировать самого себя") }
        val list = section().list

        list.onBlockClick(list.users.first())
        list.onBlockConfirm()

        assertEquals("Нельзя блокировать самого себя", list.state.value.actionError)
        assertNull(list.state.value.busyUserId)
    }

    @Test
    fun formOffersOnlyEmployeesWithoutAnAccount() {
        // Accounts exist for employees 1 and 2; the form must offer only 3.
        api.onEmployees = { listOf(employee(1), employee(2), employee(3, "Сидоров")) }
        val form = assertIs<UsersComponent.Child.Form>(section("/new").active).component

        assertEquals(listOf(EmployeeId(3)), (form.state.value.employees as LoadState.Loaded).data.map { it.id })
    }

    @Test
    fun incompleteFormIsNotSent() {
        val form = assertIs<UsersComponent.Child.Form>(section("/new").active).component

        form.onLoginChange("x")
        form.onSave()

        val errors = form.state.value.errors
        assertTrue("Выберите сотрудника" in errors)
        assertTrue("Выберите роль" in errors)
        assertTrue("Логин должен быть от 3 до 50 символов" in errors)
        assertTrue("Пароль должен быть не короче 8 символов" in errors)
        assertTrue(api.createdUsers.isEmpty())
    }

    @Test
    fun createdAccountReturnsToTheReloadedList() {
        api.onEmployees = { listOf(employee(3, "Сидоров")) }
        val section = section()
        section.list.onCreateClick()
        val form = assertIs<UsersComponent.Child.Form>(section.active).component
        val listLoads = api.usersCalls

        form.onEmployeeChange(EmployeeId(3))
        form.onRoleChange(UserRole.MANAGER)
        form.onLoginChange(" manager2 ")
        form.onPasswordChange("password123")
        form.onSave()

        val request = api.createdUsers.single()
        assertEquals("manager2", request.login)
        assertEquals(UserRole.MANAGER, request.role)
        assertEquals(EmployeeId(3), request.employeeId)
        assertIs<UsersComponent.Child.List>(section.active)
        assertTrue(api.usersCalls > listLoads)
    }

    @Test
    fun duplicateLoginStaysOnTheForm() {
        api.onEmployees = { listOf(employee(3)) }
        api.onCreateUser = { throw ApiException(ErrorCode.DUPLICATE_LOGIN, "Логин уже занят") }
        val section = section("/new")
        val form = assertIs<UsersComponent.Child.Form>(section.active).component
        form.onEmployeeChange(EmployeeId(3))
        form.onRoleChange(UserRole.HR)
        form.onLoginChange("hr1")
        form.onPasswordChange("password123")

        form.onSave()

        assertEquals(listOf("Логин уже занят"), form.state.value.errors)
        assertIs<UsersComponent.Child.Form>(section.active)
        assertEquals("new", section.webNavigation.history.value.last().path)
    }
}
