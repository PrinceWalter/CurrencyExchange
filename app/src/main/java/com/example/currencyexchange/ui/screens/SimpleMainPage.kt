package com.example.currencyexchange.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.currencyexchange.viewmodel.MainViewModel
import com.example.currencyexchange.viewmodel.AnalysisResult
import com.example.currencyexchange.data.dao.PartnerSummary
import com.example.currencyexchange.data.backup.RestoreResult
import com.example.currencyexchange.ui.components.NetPositionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleMainPage(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // Collect data from ViewModel
    val partners by viewModel.partners.collectAsState(initial = emptyList())
    val selectedPartnerId by viewModel.selectedPartnerId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredPartners by viewModel.filteredPartners.collectAsState(initial = emptyList())

    // UI state
    var showAddPartnerDialog by remember { mutableStateOf(false) }
    var showEditPartnerDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var partnerToDelete by remember { mutableStateOf(0L) }
    var partnerToEdit by remember { mutableStateOf(0L) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var newPartnerName by remember { mutableStateOf("") }
    var editPartnerName by remember { mutableStateOf("") }
    var partnerNameError by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Analysis states
    var showAnalysisDialog by remember { mutableStateOf(false) }
    var showAnalysisStartDatePicker by remember { mutableStateOf(false) }
    var showAnalysisEndDatePicker by remember { mutableStateOf(false) }
    var analysisStartDate by remember { mutableStateOf(Date()) } // Default to today
    var analysisEndDate by remember { mutableStateOf(Date()) } // Default to today (single day analysis)
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var isPerformingAnalysis by remember { mutableStateOf(false) }
    var showAnalysisResultDialog by remember { mutableStateOf(false) }

    // Backup and Restore UI state
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showRestoreResultDialog by remember { mutableStateOf(false) }
    var isCreatingBackup by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<RestoreResult?>(null) }
    var backupMessage by remember { mutableStateOf("") }

    // Export state
    var isExportingReport by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    var showExportResultDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Helper function to check if partner name already exists
    fun isPartnerNameTaken(name: String, excludeId: Long = -1): Boolean {
        return partners.any {
            it.name.trim().equals(name.trim(), ignoreCase = true) && it.id != excludeId
        }
    }

    // Helper function to validate partner name
    fun validatePartnerName(name: String, excludeId: Long = -1): String {
        return when {
            name.isBlank() -> "Partner name cannot be empty"
            name.length < 2 -> "Partner name must be at least 2 characters"
            name.length > 50 -> "Partner name cannot exceed 50 characters"
            isPartnerNameTaken(name, excludeId) -> "A partner with this name already exists"
            else -> ""
        }
    }

    // Date range states
    var startDate by remember { mutableStateOf(Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))) }
    var endDate by remember { mutableStateOf(Date()) }

    // File picker for backup export
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isCreatingBackup = true
                try {
                    val backupData = viewModel.createBackup()
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val success = viewModel.exportBackupToFile(backupData, outputStream as java.io.FileOutputStream)
                        backupMessage = if (success) {
                            "Backup exported successfully!\n\nPartners: ${backupData.partners.size}\nTransactions: ${backupData.transactions.size}"
                        } else {
                            "Failed to export backup. Please try again."
                        }
                        showBackupDialog = true
                    }
                } catch (e: Exception) {
                    backupMessage = "Error creating backup: ${e.message}"
                    showBackupDialog = true
                } finally {
                    isCreatingBackup = false
                }
            }
        }
    }

    // File picker for backup import
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isRestoringBackup = true
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val backupData = viewModel.parseBackupFromFile(inputStream)
                        if (backupData != null) {
                            restoreResult = viewModel.restoreBackup(backupData)
                            showRestoreResultDialog = true
                        } else {
                            restoreResult = RestoreResult(
                                success = false,
                                message = "Invalid backup file format",
                                errors = listOf("Could not parse the backup file")
                            )
                            showRestoreResultDialog = true
                        }
                    }
                } catch (e: Exception) {
                    restoreResult = RestoreResult(
                        success = false,
                        message = "Error reading backup file: ${e.message}",
                        errors = listOf(e.message ?: "Unknown error")
                    )
                    showRestoreResultDialog = true
                } finally {
                    isRestoringBackup = false
                }
            }
        }
    }

    // File picker for PDF export
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isExportingReport = true
                try {
                    val transactions = viewModel.getTransactionsByDateRange(selectedPartnerId, startDate, endDate)
                    val summary = viewModel.getPartnerSummaryByDateRange(selectedPartnerId, startDate, endDate)
                    val partnerName = partners.find { it.id == selectedPartnerId }?.name ?: "Partner"
                    val outputStream = context.contentResolver.openOutputStream(uri)

                    if (outputStream != null) {
                        val success = viewModel.exportDateRangeToPdf(
                            outputStream = outputStream,
                            partnerName = partnerName,
                            transactions = transactions,
                            startDate = startDate,
                            endDate = endDate,
                            summary = summary
                        )

                        exportMessage = if (success) {
                            "PDF report exported successfully!\n\nTransactions: ${transactions.size}\nDate Range: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(startDate)} - ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(endDate)}"
                        } else {
                            "Failed to export PDF report. Please try again."
                        }
                        showExportResultDialog = true
                        showDateRangeDialog = false
                    }
                } catch (e: Exception) {
                    exportMessage = "Error exporting PDF: ${e.message}"
                    showExportResultDialog = true
                } finally {
                    isExportingReport = false
                }
            }
        }
    }

    // File picker for Excel export
    val excelExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isExportingReport = true
                try {
                    val transactions = viewModel.getTransactionsByDateRange(selectedPartnerId, startDate, endDate)
                    val summary = viewModel.getPartnerSummaryByDateRange(selectedPartnerId, startDate, endDate)
                    val partnerName = partners.find { it.id == selectedPartnerId }?.name ?: "Partner"
                    val outputStream = context.contentResolver.openOutputStream(uri)

                    if (outputStream != null) {
                        val success = viewModel.exportDateRangeToExcel(
                            outputStream = outputStream,
                            partnerName = partnerName,
                            transactions = transactions,
                            startDate = startDate,
                            endDate = endDate,
                            summary = summary
                        )

                        exportMessage = if (success) {
                            "Excel report exported successfully!\n\nTransactions: ${transactions.size}\nDate Range: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(startDate)} - ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(endDate)}\n\nThe Excel file includes formulas for automatic calculations."
                        } else {
                            "Failed to export Excel report. Please try again."
                        }
                        showExportResultDialog = true
                        showDateRangeDialog = false
                    }
                } catch (e: Exception) {
                    exportMessage = "Error exporting Excel: ${e.message}"
                    showExportResultDialog = true
                } finally {
                    isExportingReport = false
                }
            }
        }
    }

    // Analysis Start Date Picker Dialog
    if (showAnalysisStartDatePicker) {
        DatePickerDialog(
            currentDate = analysisStartDate,
            onDateSelected = { analysisStartDate = it },
            onDismiss = { showAnalysisStartDatePicker = false },
            title = "Select Analysis Start Date"
        )
    }

    // Analysis End Date Picker Dialog
    if (showAnalysisEndDatePicker) {
        DatePickerDialog(
            currentDate = analysisEndDate,
            onDateSelected = { analysisEndDate = it },
            onDismiss = { showAnalysisEndDatePicker = false },
            title = "Select Analysis End Date"
        )
    }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        DatePickerDialog(
            currentDate = startDate,
            onDateSelected = { startDate = it },
            onDismiss = { showStartDatePicker = false },
            title = "Select Start Date"
        )
    }

    // End Date Picker Dialog
    if (showEndDatePicker) {
        DatePickerDialog(
            currentDate = endDate,
            onDateSelected = { endDate = it },
            onDismiss = { showEndDatePicker = false },
            title = "Select End Date"
        )
    }

    // Analysis Dialog
    if (showAnalysisDialog) {
        AlertDialog(
            onDismissRequest = { showAnalysisDialog = false },
            title = { Text("Cross-Partner Analysis") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Analyze total CNY/USDT sold and TZS received across all partners")
                    Text(
                        text = "💡 Tip: Select the same start and end date for single-day analysis",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showAnalysisStartDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(analysisStartDate)}")
                    }

                    OutlinedButton(
                        onClick = { showAnalysisEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("End: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(analysisEndDate)}")
                    }

                    if (analysisStartDate.after(analysisEndDate)) {
                        Text(
                            text = "Warning: Start date is after end date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isSameDay(analysisStartDate, analysisEndDate)) {
                        Text(
                            text = "✓ Single day analysis selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            if (!analysisStartDate.after(analysisEndDate)) {
                                coroutineScope.launch {
                                    isPerformingAnalysis = true
                                    try {
                                        analysisResult = viewModel.performCrossPartnerAnalysis(
                                            startDate = analysisStartDate,
                                            endDate = analysisEndDate
                                        )
                                        showAnalysisResultDialog = true
                                        showAnalysisDialog = false
                                    } catch (e: Exception) {
                                        // Handle error
                                    } finally {
                                        isPerformingAnalysis = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !analysisStartDate.after(analysisEndDate) && !isPerformingAnalysis
                    ) {
                        if (isPerformingAnalysis) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing...")
                        } else {
                            Icon(Icons.Filled.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Perform Analysis")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnalysisDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Analysis Result Dialog
    if (showAnalysisResultDialog && analysisResult != null) {
        // Capture the analysis result to avoid null pointer during dialog lifecycle
        val currentAnalysisResult = analysisResult!!

        AlertDialog(
            onDismissRequest = {
                showAnalysisResultDialog = false
                analysisResult = null
            },
            title = { Text("Analysis Results") },
            text = {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📊 Period: ${currentAnalysisResult.dateRange}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "💰 Total TZS Received",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatNumberWithCommas(currentAnalysisResult.totalTzsReceived),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🇨🇳 CNY Sold",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatNumberWithCommas(currentAnalysisResult.totalCnySold),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "${currentAnalysisResult.cnyTransactions} transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "💵 USDT Sold",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatNumberWithCommas(currentAnalysisResult.totalUsdtSold),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "${currentAnalysisResult.usdtTransactions} transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Divider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📈 Total Transactions:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${currentAnalysisResult.totalTransactions}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAnalysisResultDialog = false
                    analysisResult = null
                }) {
                    Text("Close")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }

    // Add Partner Dialog
    if (showAddPartnerDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddPartnerDialog = false
                newPartnerName = ""
                partnerNameError = ""
            },
            title = { Text("Add New Partner") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPartnerName,
                        onValueChange = {
                            newPartnerName = it
                            partnerNameError = validatePartnerName(it)
                        },
                        label = { Text("Partner Name") },
                        singleLine = true,
                        isError = partnerNameError.isNotEmpty(),
                        supportingText = if (partnerNameError.isNotEmpty()) {
                            { Text(partnerNameError, color = MaterialTheme.colorScheme.error) }
                        } else null
                    )

                    if (partnerNameError.isEmpty() && newPartnerName.isNotBlank()) {
                        Text(
                            text = "✓ Partner name is available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val error = validatePartnerName(newPartnerName)
                        if (error.isEmpty()) {
                            viewModel.addPartner(newPartnerName.trim())
                            showAddPartnerDialog = false
                            newPartnerName = ""
                            partnerNameError = ""
                        } else {
                            partnerNameError = error
                        }
                    },
                    enabled = newPartnerName.isNotBlank() && partnerNameError.isEmpty()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddPartnerDialog = false
                    newPartnerName = ""
                    partnerNameError = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Partner Dialog
    if (showEditPartnerDialog) {
        val partnerName = partners.find { it.id == partnerToEdit }?.name ?: ""
        AlertDialog(
            onDismissRequest = {
                showEditPartnerDialog = false
                editPartnerName = ""
                partnerNameError = ""
                partnerToEdit = 0L
            },
            title = { Text("Edit Partner Name") },
            text = {
                Column {
                    Text(
                        text = "Current name: $partnerName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editPartnerName,
                        onValueChange = {
                            editPartnerName = it
                            partnerNameError = validatePartnerName(it, partnerToEdit)
                        },
                        label = { Text("New Partner Name") },
                        singleLine = true,
                        isError = partnerNameError.isNotEmpty(),
                        supportingText = if (partnerNameError.isNotEmpty()) {
                            { Text(partnerNameError, color = MaterialTheme.colorScheme.error) }
                        } else null
                    )

                    if (partnerNameError.isEmpty() && editPartnerName.isNotBlank() && editPartnerName.trim() != partnerName) {
                        Text(
                            text = "✓ New partner name is available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val error = validatePartnerName(editPartnerName, partnerToEdit)
                        if (error.isEmpty() && editPartnerName.trim() != partnerName) {
                            viewModel.updatePartnerName(partnerToEdit, editPartnerName.trim())
                            showEditPartnerDialog = false
                            editPartnerName = ""
                            partnerNameError = ""
                            partnerToEdit = 0L
                        } else if (editPartnerName.trim() == partnerName) {
                            showEditPartnerDialog = false
                            editPartnerName = ""
                            partnerNameError = ""
                            partnerToEdit = 0L
                        } else {
                            partnerNameError = error
                        }
                    },
                    enabled = editPartnerName.isNotBlank() && partnerNameError.isEmpty()
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditPartnerDialog = false
                    editPartnerName = ""
                    partnerNameError = ""
                    partnerToEdit = 0L
                }) {
                    Text("Cancel")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }

    // Delete Partner Dialog
    if (showDeleteDialog) {
        val partnerName = partners.find { it.id == partnerToDelete }?.name ?: ""
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                partnerToDelete = 0L
            },
            title = { Text("Delete Partner") },
            text = {
                Text("Are you sure you want to delete \"$partnerName\"?\n\nThis will remove all transaction history with this partner. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePartner(partnerToDelete)
                        showDeleteDialog = false
                        partnerToDelete = 0L
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Partner")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    partnerToDelete = 0L
                }) {
                    Text("Cancel")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        )
    }

    // Date Range Dialog with Export Options
    if (showDateRangeDialog) {
        AlertDialog(
            onDismissRequest = {
                showDateRangeDialog = false
            },
            title = { Text("Date Range Report & Export") },
            text = {
                Column {
                    Text("Partner: ${partners.find { it.id == selectedPartnerId }?.name}")
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(startDate)}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("End: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(endDate)}")
                    }

                    if (startDate.after(endDate)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Warning: Start date is after end date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isSameDay(startDate, endDate)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ Single day report selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose an action:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Export Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export to PDF
                        Button(
                            onClick = {
                                if (!startDate.after(endDate)) {
                                    val partnerName = partners.find { it.id == selectedPartnerId }?.name ?: "Partner"
                                    val fileName = "DateRangeReport_${partnerName}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(startDate)}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(endDate)}.html"
                                    pdfExportLauncher.launch(fileName)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !startDate.after(endDate) && !isExportingReport
                        ) {
                            if (isExportingReport) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Filled.AccountBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }

                        // Export to Excel
                        Button(
                            onClick = {
                                if (!startDate.after(endDate)) {
                                    val partnerName = partners.find { it.id == selectedPartnerId }?.name ?: "Partner"
                                    val fileName = "DateRangeReport_${partnerName}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(startDate)}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(endDate)}.csv"
                                    excelExportLauncher.launch(fileName)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !startDate.after(endDate) && !isExportingReport
                        ) {
                            if (isExportingReport) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel")
                        }
                    }

                    if (isExportingReport) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Generating report...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = null
        )
    }

    // Export Result Dialog
    if (showExportResultDialog) {
        AlertDialog(
            onDismissRequest = { showExportResultDialog = false },
            title = {
                Text(if (exportMessage.contains("successfully")) "Export Successful" else "Export Failed")
            },
            text = { Text(exportMessage) },
            confirmButton = {
                TextButton(onClick = { showExportResultDialog = false }) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    if (exportMessage.contains("successfully")) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (exportMessage.contains("successfully"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        )
    }

    // Backup Export Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup Status") },
            text = { Text(backupMessage) },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    if (backupMessage.contains("successfully")) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (backupMessage.contains("successfully"))
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        )
    }

    // Restore Confirmation Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Backup") },
            text = {
                Column {
                    Text("⚠️ Important Information:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• This will merge backup data with existing data")
                    Text("• Duplicate transactions will be skipped")
                    Text("• Partners with same names will be merged")
                    Text("• Existing data will NOT be deleted")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a backup file to restore from.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                ) {
                    Text("Choose Backup File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }

    // Restore Result Dialog
    if (showRestoreResultDialog && restoreResult != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreResultDialog = false
                restoreResult = null
            },
            title = {
                Text(if (restoreResult!!.success) "Restore Successful" else "Restore Failed")
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    item {
                        Text(
                            text = restoreResult!!.message,
                            fontWeight = FontWeight.Medium
                        )

                        if (restoreResult!!.success) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("📊 Restore Summary:", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))

                            Text("✅ Partners Added: ${restoreResult!!.partnersAdded}")
                            Text("🔄 Partners Updated: ${restoreResult!!.partnersUpdated}")
                            Text("✅ Transactions Added: ${restoreResult!!.transactionsAdded}")
                            Text("⏭️ Transactions Skipped: ${restoreResult!!.transactionsSkipped}")
                            Text("💱 Exchange Rates Added: ${restoreResult!!.exchangeRatesAdded}")
                            Text("💱 Exchange Rates Updated: ${restoreResult!!.exchangeRatesUpdated}")
                        }

                        if (restoreResult!!.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("⚠️ Warnings/Errors:", fontWeight = FontWeight.SemiBold)
                            restoreResult!!.errors.forEach { error ->
                                Text(
                                    text = "• $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreResultDialog = false
                    restoreResult = null
                }) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    if (restoreResult!!.success) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (restoreResult!!.success)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }
        )
    }

    // Main Page View
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("Currency Exchange") }
        )

        // Compact Cumulative Net Positions at the top
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            var cumulativeSummary by remember { mutableStateOf<PartnerSummary?>(null) }
            var isLoadingCumulative by remember { mutableStateOf(false) }

            // Load cumulative summary when partners change
            LaunchedEffect(partners) {
                if (partners.isNotEmpty()) {
                    isLoadingCumulative = true
                    try {
                        cumulativeSummary = viewModel.getCumulativeNetPositions()
                    } catch (e: Exception) {
                        // Handle error silently
                    } finally {
                        isLoadingCumulative = false
                    }
                } else {
                    cumulativeSummary = null
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Positions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (isLoadingCumulative) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Content
                if (cumulativeSummary != null && !isLoadingCumulative) {
                    // Responsive layout for net positions
                    ResponsiveNetPositionsLayout(
                        tzsAmount = cumulativeSummary!!.totalNetTzs,
                        cnyAmount = cumulativeSummary!!.totalNetCny,
                        usdtAmount = cumulativeSummary!!.totalNetUsdt
                    )
                } else if (partners.isEmpty()) {
                    Text(
                        text = "Add partners to see positions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!isLoadingCumulative) {
                    Text(
                        text = "No transactions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Partner Management Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Partners",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { showAddPartnerDialog = true }
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Partner")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        label = { Text("Search partners...") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.updateSearchQuery("") }
                                ) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Results Info
                    if (searchQuery.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Found ${filteredPartners.size} of ${partners.size} partners",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (filteredPartners.size != partners.size) {
                                TextButton(
                                    onClick = { viewModel.updateSearchQuery("") }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Partners List
                    if (filteredPartners.isEmpty()) {
                        if (searchQuery.isNotBlank()) {
                            // No search results
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No partners found for \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Try a different search term",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // No partners at all
                            Text(
                                text = "No partners added yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(filteredPartners) { _, partner ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedPartnerId == partner.id) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    onClick = { viewModel.selectPartner(partner.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = partner.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selectedPartnerId == partner.id) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row {
                                            if (selectedPartnerId == partner.id) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    partnerToEdit = partner.id
                                                    editPartnerName = partner.name
                                                    showEditPartnerDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Edit,
                                                    contentDescription = "Edit",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    partnerToDelete = partner.id
                                                    showDeleteDialog = true
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Actions Section
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Go to Partner Page
                    Button(
                        onClick = {
                            if (selectedPartnerId != 0L) {
                                navController.navigate("partner_page/$selectedPartnerId")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedPartnerId != 0L
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Partner Transactions")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Date Range Report
                    OutlinedButton(
                        onClick = {
                            if (selectedPartnerId != 0L) {
                                showDateRangeDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedPartnerId != 0L
                    ) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get Date Range Report")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // NEW ANALYSIS BUTTON
                    OutlinedButton(
                        onClick = { showAnalysisDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = partners.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analysis")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backup and Restore section
                    Text(
                        text = "Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Export Backup
                    Button(
                        onClick = {
                            val fileName = viewModel.generateBackupFileName()
                            backupExportLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCreatingBackup
                    ) {
                        if (isCreatingBackup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isCreatingBackup) "Creating Backup..." else "Export Backup")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Import Backup
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRestoringBackup
                    ) {
                        if (isRestoringBackup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRestoringBackup) "Restoring..." else "Import Backup")
                    }

                    if (selectedPartnerId == 0L && partners.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please select a partner to enable partner-specific actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (partners.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add partners to enable analysis and reporting features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveNetPositionsLayout(
    tzsAmount: Double,
    cnyAmount: Double,
    usdtAmount: Double,
    modifier: Modifier = Modifier
) {
    // Calculate if we need to use vertical layout based on text length
    val maxTextLength = maxOf(
        formatNumberWithCommas(tzsAmount).length,
        formatNumberWithCommas(cnyAmount).length,
        formatNumberWithCommas(usdtAmount).length
    )

    // Use vertical layout for very large numbers (more than 15 characters including commas and decimals)
    if (maxTextLength > 15) {
        // Vertical layout for large numbers
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnhancedNetItem("TZS", tzsAmount, modifier = Modifier.fillMaxWidth())
            EnhancedNetItem("CNY", cnyAmount, modifier = Modifier.fillMaxWidth())
            EnhancedNetItem("USDT", usdtAmount, modifier = Modifier.fillMaxWidth())
        }
    } else {
        // Horizontal layout for smaller numbers
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EnhancedNetItem("TZS", tzsAmount, modifier = Modifier.weight(1f))
            EnhancedNetItem("CNY", cnyAmount, modifier = Modifier.weight(1f))
            EnhancedNetItem("USDT", usdtAmount, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EnhancedNetItem(
    currency: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    val formattedAmount = formatNumberWithCommas(amount)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = currency,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )

        Text(
            text = formattedAmount,
            style = when {
                formattedAmount.length > 20 -> MaterialTheme.typography.bodySmall
                formattedAmount.length > 15 -> MaterialTheme.typography.bodyMedium
                formattedAmount.length > 10 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Bold,
            color = if (amount >= 0)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.error,
            maxLines = 1,
            softWrap = false
        )
    }
}

// Helper Composables (keeping DatePickerDialog in this file)
@Composable
private fun DatePickerDialog(
    currentDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit,
    title: String
) {
    val calendar = java.util.Calendar.getInstance()
    calendar.time = currentDate

    var selectedYear by remember { mutableStateOf(calendar.get(java.util.Calendar.YEAR)) }
    var selectedMonth by remember { mutableStateOf(calendar.get(java.util.Calendar.MONTH)) }
    var selectedDay by remember { mutableStateOf(calendar.get(java.util.Calendar.DAY_OF_MONTH)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick date options
                Text(
                    text = "Quick Options:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDateSelected(Date())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Today")
                    }

                    OutlinedButton(
                        onClick = {
                            onDateSelected(Date(System.currentTimeMillis() - 86400000)) // Yesterday
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Yesterday")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Manual date selection
                Text(
                    text = "Or select manually:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Year selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Year:", modifier = Modifier.width(50.dp))
                    OutlinedButton(
                        onClick = { if (selectedYear > 2020) selectedYear-- }
                    ) { Text("-") }
                    Text(
                        text = selectedYear.toString(),
                        modifier = Modifier.width(60.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = { if (selectedYear < 2030) selectedYear++ }
                    ) { Text("+") }
                }

                // Month selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Month:", modifier = Modifier.width(50.dp))
                    OutlinedButton(
                        onClick = { if (selectedMonth > 0) selectedMonth-- }
                    ) { Text("-") }
                    Text(
                        text = getMonthName(selectedMonth),
                        modifier = Modifier.width(60.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = { if (selectedMonth < 11) selectedMonth++ }
                    ) { Text("+") }
                }

                // Day selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Day:", modifier = Modifier.width(50.dp))
                    OutlinedButton(
                        onClick = { if (selectedDay > 1) selectedDay-- }
                    ) { Text("-") }
                    Text(
                        text = selectedDay.toString(),
                        modifier = Modifier.width(60.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    OutlinedButton(
                        onClick = {
                            val maxDay = getMaxDayOfMonth(selectedYear, selectedMonth)
                            if (selectedDay < maxDay) selectedDay++
                        }
                    ) { Text("+") }
                }

                // Preview
                Text(
                    text = "Selected: ${selectedDay}/${selectedMonth + 1}/${selectedYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newCalendar = java.util.Calendar.getInstance()
                    newCalendar.set(selectedYear, selectedMonth, selectedDay)
                    onDateSelected(newCalendar.time)
                    onDismiss()
                }
            ) {
                Text("Set Date")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions (keeping these in this file)
private fun getMonthName(month: Int): String {
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return months.getOrNull(month) ?: "Jan"
}

private fun getMaxDayOfMonth(year: Int, month: Int): Int {
    val calendar = java.util.Calendar.getInstance()
    calendar.set(year, month, 1)
    return calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
}

private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
    val cal2 = java.util.Calendar.getInstance().apply { time = date2 }

    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatNumberWithCommas(number: Double): String {
    val formatted = String.format("%.2f", number)
    val parts = formatted.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else "00"

    // Handle negative sign
    val isNegative = integerPart.startsWith("-")
    val absoluteIntegerPart = if (isNegative) integerPart.substring(1) else integerPart

    // Add commas to integer part
    val formattedInteger = absoluteIntegerPart.reversed().chunked(3).joinToString(",").reversed()

    val sign = if (isNegative) "-" else ""
    return "$sign$formattedInteger.$decimalPart"
}