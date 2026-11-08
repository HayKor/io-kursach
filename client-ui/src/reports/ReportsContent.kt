package ru.haykor.sudv.client.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.jetbrains.compose.resources.painterResource
import ru.haykor.sudv.client.ui.common.DateField
import ru.haykor.sudv.client.ui.common.FormatButton
import ru.haykor.sudv.client.ui.common.LoadStateContent
import ru.haykor.sudv.client.ui.common.PaneHeader
import ru.haykor.sudv.client.ui.common.deliverFile
import ru.haykor.sudv.client.ui.common.display
import ru.haykor.sudv.client.ui.common.label
import ru.haykor.sudv.client.ui.resources.Res
import ru.haykor.sudv.client.ui.resources.ic_arrow_back
import ru.haykor.sudv.client.ui.resources.ic_download
import ru.haykor.sudv.client.ui.resources.ic_inventory
import ru.haykor.sudv.client.ui.resources.ic_refresh
import ru.haykor.sudv.shared.contract.ReportDto
import ru.haykor.sudv.shared.contract.SummaryReportDto

@Composable
fun ReportsContent(component: ReportsComponent, modifier: Modifier = Modifier) {
    Children(stack = component.stack, modifier = modifier) { child ->
        when (val instance = child.instance) {
            is ReportsComponent.Child.Summary -> SummaryContent(instance.component)
            is ReportsComponent.Child.Archive -> ArchiveContent(instance.component)
        }
    }
}

@Composable
private fun SummaryContent(component: SummaryComponent) {
    val state by component.state.subscribeAsState()
    val deliveryError = deliverFile(state.delivery, component::onDelivered).takeIf { state.delivery == null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Сводный отчёт", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = component::onArchiveClick) {
                Icon(painterResource(Res.drawable.ic_inventory), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Архив", modifier = Modifier.padding(start = 8.dp))
            }
        }
        LoadStateContent(state.departments, onRetry = component::onRetry) { departments ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Число взысканий по подразделениям за нарушения, совершённые в указанный период",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(state.periodFrom, component::onPeriodFromChange, "Период с", Modifier.weight(1f))
                    DateField(state.periodTo, component::onPeriodToChange, "по", Modifier.weight(1f))
                }
                Column {
                    Text("Подразделения", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.selectedDepartmentIds.isEmpty(),
                            onClick = component::onAllDepartmentsClick,
                            label = { Text("Все") },
                        )
                        departments.forEach { department ->
                            FilterChip(
                                selected = department.id in state.selectedDepartmentIds,
                                onClick = { component.onDepartmentToggle(department.id) },
                                label = { Text(department.name) },
                            )
                        }
                    }
                }
                Button(onClick = component::onBuild, enabled = !state.isBuilding, modifier = Modifier.fillMaxWidth()) {
                    if (state.isBuilding) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Сформировать")
                }
                (state.errors + listOfNotNull(deliveryError)).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                state.report?.let { report ->
                    SummaryTable(report.summary)
                    val saved = state.saved
                    if (saved == null) {
                        FormatButton("Сохранить в архив", onSelect = component::onSave, busy = state.isSaving)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Сохранён в архив (${saved.format.label})", modifier = Modifier.weight(1f))
                            TextButton(onClick = component::onDownloadSaved, enabled = !state.isDownloading) {
                                if (state.isDownloading) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Скачать")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The same columns as the saved file, with a total row. */
@Composable
private fun SummaryTable(summary: SummaryReportDto) {
    Column {
        Text(
            "С ${summary.periodFrom.display()} по ${summary.periodTo.display()}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TableRow("Подразделение", "Замечаний", "Выговоров", "Всего", header = true)
        HorizontalDivider()
        summary.rows.forEach { row ->
            TableRow(row.department.name, "${row.remarkCount}", "${row.reprimandCount}", "${row.totalCount}")
        }
        HorizontalDivider()
        TableRow(
            "Итого",
            "${summary.rows.sumOf { it.remarkCount }}",
            "${summary.rows.sumOf { it.reprimandCount }}",
            "${summary.rows.sumOf { it.totalCount }}",
            header = true,
        )
    }
}

@Composable
private fun TableRow(name: String, remarks: String, reprimands: String, total: String, header: Boolean = false) {
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    val style = MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, fontWeight = weight, modifier = Modifier.weight(2f))
        listOf(remarks, reprimands, total).forEach {
            Text(it, style = style, fontWeight = weight, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArchiveContent(component: ArchiveComponent) {
    val state by component.state.subscribeAsState()
    val deliveryError = deliverFile(state.delivery, component::onDelivered).takeIf { state.delivery == null }
    Column(Modifier.fillMaxSize()) {
        PaneHeader(
            title = "Архив отчётов",
            navigationIcon = Res.drawable.ic_arrow_back,
            navigationDescription = "Назад",
            onNavigate = component::onCloseClick,
        ) {
            IconButton(onClick = component::onRefresh) {
                Icon(painterResource(Res.drawable.ic_refresh), contentDescription = "Обновить")
            }
        }
        (state.error ?: deliveryError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        LoadStateContent(state.reports, onRetry = component::onRefresh) { reports ->
            if (reports.isEmpty()) {
                Text("Архив пуст: сохраните сводный отчёт", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(reports, key = { it.id.value }) { report ->
                        ArchiveRow(report, busy = report.id == state.busyId) { component.onDownloadClick(report.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(report: ReportDto, busy: Boolean, onDownload: () -> Unit) {
    ListItem(
        headlineContent = { Text("С ${report.periodFrom.display()} по ${report.periodTo.display()}") },
        supportingContent = {
            Column {
                Text(report.departments.joinToString { it.name }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${report.format.label} · создан ${report.createdAt.display()}")
            }
        },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                IconButton(onClick = onDownload) {
                    Icon(painterResource(Res.drawable.ic_download), contentDescription = "Скачать")
                }
            }
        },
    )
}
