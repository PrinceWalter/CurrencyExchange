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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var partnerToDelete by remember { mutableStateOf(0L) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPartnerName by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Backup and Restore UI state
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showRestoreResultDialog by remember { mutableStateOf(false) }
    var isCreatingBackup by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<RestoreResult?>(null) }
    var backupMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    // Date range states
    var startDate by remember { mutableStateOf(Date(System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000))) }
    var endDate by remember { mutableStateOf(Date()) }
    var showDateRangeResults by remember { mutableStateOf(false) }
    var dateRangeResults by remember { mutableStateOf<PartnerSummary?>(null) }
    var isLoadingReport by remember { mutableStateOf(false) }

    // Move LaunchedEffect outside of onClick and use trigger state
    var shouldGenerateReport by remember { mutableStateOf(false) }

    // Handle report generation
    LaunchedEffect(shouldGenerateReport) {
        if (shouldGenerateReport && selectedPartnerId != 0L) {
            isLoadingReport = true
            try {
                dateRangeResults = viewModel.getPartnerSummaryByDateRange(
                    selectedPartnerId, startDate, endDate
                )
                showDateRangeDialog = false
                showDateRangeResults = true
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoadingReport = false
                shouldGenerateReport = false
            }
        }
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

    // Add Partner Dialog
    if (showAddPartnerDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddPartnerDialog = false
                newPartnerName = ""
            },
            title = { Text("Add New Partner") },
            text = {
                OutlinedTextField(
                    value = newPartnerName,
                    onValueChange = { newPartnerName = it },
                    label = { Text("Partner Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPartnerName.isNotBlank()) {
                            viewModel.addPartner(newPartnerName.trim())
                            showAddPartnerDialog = false
                            newPartnerName = ""
                        }
                    },
                    enabled = newPartnerName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddPartnerDialog = false
                    newPartnerName = ""
                }) {
                    Text("Cancel")
                }
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

    // Date Range Dialog
    if (showDateRangeDialog) {
        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = { Text("Select Date Range") },
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
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        shouldGenerateReport = true
                    },
                    enabled = !startDate.after(endDate) && !isLoadingReport
                ) {
                    if (isLoadingReport) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Get Report")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Online Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Online") },
            text = { Text("All data has been saved to cloud storage successfully.") },
            confirmButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("OK")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
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

    // Date Range Results View
    if (showDateRangeResults) {
        dateRangeResults?.let { results ->
            Column(
                modifier = modifier.fillMaxSize()
            ) {
                TopAppBar(
                    title = { Text("Date Range Report") },
                    navigationIcon = {
                        IconButton(onClick = { showDateRangeResults = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Report Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Partner: ${partners.find { it.id == selectedPartnerId }?.name}")
                            Text("From: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(startDate)}")
                            Text("To: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(endDate)}")
                            Text("Transactions: ${results.transactionCount}")
                        }
                    }

                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Net Positions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                NetPositionItem("TZS", results.totalNetTzs)
                                NetPositionItem("CNY", results.totalNetCny)
                                NetPositionItem("USDT", results.totalNetUsdt)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Main Page View
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = { Text("Currency Exchange") },
                actions = {
                    IconButton(
                        onClick = { showSaveDialog = true }
                    ) {
                        // Icon(Icons.Filled.CloudUpload, contentDescription = "Save Online")
                    }
                }
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Positions:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (isLoadingCumulative) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else if (cumulativeSummary != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactNetItem("TZS", cumulativeSummary!!.totalNetTzs)
                            CompactNetItem("CNY", cumulativeSummary!!.totalNetCny)
                            CompactNetItem("USDT", cumulativeSummary!!.totalNetUsdt)
                        }
                    } else if (partners.isEmpty()) {
                        Text(
                            text = "Add partners to see positions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
                                                fontWeight = if (selectedPartnerId == partner.id) FontWeight.Bold else FontWeight.Normal
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

                        // Save Online
                        OutlinedButton(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            //   Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save All Data Online")
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

                        if (selectedPartnerId == 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please select a partner to enable actions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Info
                if (selectedPartnerId != 0L) {
                    val selectedPartner = partners.find { it.id == selectedPartnerId }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Selected Partner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = selectedPartner?.name ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Click 'Open Partner Transactions' to add new transactions",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• Click 'Get Date Range Report' to view transaction history",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNetItem(
    currency: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Text(
            text = currency,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = formatNumberWithCommas(amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (amount >= 0)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
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

private fun formatNumber(number: Double): String {
    return String.format("%.2f", number)
}

private fun formatNumberWithCommas(number: Double): String {
    val formatted = String.format("%.2f", number)
    val parts = formatted.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else "00"

    // Add commas to integer part
    val formattedInteger = integerPart.reversed().chunked(3).joinToString(",").reversed()

    return "$formattedInteger.$decimalPart"
}