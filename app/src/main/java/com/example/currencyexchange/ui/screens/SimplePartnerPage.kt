package com.example.currencyexchange.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.currencyexchange.viewmodel.PartnerViewModel
import com.example.currencyexchange.data.entities.TransactionEntity
import com.example.currencyexchange.data.dao.PartnerSummary
import com.example.currencyexchange.ui.components.NetPositionItem
import java.text.SimpleDateFormat
import java.util.*

// Data class for table rows (without notes)
data class TransactionRow(
    val id: String = UUID.randomUUID().toString(),
    var tzs: String = "",
    var foreignAmount: String = "",
    var currency: String = "CNY",
    var rate: String = ""
)

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

    // Table state (without notes)
    var transactionRows by remember { mutableStateOf(listOf(TransactionRow())) }
    var selectedDate by remember { mutableStateOf(Date()) }
    var showTransactionsList by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    // Edit transaction states (without notes)
    var editTzsReceived by remember { mutableStateOf("") }
    var editForeignAmount by remember { mutableStateOf("") }
    var editForeignCurrency by remember { mutableStateOf("CNY") }
    var editExchangeRate by remember { mutableStateOf("") }

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

    val partnerName = partner?.name ?: "Loading..."

    // Helper function to add a new row
    fun addNewRow() {
        transactionRows = transactionRows + TransactionRow()
    }

    // Helper function to remove a row
    fun removeRow(rowId: String) {
        if (transactionRows.size > 1) {
            transactionRows = transactionRows.filter { it.id != rowId }
        }
    }

    // Helper function to update row
    fun updateRow(rowId: String, updatedRow: TransactionRow) {
        transactionRows = transactionRows.map { if (it.id == rowId) updatedRow else it }
    }

    // Helper function to format number with commas
    fun formatWithCommas(number: String): String {
        if (number.isBlank()) return ""

        // Remove existing commas and non-numeric characters except dots
        val cleanNumber = number.replace(",", "").filter { it.isDigit() || it == '.' }

        if (cleanNumber.isBlank()) return ""

        // Split by decimal point if exists
        val parts = cleanNumber.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1] else ""

        // Add commas to integer part
        val formattedInteger = integerPart.reversed().chunked(3).joinToString(",").reversed()

        return if (decimalPart.isNotEmpty()) "$formattedInteger.$decimalPart" else formattedInteger
    }

    // Helper function to remove commas for parsing
    fun removeCommas(number: String): String {
        return number.replace(",", "")
    }

    // Helper function to validate and save transactions
    fun saveTransactions() {
        val validRows = transactionRows.filter { row ->
            // Valid if at least one field has a value
            val hasTzs = row.tzs.isNotBlank()
            val hasForeignAmount = row.foreignAmount.isNotBlank()
            val hasRate = row.rate.isNotBlank()

            hasTzs || hasForeignAmount || hasRate
        }

        if (validRows.isEmpty()) {
            // Show error - no valid rows
            return
        }

        validRows.forEach { row ->
            val tzs = removeCommas(row.tzs).toDoubleOrNull() ?: 0.0
            val foreignAmount = removeCommas(row.foreignAmount).toDoubleOrNull() ?: 0.0
            val rate = row.rate.toDoubleOrNull() ?: when (row.currency) {
                "CNY" -> defaultCnyRate.toDoubleOrNull() ?: 376.0
                "USDT" -> defaultUsdtRate.toDoubleOrNull() ?: 2380.0
                else -> 376.0
            }

            viewModel.saveTransaction(
                date = selectedDate,
                tzsReceived = tzs,
                foreignGiven = foreignAmount,
                foreignCurrency = row.currency,
                exchangeRate = rate,
                notes = "" // No notes
            )
        }

        // Clear the table after saving
        transactionRows = listOf(TransactionRow())
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

    // Edit Transaction Dialog (without notes)
    if (showEditDialog && editingTransaction != null) {
        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                editingTransaction = null
            },
            title = { Text("Edit Transaction", fontSize = 16.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editTzsReceived,
                        onValueChange = { editTzsReceived = it },
                        label = { Text("TZS Received", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = editForeignAmount,
                            onValueChange = { editForeignAmount = it },
                            label = { Text("${editForeignCurrency} Amount", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                        )

                        OutlinedTextField(
                            value = editExchangeRate,
                            onValueChange = { editExchangeRate = it },
                            label = { Text("Rate", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                        )
                    }

                    // Currency selection
                    Row(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(currency, fontSize = 14.sp)
                            }
                        }
                    }

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
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = "Calculated Net Values:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Net TZS: ${formatNumber(calcTzs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Net ${editForeignCurrency}: ${formatNumber(calcForeign)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcForeign >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
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
                                    notes = "", // No notes
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
                    Text("Save", fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingTransaction = null
                }) {
                    Text("Cancel", fontSize = 14.sp)
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
            title = { Text("Delete Transaction", fontSize = 16.sp) },
            text = {
                Column {
                    Text("Are you sure you want to delete this transaction?", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    deletingTransaction?.let { transaction ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = "Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(transaction.date)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "TZS: ${formatNumber(transaction.tzsReceived)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${transaction.foreignCurrency}: ${formatNumber(transaction.foreignGiven)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
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
                    Text("Delete", fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    deletingTransaction = null
                }) {
                    Text("Cancel", fontSize = 14.sp)
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
            title = { Text("Error", fontSize = 16.sp) },
            text = { Text(errorMessage, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("OK", fontSize = 14.sp)
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
            title = { Text("Success", fontSize = 16.sp) },
            text = { Text(successMessage, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearSuccessMessage() }) {
                    Text("OK", fontSize = 14.sp)
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
                title = { Text("$partnerName - Transactions", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { showTransactionsList = false }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall Net Positions Summary
                partnerSummary?.let { summary ->
                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Overall Net Positions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                NetPositionItem("TZS", summary.totalNetTzs)
                                NetPositionItem("CNY", summary.totalNetCny)
                                NetPositionItem("USDT", summary.totalNetUsdt)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Total Transactions: ${summary.transactionCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Transactions by Date
                if (transactions.isEmpty()) {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No transactions yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Go back and add some transactions",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    // Group transactions by date
                    val groupedTransactions = transactions.groupBy {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.date)
                    }.toSortedMap(compareByDescending {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).parse(it)
                    })

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedTransactions.forEach { (dateString, dayTransactions) ->
                            item {
                                TransactionDateGroup(
                                    dateString = dateString,
                                    transactions = dayTransactions,
                                    onUpdateTransaction = { transaction ->
                                        viewModel.updateTransaction(transaction)
                                    },
                                    onDeleteTransaction = { transaction ->
                                        deletingTransaction = transaction
                                        showDeleteDialog = true
                                    },
                                    formatWithCommas = ::formatWithCommas,
                                    removeCommas = ::removeCommas
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Main transaction entry view with optimized table
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            TopAppBar(
                title = { Text(partnerName, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Picker and Default Rates
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Transaction Date & Default Rates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Date Picker
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate), fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Default Exchange Rates
                        Text(
                            text = "Default Exchange Rates",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // CNY Rate
                            OutlinedTextField(
                                value = defaultCnyRate,
                                onValueChange = { viewModel.updateDefaultCnyRate(it) },
                                label = { Text("CNY Rate", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                            )

                            // USDT Rate
                            OutlinedTextField(
                                value = defaultUsdtRate,
                                onValueChange = { viewModel.updateDefaultUsdtRate(it) },
                                label = { Text("USDT Rate", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                            )
                        }

                        // Rate explanations
                        Column(
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            Text(
                                text = "1 CNY = $defaultCnyRate TZS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "1 USDT = $defaultUsdtRate TZS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Optimized Transaction Table Section
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add New Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            OutlinedButton(
                                onClick = { addNewRow() }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add Row", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Optimized Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "TZS",
                                modifier = Modifier.weight(3.5f), // Larger for 9 digits
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "CNY/USDT",
                                modifier = Modifier.weight(2.5f), // Medium for 6 digits
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "RATE",
                                modifier = Modifier.weight(1.5f), // Smaller for 3 digits
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(32.dp)) // Space for delete button
                        }

                        // Optimized Table Rows
                        transactionRows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // TZS Column (Optimized for 9 digits)
                                OutlinedTextField(
                                    value = row.tzs,
                                    onValueChange = { newValue ->
                                        val formattedValue = formatWithCommas(newValue)
                                        updateRow(row.id, row.copy(tzs = formattedValue))
                                    },
                                    placeholder = { Text("0", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(3.5f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                )

                                // CNY/USDT Column (Optimized for 6 digits)
                                Column(
                                    modifier = Modifier.weight(2.5f)
                                ) {
                                    // Currency selector
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = row.currency,
                                            onValueChange = { },
                                            readOnly = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(
                                                    expanded = expanded
                                                )
                                            },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            listOf("CNY", "USDT").forEach { currency ->
                                                DropdownMenuItem(
                                                    text = { Text(currency, fontSize = 12.sp) },
                                                    onClick = {
                                                        updateRow(row.id, row.copy(currency = currency))
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Amount field with comma formatting
                                    OutlinedTextField(
                                        value = row.foreignAmount,
                                        onValueChange = { newValue ->
                                            val formattedValue = formatWithCommas(newValue)
                                            updateRow(row.id, row.copy(foreignAmount = formattedValue))
                                        },
                                        placeholder = { Text("0", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                    )
                                }

                                // RATE Column (Optimized for 3 digits)
                                OutlinedTextField(
                                    value = row.rate,
                                    onValueChange = {
                                        updateRow(row.id, row.copy(rate = it))
                                    },
                                    placeholder = {
                                        Text(if (row.currency == "CNY") defaultCnyRate else defaultUsdtRate, fontSize = 12.sp)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                )

                                // Delete button
                                IconButton(
                                    onClick = { removeRow(row.id) },
                                    enabled = transactionRows.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete row",
                                        tint = if (transactionRows.size > 1)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { saveTransactions() },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && transactionRows.any { row ->
                            row.tzs.isNotBlank() || row.foreignAmount.isNotBlank() || row.rate.isNotBlank()
                        }
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save All", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showTransactionsList = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View All (${transactions.size})", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDateGroup(
    dateString: String,
    transactions: List<TransactionEntity>,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    formatWithCommas: (String) -> String,
    removeCommas: (String) -> String
) {
    var editingTransactionId by remember { mutableStateOf<Long?>(null) }
    var editingValues by remember { mutableStateOf(mapOf<Long, EditableTransactionData>()) }

    // Calculate net positions for this date
    val dateTzs = transactions.sumOf { it.netTzs }
    val dateCny = transactions.filter { it.foreignCurrency == "CNY" }.sumOf { it.netForeign }
    val dateUsdt = transactions.filter { it.foreignCurrency == "USDT" }.sumOf { it.netForeign }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Date Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
                Text(
                    text = "${transactions.size} transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Optimized Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "TZS",
                    modifier = Modifier.weight(2.5f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = "CNY/USDT",
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = "RATE",
                    modifier = Modifier.weight(1.5f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Text(
                    text = "NET TZS",
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(64.dp)) // Space for action buttons
            }

            // Transaction Rows
            transactions.forEach { transaction ->
                val isEditing = editingTransactionId == transaction.id
                val editData = editingValues[transaction.id]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing && editData != null) {
                        // Editable TZS field
                        OutlinedTextField(
                            value = editData.tzs,
                            onValueChange = { newValue ->
                                val formattedValue = formatWithCommas(newValue)
                                editingValues = editingValues + (transaction.id to editData.copy(tzs = formattedValue))
                            },
                            singleLine = true,
                            modifier = Modifier.weight(2.5f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )

                        // Editable Currency/Amount
                        Column(
                            modifier = Modifier.weight(2f)
                        ) {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = editData.currency,
                                    onValueChange = { },
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf("CNY", "USDT").forEach { currency ->
                                        DropdownMenuItem(
                                            text = { Text(currency, fontSize = 12.sp) },
                                            onClick = {
                                                editingValues = editingValues + (transaction.id to editData.copy(currency = currency))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = editData.foreignAmount,
                                onValueChange = { newValue ->
                                    val formattedValue = formatWithCommas(newValue)
                                    editingValues = editingValues + (transaction.id to editData.copy(foreignAmount = formattedValue))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        }

                        // Editable Rate
                        OutlinedTextField(
                            value = editData.rate,
                            onValueChange = { newValue ->
                                editingValues = editingValues + (transaction.id to editData.copy(rate = newValue))
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )

                        // Net TZS (calculated)
                        val calculatedNetTzs = (removeCommas(editData.tzs).toDoubleOrNull() ?: 0.0) -
                                ((removeCommas(editData.foreignAmount).toDoubleOrNull() ?: 0.0) * (editData.rate.toDoubleOrNull() ?: 0.0))

                        Text(
                            text = formatNumber(calculatedNetTzs),
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (calculatedNetTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 10.sp
                        )

                        // Action Buttons (Save/Cancel)
                        Row {
                            IconButton(
                                onClick = {
                                    // Save changes
                                    val updatedTransaction = transaction.copy(
                                        tzsReceived = removeCommas(editData.tzs).toDoubleOrNull() ?: 0.0,
                                        foreignGiven = removeCommas(editData.foreignAmount).toDoubleOrNull() ?: 0.0,
                                        foreignCurrency = editData.currency,
                                        exchangeRate = editData.rate.toDoubleOrNull() ?: 0.0,
                                        notes = "", // No notes
                                        lastModified = Date()
                                    )
                                    onUpdateTransaction(updatedTransaction)
                                    editingTransactionId = null
                                    editingValues = editingValues - transaction.id
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    // Cancel editing
                                    editingTransactionId = null
                                    editingValues = editingValues - transaction.id
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else {
                        // Display mode
                        Text(
                            text = formatWithCommas(transaction.tzsReceived.toLong().toString()),
                            modifier = Modifier.weight(2.5f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )

                        Column(
                            modifier = Modifier.weight(2f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = transaction.foreignCurrency,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            )
                            Text(
                                text = formatWithCommas(transaction.foreignGiven.toLong().toString()),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp
                            )
                        }

                        Text(
                            text = formatNumber(transaction.exchangeRate),
                            modifier = Modifier.weight(1.5f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp
                        )

                        Text(
                            text = formatNumber(transaction.netTzs),
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (transaction.netTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 10.sp
                        )

                        // Action Buttons (Edit/Delete)
                        Row {
                            IconButton(
                                onClick = {
                                    // Start editing
                                    editingTransactionId = transaction.id
                                    editingValues = editingValues + (transaction.id to EditableTransactionData(
                                        tzs = formatWithCommas(transaction.tzsReceived.toLong().toString()),
                                        foreignAmount = formatWithCommas(transaction.foreignGiven.toLong().toString()),
                                        currency = transaction.foreignCurrency,
                                        rate = transaction.exchangeRate.toString(),
                                        notes = "" // No notes
                                    ))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeleteTransaction(transaction) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Date Net Positions
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Net Positions for $dateString",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        NetPositionItem("TZS", dateTzs)
                        NetPositionItem("CNY", dateCny)
                        NetPositionItem("USDT", dateUsdt)
                    }
                }
            }
        }
    }
}

// Data class for editable transaction data (without notes)
data class EditableTransactionData(
    val tzs: String,
    val foreignAmount: String,
    val currency: String,
    val rate: String,
    val notes: String = "" // Keep for compatibility but not used
)

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
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick date options
                Text(
                    text = "Quick Options:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDateSelected(Date())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Today", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onDateSelected(Date(System.currentTimeMillis() - 86400000))
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Yesterday", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Manual date selection
                Text(
                    text = "Or select manually:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                // Year selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Year:", modifier = Modifier.width(40.dp), fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { if (selectedYear > 2020) selectedYear-- }
                    ) { Text("-", fontSize = 12.sp) }
                    Text(
                        text = selectedYear.toString(),
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = { if (selectedYear < 2030) selectedYear++ }
                    ) { Text("+", fontSize = 12.sp) }
                }

                // Month selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Month:", modifier = Modifier.width(40.dp), fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { if (selectedMonth > 0) selectedMonth-- }
                    ) { Text("-", fontSize = 12.sp) }
                    Text(
                        text = getMonthName(selectedMonth),
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = { if (selectedMonth < 11) selectedMonth++ }
                    ) { Text("+", fontSize = 12.sp) }
                }

                // Day selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Day:", modifier = Modifier.width(40.dp), fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { if (selectedDay > 1) selectedDay-- }
                    ) { Text("-", fontSize = 12.sp) }
                    Text(
                        text = selectedDay.toString(),
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = {
                            val maxDay = getMaxDayOfMonth(selectedYear, selectedMonth)
                            if (selectedDay < maxDay) selectedDay++
                        }
                    ) { Text("+", fontSize = 12.sp) }
                }

                // Preview
                Text(
                    text = "Selected: ${selectedDay}/${selectedMonth + 1}/${selectedYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
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
                Text("Set Date", fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 14.sp)
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