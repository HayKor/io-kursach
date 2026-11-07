package ru.haykor.sudv.client.ui.penalties

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
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.HR_DEPARTMENT
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.employees.SEARCH_DEBOUNCE_MS
import ru.haykor.sudv.client.ui.penalty
import ru.haykor.sudv.shared.contract.CreatePenaltyRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.PenaltyDetailsDto
import ru.haykor.sudv.shared.contract.PenaltyFilter
import ru.haykor.sudv.shared.contract.PenaltyRemovalDto
import ru.haykor.sudv.shared.contract.PenaltySortField
import ru.haykor.sudv.shared.contract.SortDirection
import ru.haykor.sudv.shared.domain.EmployeeId
import ru.haykor.sudv.shared.domain.PenaltyId
import ru.haykor.sudv.shared.domain.PenaltyStatus
import ru.haykor.sudv.shared.domain.PenaltyType
import ru.haykor.sudv.shared.domain.UserRole
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PenaltiesComponentTest {
    private val api = FakeApiClient(signedIn = true)
    private val today = LocalDate(2026, 11, 7)

    // Midday UTC is the same date in every time zone the tests could run in.
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-07T12:00:00Z")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.section(role: UserRole = UserRole.HR, deepLink: String? = null): PenaltiesComponent {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return DefaultPenaltiesComponent(DefaultComponentContext(LifecycleRegistry()), api, role, DeepLink.parse(deepLink), clock)
    }

    private val PenaltiesComponent.active get() = stack.value.active.instance
    private val PenaltiesComponent.list get() = (stack.value.items.first().instance as PenaltiesComponent.Child.List).component
    private val PenaltiesComponent.details get() = (active as PenaltiesComponent.Child.Details).component
    private val PenaltiesComponent.form get() = (active as PenaltiesComponent.Child.Form).component
    private val PenaltiesComponent.path get() = webNavigation.history.value.last().path

    private fun details(status: PenaltyStatus) {
        api.onPenalty = { PenaltyDetailsDto(penalty(it.value, status), emptyList(), emptyList()) }
    }

    @Test
    fun registryStartsWithTheDefaultFilter() = runTest {
        val list = section().list

        assertEquals(listOf(PenaltyFilter()), api.penaltyFilters)
        assertEquals(2, (list.state.value.penalties as LoadState.Loaded).data.size)
    }

    @Test
    fun shownFiltersAreWhatGoesToTheServer() = runTest {
        val list = section().list

        list.onStatusChange(PenaltyStatus.ACTIVE)
        list.onDepartmentChange(HR_DEPARTMENT.id)
        list.onOrderDateFromChange(LocalDate(2026, 1, 1))
        list.onOrderDateToChange(LocalDate(2026, 6, 30))
        list.onSortChange(PenaltySortField.EMPLOYEE_NAME)
        list.onDirectionToggle()

        val expected = PenaltyFilter(
            status = PenaltyStatus.ACTIVE,
            departmentId = HR_DEPARTMENT.id,
            orderDateFrom = LocalDate(2026, 1, 1),
            orderDateTo = LocalDate(2026, 6, 30),
            sort = PenaltySortField.EMPLOYEE_NAME,
            direction = SortDirection.ASC,
        )
        assertEquals(expected, api.penaltyFilters.last())
        assertEquals(expected, list.state.value.filter)
        assertEquals(4, list.state.value.activeFilterCount)
    }

    @Test
    fun nameSearchIsTrimmedAndWaitsUntilTypingStops() = runTest {
        val list = section().list
        api.penaltyFilters.clear()

        list.onEmployeeNameChange("Ив")
        list.onEmployeeNameChange("Иванов ")
        advanceTimeBy(SEARCH_DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf(PenaltyFilter(employeeName = "Иванов")), api.penaltyFilters)
    }

    @Test
    fun reversedPeriodIsNotSent() = runTest {
        val list = section().list
        api.penaltyFilters.clear()

        list.onOrderDateFromChange(LocalDate(2026, 6, 1))
        list.onOrderDateToChange(LocalDate(2026, 1, 1))

        assertEquals(1, api.penaltyFilters.size, "only the period start went to the server")
        val failed = assertIs<LoadState.Failed>(list.state.value.penalties)
        assertFalse(failed.canRetry)
    }

    @Test
    fun managerHasNoDepartmentFilterAndCannotRegister() = runTest {
        val component = section(UserRole.MANAGER, deepLink = "new")

        assertIs<PenaltiesComponent.Child.List>(component.active)
        assertFalse(component.list.state.value.canFilterByDepartment)
        assertFalse(component.list.state.value.canCreate)
        assertEquals(0, api.departmentsCalls)
    }

    @Test
    fun deepLinkOpensTheCardOverTheRegistry() = runTest {
        val component = section(deepLink = "42")

        assertEquals(PenaltyId(42), component.details.penaltyId)
        assertEquals("42", component.path)
        assertEquals(listOf(PenaltyId(42)), api.penaltyCalls)
    }

    @Test
    fun foreignPenaltyShowsTheRefusalWithoutRetry() = runTest {
        api.onPenalty = { throw ApiException(ErrorCode.FORBIDDEN, "Нет доступа к данным другого подразделения") }

        val details = section(UserRole.MANAGER, deepLink = "42").details

        val failed = assertIs<LoadState.Failed>(details.state.value.details)
        assertEquals("Нет доступа к данным другого подразделения", failed.message)
        assertFalse(failed.canRetry)
    }

    @Test
    fun openingAnotherPenaltyReplacesTheCard() = runTest {
        val component = section()

        component.list.onPenaltyClick(PenaltyId(1))
        component.list.onPenaltyClick(PenaltyId(2))

        assertEquals(2, component.stack.value.items.size)
        assertEquals(PenaltyId(2), component.details.penaltyId)
    }

    @Test
    fun activePenaltyCountsDaysUntilExpiry() = runTest {
        // Ordered 01.03.2026, so it expires on 01.03.2027: 114 days after 07.11.2026.
        val details = section(deepLink = "1").details

        assertEquals(114, details.state.value.remainingDays)
        assertTrue(details.state.value.canRemove)
    }

    @Test
    fun onlyAnActivePenaltyCanBeRemovedAndOnlyByHr() = runTest {
        details(PenaltyStatus.EXPIRED)
        val expired = section(deepLink = "1").details
        assertFalse(expired.state.value.canRemove)
        assertNull(expired.state.value.remainingDays)
        expired.onRemoveClick()
        assertNull(expired.state.value.removal)

        details(PenaltyStatus.REMOVED)
        assertFalse(section(deepLink = "2").details.state.value.canRemove)

        details(PenaltyStatus.ACTIVE)
        assertFalse(section(UserRole.MANAGER, deepLink = "3").details.state.value.canRemove)
    }

    @Test
    fun removalIsCheckedBeforeSending() = runTest {
        val details = section(deepLink = "1").details

        details.onRemoveClick()
        assertEquals(today, details.state.value.removal?.orderDate, "today is offered as the order date")
        details.onRemovalConfirm()

        assertEquals(listOf("Номер приказа не должен быть пустым"), details.state.value.removal?.errors)
        assertTrue(api.removals.isEmpty())
    }

    @Test
    fun removalReloadsTheCardAndTheRegistry() = runTest {
        val component = section(deepLink = "1")
        val details = component.details
        api.penaltyFilters.clear()

        details.onRemoveClick()
        details.onRemovalOrderNumberChange(" 15-к ")
        details.onRemovalConfirm()

        assertEquals(listOf(PenaltyId(1) to PenaltyRemovalDto("15-к", today)), api.removals)
        assertNull(details.state.value.removal)
        assertEquals(2, api.penaltyCalls.size, "the card was fetched again for the new status and history")
        assertEquals(1, api.penaltyFilters.size, "the registry was reloaded")
    }

    @Test
    fun penaltyNoLongerActiveIsReportedAndTheCardRefreshed() = runTest {
        val details = section(deepLink = "1").details
        api.onRemovePenalty = { _, _ -> throw ApiException(ErrorCode.PENALTY_NOT_ACTIVE, "Взыскание уже не действует") }
        details.onRemoveClick()
        details.onRemovalOrderNumberChange("15-к")
        details(PenaltyStatus.EXPIRED)

        details.onRemovalConfirm()

        assertEquals(listOf("Взыскание уже не действует"), details.state.value.removal?.errors)
        assertFalse(details.state.value.removal!!.isSaving)
        assertFalse(details.state.value.canRemove, "the refreshed card shows the penalty expired")
    }

    @Test
    fun registrationFormChecksMissingFieldsFirst() = runTest {
        val component = section(deepLink = "new")
        val form = component.form

        form.onSave()

        assertEquals(
            listOf("Выберите сотрудника", "Выберите вид взыскания", "Укажите дату нарушения", "Укажите дату приказа"),
            form.state.value.errors,
        )
        assertTrue(api.createdPenalties.isEmpty())
    }

    @Test
    fun registrationAppliesTheSharedRules() = runTest {
        val form = section(deepLink = "new").form
        form.onEmployeeChange(EmployeeId(1))
        form.onTypeChange(PenaltyType.REPRIMAND)
        form.onViolationDateChange(LocalDate(2026, 11, 2))
        form.onOrderDateChange(LocalDate(2026, 11, 1))
        form.onOrderNumberChange("12-к")

        form.onSave()

        assertEquals(
            listOf("Описание не должно быть пустым", "Дата нарушения не может быть позже даты приказа"),
            form.state.value.errors,
        )
        assertTrue(api.createdPenalties.isEmpty())
    }

    @Test
    fun registeredPenaltyOpensInPlaceOfTheForm() = runTest {
        val component = section()
        component.list.onCreateClick()
        assertEquals("new", component.path)
        val form = component.form
        api.penaltyFilters.clear()

        form.onEmployeeChange(EmployeeId(1))
        form.onTypeChange(PenaltyType.REMARK)
        form.onViolationDateChange(LocalDate(2026, 11, 1))
        form.onOrderDateChange(LocalDate(2026, 11, 3))
        assertEquals(LocalDate(2027, 11, 3), form.state.value.expiryDate)
        form.onDescriptionChange(" Опоздание ")
        form.onOrderNumberChange("12-к")
        form.onSave()

        assertEquals(
            listOf(CreatePenaltyRequest(EmployeeId(1), PenaltyType.REMARK, LocalDate(2026, 11, 1), "Опоздание", "12-к", LocalDate(2026, 11, 3))),
            api.createdPenalties,
        )
        assertEquals(PenaltyId(77), component.details.penaltyId)
        assertEquals(2, component.stack.value.items.size)
        assertEquals(1, api.penaltyFilters.size, "the registry was reloaded")
    }
}
