package com.example.currencyexchange.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.currencyexchange.viewmodel.PartnerViewModel
import com.example.currencyexchange.data.entities.TransactionEntity
import com.example.currencyexchange.data.dao.PartnerSummary
import com.example.currencyexchange.ui.components.NetPositionItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePartnerPage(
    partnerId: Long,
    navController: NavController,
    viewModel: PartnerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // Collect data from ViewModel
    val partner by viewModel.partner.collectAsState()
    val transactions by viewModel.transactions.collectAsState(initial = emptyList())
    val defaultCnyRate by viewModel.defaultCnyRate.collectAsState()
    val defaultUsdtRate by viewModel.defaultUsdtRate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    // UI state for transaction creation
    var selectedDate by remember { mutableStateOf(Date()) }
    var tzsReceived by remember { mutableStateOf("") }
    var foreignGiven by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("CNY") }
    var exchangeRate by remember { mutableStateOf(defaultCnyRate) }
    var transactionNotes by remember { mutableStateOf("") }

    var showTransactionsList by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    // Edit transaction states
    var editTzsReceived by remember { mutableStateOf("") }
    var editForeignAmount by remember { mutableStateOf("") }
    var editForeignCurrency by remember { mutableStateOf("CNY") }
    var editExchangeRate by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }

    // Summary data
    var partnerSummary by remember { mutableStateOf<PartnerSummary?>(null) }

    // Load summary when transactions change
    LaunchedEffect(transactions) {
        partnerSummary = try {
            viewModel.getPartnerSummary()
        } catch (e: Exception) {
            null
        }
    }

    // Update exchange rate when currency changes
    LaunchedEffect(selectedCurrency, defaultCnyRate, defaultUsdtRate) {
        exchangeRate = when (selectedCurrency) {
            "CNY" -> defaultCnyRate
            "USDT" -> defaultUsdtRate
            else -> defaultCnyRate
        }
    }

    val partnerName = partner?.name ?: "Loading..."

    // Helper function to validate inputs
    fun validateInputs(): Boolean {
        if (tzsReceived.isBlank()) {
            return false
        }
        if (foreignGiven.isBlank()) {
            return false
        }
        if (exchangeRate.isBlank()) {
            return false
        }
        if (tzsReceived.toDoubleOrNull() == null || tzsReceived.toDouble() <= 0) {
            return false
        }
        if (foreignGiven.toDoubleOrNull() == null || foreignGiven.toDouble() <= 0) {
            return false
        }
        if (exchangeRate.toDoubleOrNull() == null || exchangeRate.toDouble() <= 0) {
            return false
        }
        return true
    }

    // Helper function to clear inputs
    fun clearInputs() {
        tzsReceived = ""
        foreignGiven = ""
        transactionNotes = ""
        exchangeRate = when (selectedCurrency) {
            "CNY" -> defaultCnyRate
            "USDT" -> defaultUsdtRate
            else -> defaultCnyRate
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            currentDate = selectedDate,
            onDateSelected = { selectedDate = it },
            onDismiss = { showDatePicker = false },
            title = "Select Transaction Date"
        )
    }

    // Edit Transaction Dialog
    if (showEditDialog && editingTransaction != null) {
        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                editingTransaction = null
            },
            title = { Text("Edit Transaction") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editTzsReceived,
                        onValueChange = { editTzsReceived = it },
                        label = { Text("TZS Received") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editForeignAmount,
                            onValueChange = { editForeignAmount = it },
                            label = { Text("${editForeignCurrency} Amount") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )

                        OutlinedTextField(
                            value = editExchangeRate,
                            onValueChange = { editExchangeRate = it },
                            label = { Text("Rate") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Currency selection
                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf("CNY", "USDT").forEach { currency ->
                            Row(
                                modifier = Modifier.selectable(
                                    selected = editForeignCurrency == currency,
                                    onClick = { editForeignCurrency = currency },
                                    role = Role.RadioButton
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editForeignCurrency == currency,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(currency)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    // Show calculated net values
                    val calcTzs = (editTzsReceived.toDoubleOrNull() ?: 0.0) -
                            ((editForeignAmount.toDoubleOrNull() ?: 0.0) * (editExchangeRate.toDoubleOrNull() ?: 0.0))
                    val calcForeign = if ((editExchangeRate.toDoubleOrNull() ?: 0.0) > 0) {
                        calcTzs / (editExchangeRate.toDoubleOrNull() ?: 1.0)
                    } else 0.0

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Calculated Net Values:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Net TZS: ${formatNumber(calcTzs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Net ${editForeignCurrency}: ${formatNumber(calcForeign)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcForeign >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTzsReceived = editTzsReceived.toDoubleOrNull()
                        val newForeignAmount = editForeignAmount.toDoubleOrNull()
                        val newExchangeRate = editExchangeRate.toDoubleOrNull()

                        if (newTzsReceived != null && newForeignAmount != null && newExchangeRate != null && newExchangeRate > 0) {
                            editingTransaction?.let { oldTransaction ->
                                val updatedTransaction = oldTransaction.copy(
                                    tzsReceived = newTzsReceived,
                                    foreignGiven = newForeignAmount,
                                    foreignCurrency = editForeignCurrency,
                                    exchangeRate = newExchangeRate,
                                    notes = editNotes,
                                    lastModified = Date()
                                )
                                viewModel.updateTransaction(updatedTransaction)
                                showEditDialog = false
                                editingTransaction = null
                            }
                        }
                    },
                    enabled = editTzsReceived.isNotBlank() && editForeignAmount.isNotBlank() &&
                            editExchangeRate.isNotBlank() && (editExchangeRate.toDoubleOrNull() ?: 0.0) > 0
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingTransaction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Transaction Dialog
    if (showDeleteDialog && deletingTransaction != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deletingTransaction = null
            },
            title = { Text("Delete Transaction") },
            text = {
                Column {
                    Text("Are you sure you want to delete this transaction?")
                    Spacer(modifier = Modifier.height(8.dp))

                    deletingTransaction?.let { transaction ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(transaction.date)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "TZS: ${formatNumber(transaction.tzsReceived)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${transaction.foreignCurrency}: ${formatNumber(transaction.foreignGiven)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingTransaction?.let { transaction ->
                            viewModel.deleteTransaction(transaction)
                            showDeleteDialog = false
                            deletingTransaction = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deletingTransaction = null
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

    // Error Dialog
    if (errorMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearErrorMessage() },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK")
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

    // Success Dialog
    if (successMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.clearSuccessMessage() },
            title = { Text("Success") },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSuccessMessage() }) {
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

    if (showTransactionsList) {
        // Show all transactions view
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Header
            TopAppBar(
                title = { Text("$partnerName - All Transactions") },
                navigationIcon = {
                    IconButton(onClick = { showTransactionsList = false }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Net Positions Summary
                partnerSummary?.let { summary ->
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
                                NetPositionItem("TZS", summary.totalNetTzs)
                                NetPositionItem("CNY", summary.totalNetCny)
                                NetPositionItem("USDT", summary.totalNetUsdt)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Total Transactions: ${summary.transactionCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Transactions List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "${transactions.size} transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (transactions.isEmpty()) {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Go back and add some transactions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(transactions) { _, transaction ->
                            TransactionCard(
                                transaction = transaction,
                                onEdit = {
                                    editingTransaction = transaction
                                    editTzsReceived = transaction.tzsReceived.toString()
                                    editForeignAmount = transaction.foreignGiven.toString()
                                    editForeignCurrency = transaction.foreignCurrency
                                    editExchangeRate = transaction.exchangeRate.toString()
                                    editNotes = transaction.notes
                                    showEditDialog = true
                                },
                                onDelete = {
                                    deletingTransaction = transaction
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Main transaction entry view
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            TopAppBar(
                title = { Text(partnerName) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date Picker and Default Rates
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Transaction Date & Default Rates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Date Picker
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Default Exchange Rates
                        Text(
                            text = "Default Exchange Rates",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // CNY Rate
                            OutlinedTextField(
                                value = defaultCnyRate,
                                onValueChange = { viewModel.updateDefaultCnyRate(it) },
                                label = { Text("CNY Rate") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            // USDT Rate
                            OutlinedTextField(
                                value = defaultUsdtRate,
                                onValueChange = { viewModel.updateDefaultUsdtRate(it) },
                                label = { Text("USDT Rate") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Rate explanations
                        Column(
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "1 CNY = $defaultCnyRate TZS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "1 USDT = $defaultUsdtRate TZS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Transaction Entry Section
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Add New Transaction",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // TZS Received
                        OutlinedTextField(
                            value = tzsReceived,
                            onValueChange = { tzsReceived = it },
                            label = { Text("TZS Received") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Foreign Currency and Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = foreignGiven,
                                onValueChange = { foreignGiven = it },
                                label = { Text("$selectedCurrency Given") },
                                modifier = Modifier.weight(2f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = exchangeRate,
                                onValueChange = { exchangeRate = it },
                                label = { Text("Rate") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Currency Selection
                        Text(
                            text = "Foreign Currency:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("CNY", "USDT").forEach { currency ->
                                Row(
                                    modifier = Modifier.selectable(
                                        selected = selectedCurrency == currency,
                                        onClick = { selectedCurrency = currency },
                                        role = Role.RadioButton
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedCurrency == currency,
                                        onClick = null
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(currency)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Notes
                        OutlinedTextField(
                            value = transactionNotes,
                            onValueChange = { transactionNotes = it },
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )

                        // Show calculated net values
                        if (tzsReceived.isNotBlank() && foreignGiven.isNotBlank() && exchangeRate.isNotBlank()) {
                            val tzs = tzsReceived.toDoubleOrNull() ?: 0.0
                            val foreign = foreignGiven.toDoubleOrNull() ?: 0.0
                            val rate = exchangeRate.toDoubleOrNull() ?: 0.0

                            if (tzs > 0 && foreign > 0 && rate > 0) {
                                val netTzs = tzs - (foreign * rate)
                                val netForeign = netTzs / rate

                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Calculated Net Values:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Net TZS: ${formatNumber(netTzs)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (netTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = "Net $selectedCurrency: ${formatNumber(netForeign)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (netForeign >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (validateInputs()) {
                                viewModel.saveTransaction(
                                    date = selectedDate,
                                    tzsReceived = tzsReceived.toDouble(),
                                    foreignGiven = foreignGiven.toDouble(),
                                    foreignCurrency = selectedCurrency,
                                    exchangeRate = exchangeRate.toDouble(),
                                    notes = transactionNotes
                                )
                                clearInputs()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = validateInputs() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Transaction")
                    }

                    OutlinedButton(
                        onClick = { showTransactionsList = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.List, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View All (${transactions.size})")
                    }
                }

                // Validation hints
                if (!validateInputs() && (tzsReceived.isNotBlank() || foreignGiven.isNotBlank() || exchangeRate.isNotBlank())) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "⚠️ Please check your inputs:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (tzsReceived.isBlank() || tzsReceived.toDoubleOrNull() == null || tzsReceived.toDouble() <= 0) {
                                Text(
                                    text = "• TZS Received must be a positive number",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (foreignGiven.isBlank() || foreignGiven.toDoubleOrNull() == null || foreignGiven.toDouble() <= 0) {
                                Text(
                                    text = "• Foreign amount must be a positive number",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (exchangeRate.isBlank() || exchangeRate.toDoubleOrNull() == null || exchangeRate.toDouble() <= 0) {
                                Text(
                                    text = "• Exchange rate must be a positive number",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: TransactionEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with date and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(transaction.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row {
                    // Edit Button
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete Button
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TZS Received: ${formatNumber(transaction.tzsReceived)}")
                Text("${transaction.foreignCurrency} Given: ${formatNumber(transaction.foreignGiven)}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Rate: 1 ${transaction.foreignCurrency} = ${formatNumber(transaction.exchangeRate)} TZS")
                Text("")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Net TZS: ${formatNumber(transaction.netTzs)}",
                    color = if (transaction.netTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Net ${transaction.foreignCurrency}: ${formatNumber(transaction.netForeign)}",
                    color = if (transaction.netForeign >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            if (transaction.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes: ${transaction.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
                            onDateSelected(Date(System.currentTimeMillis() - 86400000))
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

// Helper functions
private fun formatNumber(number: Double): String {
    return String.format("%.2f", number)
}

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