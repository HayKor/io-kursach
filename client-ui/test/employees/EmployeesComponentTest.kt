package ru.haykor.sudv.client.ui.employees

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.EmployeesQuery
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.HR_DEPARTMENT
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmployeesComponentTest {
    private val api = FakeApiClient(signedIn = true)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Components launch on Dispatchers.Main; sharing the test scheduler lets the debounce run on virtual time.
    private fun TestScope.section(role: UserRole = UserRole.HR, deepLink: String? = null): EmployeesComponent {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return DefaultEmployeesComponent(
            DefaultComponentContext(LifecycleRegistry()),
            api,
            role,
            DeepLink.parse(deepLink),
        )
    }

    private val EmployeesComponent.active get() = stack.value.active.instance
    private val EmployeesComponent.list get() = (stack.value.items.first().instance as EmployeesComponent.Child.List).component
    private val EmployeesComponent.path get() = webNavigation.history.value.last().path

    @Test
    fun listLoadsActiveEmployees() = runTest {
        val list = section().list

        assertEquals(listOf(EmployeesQuery(null, null, false)), api.employeesQueries)
        assertEquals(2, (list.state.value.employees as LoadState.Loaded).data.size)
    }

    @Test
    fun filtersGoToTheServer() = runTest {
        val list = section().list

        list.onDepartmentChange(HR_DEPARTMENT.id)
        list.onIncludeArchivedChange(true)

        assertEquals(EmployeesQuery(null, HR_DEPARTMENT.id, true), api.employeesQueries.last())
    }

    @Test
    fun searchWaitsUntilTypingStops() = runTest {
        val list = section().list
        api.employeesQueries.clear()

        list.onQueryChange("Ив")
        advanceTimeBy(SEARCH_DEBOUNCE_MS / 2)
        list.onQueryChange("Иванов ")
        advanceTimeBy(SEARCH_DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf(EmployeesQuery("Иванов", null, false)), api.employeesQueries)
    }

    @Test
    fun managerHasNoDepartmentFilterAndCannotCreate() = runTest {
        val state = section(UserRole.MANAGER).list.state.value

        assertFalse(state.canFilterByDepartment)
        assertFalse(state.canCreate)
        assertEquals(0, api.departmentsCalls)
    }

    @Test
    fun adminCanCreateButNotEdit() = runTest {
        val section = section(UserRole.ADMIN)
        assertTrue(section.list.state.value.canCreate)

        section.list.onEmployeeClick(EmployeeId(1))

        assertFalse(assertIs<EmployeesComponent.Child.Details>(section.active).component.state.value.canEdit)
    }

    @Test
    fun deepLinksOpenCardAndForm() = runTest {
        val card = section(deepLink = "/7")
        assertEquals(EmployeeId(7), assertIs<EmployeesComponent.Child.Details>(card.active).component.employeeId)
        assertEquals("7", card.path)

        val edit = section(deepLink = "/7/edit")
        assertIs<EmployeesComponent.Child.Form>(edit.active)
        assertEquals("7/edit", edit.path)

        assertIs<EmployeesComponent.Child.Form>(section(deepLink = "/new").active)
    }

    @Test
    fun deepLinksIgnoreFormsTheRoleCannotUse() = runTest {
        assertIs<EmployeesComponent.Child.Details>(section(UserRole.MANAGER, "/7/edit").active)
        assertIs<EmployeesComponent.Child.List>(section(UserRole.MANAGER, "/new").active)
    }

    @Test
    fun openingAnotherEmployeeReplacesTheCard() = runTest {
        val section = section()

        section.list.onEmployeeClick(EmployeeId(1))
        section.list.onEmployeeClick(EmployeeId(2))

        assertEquals(2, section.stack.value.items.size)
        assertEquals(EmployeeId(2), assertIs<EmployeesComponent.Child.Details>(section.active).component.employeeId)
    }

    @Test
    fun emptyFormIsNotSent() = runTest {
        val section = section()
        section.list.onCreateClick()
        val form = assertIs<EmployeesComponent.Child.Form>(section.active).component

        form.onSave()

        assertTrue("Табельный номер не должен быть пустым" in form.state.value.errors)
        assertTrue("Выберите подразделение" in form.state.value.errors)
        assertTrue(api.createdEmployees.isEmpty())
    }

    @Test
    fun createdEmployeeOpensInTheCardAndTheListReloads() = runTest {
        val section = section()
        section.list.onCreateClick()
        val form = assertIs<EmployeesComponent.Child.Form>(section.active).component
        val listLoads = api.employeesQueries.size

        form.onPersonnelNumberChange(" EMP-99 ")
        form.onLastNameChange("Сидоров")
        form.onFirstNameChange("Олег")
        form.onPositionChange("Юрист")
        form.onDepartmentChange(HR_DEPARTMENT.id)
        form.onSave()

        val request = api.createdEmployees.single()
        assertEquals("EMP-99", request.personnelNumber)
        assertEquals(null, request.middleName)
        assertEquals(EmployeeId(99), assertIs<EmployeesComponent.Child.Details>(section.active).component.employeeId)
        assertEquals(listLoads + 1, api.employeesQueries.size)
    }

    @Test
    fun serverRejectionStaysOnTheForm() = runTest {
        api.onCreateEmployee = { throw ApiException(ErrorCode.DUPLICATE_PERSONNEL_NUMBER, "Табельный номер уже занят") }
        val section = section(deepLink = "/new")
        val form = assertIs<EmployeesComponent.Child.Form>(section.active).component
        form.onPersonnelNumberChange("EMP-1")
        form.onLastNameChange("Иванов")
        form.onFirstNameChange("Иван")
        form.onPositionChange("Инженер")
        form.onDepartmentChange(HR_DEPARTMENT.id)

        form.onSave()

        assertEquals(listOf("Табельный номер уже занят"), form.state.value.errors)
        assertFalse(form.state.value.isSaving)
        assertIs<EmployeesComponent.Child.Form>(section.active)
    }

    @Test
    fun editPrefillsAndReturnsToTheRefreshedCard() = runTest {
        val section = section(deepLink = "/7/edit")
        val form = assertIs<EmployeesComponent.Child.Form>(section.active).component
        assertEquals("EMP-7", form.state.value.personnelNumber)
        val cardLoads = api.employeeCalls.count { it == EmployeeId(7) }

        form.onLastNameChange("Новикова")
        form.onSave()

        assertEquals("Новикова", api.updatedEmployees.single().second.lastName)
        assertIs<EmployeesComponent.Child.Details>(section.active)
        assertEquals(cardLoads + 1, api.employeeCalls.count { it == EmployeeId(7) })
    }

    @Test
    fun archiveNeedsConfirmationAndRefreshesTheList() = runTest {
        val section = section(deepLink = "/7")
        val card = assertIs<EmployeesComponent.Child.Details>(section.active).component
        val listLoads = api.employeesQueries.size

        card.onArchiveClick()
        assertTrue(card.state.value.confirmingArchive)
        assertTrue(api.archivedEmployees.isEmpty())
        card.onArchiveConfirm()

        assertEquals(listOf(EmployeeId(7)), api.archivedEmployees)
        assertFalse(card.state.value.canArchive)
        assertEquals(listLoads + 1, api.employeesQueries.size)
    }
}
