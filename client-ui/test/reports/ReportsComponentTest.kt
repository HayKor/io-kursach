package ru.haykor.sudv.client.ui.reports

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import ru.haykor.sudv.client.domain.ApiException
import ru.haykor.sudv.client.ui.DeepLink
import ru.haykor.sudv.client.ui.FakeApiClient
import ru.haykor.sudv.client.ui.HR_DEPARTMENT
import ru.haykor.sudv.client.ui.IT_DEPARTMENT
import ru.haykor.sudv.client.ui.SummaryQuery
import ru.haykor.sudv.client.ui.common.FileAction
import ru.haykor.sudv.client.ui.common.LoadState
import ru.haykor.sudv.client.ui.report
import ru.haykor.sudv.shared.contract.CreateReportRequest
import ru.haykor.sudv.shared.contract.ErrorCode
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.domain.ReportFormat
import ru.haykor.sudv.shared.domain.ReportId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsComponentTest {
    private val api = FakeApiClient(signedIn = true)

    // Midday UTC is the same date in every time zone the tests could run in.
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-11-08T12:00:00Z")
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.section(deepLink: String? = null): ReportsComponent {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return DefaultReportsComponent(DefaultComponentContext(LifecycleRegistry()), api, DeepLink.parse(deepLink), clock)
    }

    private val ReportsComponent.active get() = stack.value.active.instance
    private val ReportsComponent.summary get() = (stack.value.items.first().instance as ReportsComponent.Child.Summary).component
    private val ReportsComponent.archive get() = assertIs<ReportsComponent.Child.Archive>(active).component
    private val ReportsComponent.path get() = webNavigation.history.value.last().path

    @Test
    fun periodStartsWithTheCurrentYearSoFar() = runTest {
        val state = section().summary.state.value

        assertEquals(LocalDate(2026, 1, 1), state.periodFrom)
        assertEquals(LocalDate(2026, 11, 8), state.periodTo)
        assertTrue(state.selectedDepartmentIds.isEmpty(), "all departments by default")
    }

    @Test
    fun missingPeriodIsAFormError() = runTest {
        val summary = section().summary

        summary.onPeriodFromChange(null)
        summary.onBuild()

        assertEquals(listOf("Укажите начало и конец периода"), summary.state.value.errors)
        assertTrue(api.summaryQueries.isEmpty())
    }

    @Test
    fun reversedPeriodIsAFormError() = runTest {
        val summary = section().summary

        summary.onPeriodFromChange(LocalDate(2026, 12, 1))
        summary.onBuild()

        assertEquals(listOf("Дата начала периода не может быть позже даты окончания"), summary.state.value.errors)
        assertTrue(api.summaryQueries.isEmpty())
    }

    @Test
    fun selectedDepartmentsGoToTheServerAndAllMeansNone() = runTest {
        val summary = section().summary

        summary.onDepartmentToggle(HR_DEPARTMENT.id)
        summary.onDepartmentToggle(IT_DEPARTMENT.id)
        summary.onBuild()
        assertEquals(listOf(IT_DEPARTMENT.id, HR_DEPARTMENT.id), api.summaryQueries.last().departmentIds)

        summary.onDepartmentToggle(IT_DEPARTMENT.id)
        summary.onBuild()
        assertEquals(listOf(HR_DEPARTMENT.id), api.summaryQueries.last().departmentIds)

        summary.onAllDepartmentsClick()
        summary.onBuild()
        assertEquals(SummaryQuery(LocalDate(2026, 1, 1), LocalDate(2026, 11, 8), emptyList()), api.summaryQueries.last())
    }

    @Test
    fun archiveGetsTheParametersOfTheShownReport() = runTest {
        val summary = section().summary
        summary.onPeriodFromChange(LocalDate(2026, 3, 1))
        summary.onDepartmentToggle(HR_DEPARTMENT.id)
        summary.onBuild()
        assertEquals(2, summary.state.value.report?.summary?.rows?.size)

        summary.onSave(ReportFormat.CSV)

        assertEquals(
            listOf(CreateReportRequest(LocalDate(2026, 3, 1), LocalDate(2026, 11, 8), listOf(HR_DEPARTMENT.id), ReportFormat.CSV)),
            api.createdReports,
        )
        assertEquals(ReportId(30), summary.state.value.saved?.id)
    }

    @Test
    fun changedParametersHideTheShownReport() = runTest {
        val summary = section().summary
        summary.onBuild()
        summary.onSave(ReportFormat.XLSX)
        assertNotNull(summary.state.value.saved)

        summary.onDepartmentToggle(IT_DEPARTMENT.id)

        // Otherwise «Сохранить» would archive parameters different from the table on the screen.
        assertNull(summary.state.value.report)
        assertNull(summary.state.value.saved)
        summary.onSave(ReportFormat.XLSX)
        assertEquals(1, api.createdReports.size)
    }

    @Test
    fun savedReportCanBeDownloaded() = runTest {
        val summary = section().summary
        summary.onBuild()
        summary.onSave(ReportFormat.XLSX)

        summary.onDownloadSaved()

        assertEquals(listOf(ReportId(30)), api.reportDownloads)
        assertEquals(FileAction.SAVE, summary.state.value.delivery?.action)
    }

    @Test
    fun serverRefusalStaysOnTheForm() = runTest {
        api.onSummary = { throw ApiException(ErrorCode.VALIDATION_FAILED, "Подразделение не найдено") }
        val summary = section().summary

        summary.onBuild()

        assertEquals(listOf("Подразделение не найдено"), summary.state.value.errors)
        assertNull(summary.state.value.report)
    }

    @Test
    fun archiveOpensFromTheSummaryAndByItsAddress() = runTest {
        val component = section()
        assertEquals("", component.path)

        component.summary.onArchiveClick()
        assertEquals("archive", component.path)

        val fromLink = section(deepLink = "/archive")
        assertIs<ReportsComponent.Child.Archive>(fromLink.active)
        assertEquals(2, fromLink.stack.value.items.size, "back from the archive leads to the summary")
    }

    @Test
    fun archiveListsNewestFirstAndDownloadsAgain() = runTest {
        api.onReports = {
            listOf(
                report(1, LocalDate(2026, 1, 1), LocalDate(2026, 3, 31), createdAt = "2026-04-01T09:00:00Z"),
                report(2, LocalDate(2026, 1, 1), LocalDate(2026, 6, 30), createdAt = "2026-07-01T09:00:00Z"),
            )
        }
        val archive = section(deepLink = "/archive").archive

        val shown = archive.state.value.reports
        assertEquals(listOf(ReportId(2), ReportId(1)), assertIs<LoadState.Loaded<List<ReportDto>>>(shown).data.map { it.id })

        archive.onDownloadClick(ReportId(1))
        assertEquals(listOf(ReportId(1)), api.reportDownloads)
        assertEquals("summary_1.xlsx", archive.state.value.delivery?.file?.fileName)
    }
}
