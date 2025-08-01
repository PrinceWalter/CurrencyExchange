package com.example.currencyexchange.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

// Data class for cumulative positions
data class CumulativePositions(
    val cumulativeTzs: Double,
    val cumulativeCny: Double,
    val cumulativeUsdt: Double
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

    // Function to determine default currency based on partner name
    fun getDefaultCurrencyForPartner(partnerName: String?): String {
        if (partnerName == null) return "CNY"

        val lowerCaseName = partnerName.lowercase()
        return when {
            lowerCaseName.contains("rmb") -> "CNY"
            lowerCaseName.contains("usdt") -> "USDT"
            else -> "CNY" // Default fallback
        }
    }

    // Get default currency for this partner
    val defaultCurrency = getDefaultCurrencyForPartner(partner?.name)

    // Table state (without notes) - Initialize with partner-specific currency
    var transactionRows by remember { mutableStateOf(listOf(TransactionRow(currency = defaultCurrency))) }
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

    // Update transaction rows when partner changes
    LaunchedEffect(partner?.name) {
        if (transactionRows.size == 1 && transactionRows[0].tzs.isEmpty() && transactionRows[0].foreignAmount.isEmpty()) {
            // Only update if we have a single empty row
            transactionRows = listOf(TransactionRow(currency = defaultCurrency))
        }
    }

    // Load summary when transactions change
    LaunchedEffect(transactions) {
        partnerSummary = try {
            viewModel.getPartnerSummary()
        } catch (e: Exception) {
            null
        }
    }

    val partnerName = partner?.name ?: "Loading..."

    // Helper function to validate numeric input that allows negative numbers
    fun isValidNumericInput(input: String): Boolean {
        if (input.isBlank()) return true

        // Remove commas for validation
        val cleanInput = input.replace(",", "")

        // Check if it's a valid number format
        return try {
            // Allow empty string, single minus sign, or valid number
            when {
                cleanInput.isEmpty() -> true
                cleanInput == "-" -> true
                cleanInput.matches(Regex("^-?\\d*\\.?\\d*$")) -> {
                    // Additional check: only one decimal point and minus only at start
                    val minusCount = cleanInput.count { it == '-' }
                    val decimalCount = cleanInput.count { it == '.' }
                    minusCount <= 1 && decimalCount <= 1 &&
                            (minusCount == 0 || cleanInput.startsWith("-"))
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Helper function to add a new row with partner-specific default currency
    fun addNewRow() {
        transactionRows = transactionRows + TransactionRow(currency = defaultCurrency)
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

    // Improved helper function to format number with commas (better decimal handling)
    fun formatWithCommas(number: String): String {
        if (number.isBlank()) return ""

        // Handle negative sign
        val isNegative = number.startsWith("-")
        val absoluteNumber = if (isNegative) number.substring(1) else number

        // Remove existing commas and validate
        val cleanNumber = absoluteNumber.replace(",", "").filter { it.isDigit() || it == '.' }
        if (cleanNumber.isBlank()) return if (isNegative) "-" else ""

        // Handle decimal point
        val parts = cleanNumber.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) ".${parts[1]}" else ""

        // Add commas to integer part only if it's longer than 3 digits
        val formattedInteger = if (integerPart.length > 3) {
            integerPart.reversed().chunked(3).joinToString(",").reversed()
        } else {
            integerPart
        }

        val sign = if (isNegative) "-" else ""
        return "$sign$formattedInteger$decimalPart"
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

        // Clear the table after saving - with partner-specific default currency
        transactionRows = listOf(TransactionRow(currency = defaultCurrency))
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

    // Edit Transaction Dialog (without notes) - FIXED FOCUS STATES with negative number support
    if (showEditDialog && editingTransaction != null) {
        // Initialize edit fields with formatted values when dialog opens
        LaunchedEffect(editingTransaction) {
            editingTransaction?.let { transaction ->
                editTzsReceived = formatNumber(transaction.tzsReceived)
                editForeignAmount = formatNumber(transaction.foreignGiven)
                editForeignCurrency = transaction.foreignCurrency
                editExchangeRate = transaction.exchangeRate.toString()
            }
        }

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
                    // TZS Field - FIXED FOCUS STATE with negative support
                    var isFocusedTzs by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = editTzsReceived,
                        onValueChange = { newValue ->
                            // Allow typing negative numbers without immediate formatting
                            if (isValidNumericInput(newValue)) {
                                editTzsReceived = newValue
                            }
                        },
                        label = { Text("TZS Received", fontSize = 9.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                val wasFocused = isFocusedTzs
                                isFocusedTzs = focusState.isFocused

                                if (wasFocused && !focusState.isFocused) {
                                    // Field just lost focus - format with commas
                                    if (editTzsReceived.isNotBlank() && editTzsReceived != "-") {
                                        val formatted = formatWithCommas(editTzsReceived)
                                        if (formatted != editTzsReceived) {
                                            editTzsReceived = formatted
                                        }
                                    }
                                } else if (!wasFocused && focusState.isFocused) {
                                    // Field just gained focus - remove commas
                                    if (editTzsReceived.isNotBlank() && editTzsReceived.contains(",")) {
                                        editTzsReceived = removeCommas(editTzsReceived)
                                    }
                                }
                            },
                        textStyle = LocalTextStyle.current.copy(fontSize = 9.sp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Foreign Amount Field - FIXED FOCUS STATE with negative support
                        var isFocusedForeign by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = editForeignAmount,
                            onValueChange = { newValue ->
                                // Allow typing negative numbers without immediate formatting
                                if (isValidNumericInput(newValue)) {
                                    editForeignAmount = newValue
                                }
                            },
                            label = { Text("${editForeignCurrency} Amount", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(2f)
                                .onFocusChanged { focusState ->
                                    val wasFocused = isFocusedForeign
                                    isFocusedForeign = focusState.isFocused

                                    if (wasFocused && !focusState.isFocused) {
                                        // Field just lost focus - format with commas
                                        if (editForeignAmount.isNotBlank() && editForeignAmount != "-") {
                                            val formatted = formatWithCommas(editForeignAmount)
                                            if (formatted != editForeignAmount) {
                                                editForeignAmount = formatted
                                            }
                                        }
                                    } else if (!wasFocused && focusState.isFocused) {
                                        // Field just gained focus - remove commas
                                        if (editForeignAmount.isNotBlank() && editForeignAmount.contains(",")) {
                                            editForeignAmount = removeCommas(editForeignAmount)
                                        }
                                    }
                                },
                            textStyle = LocalTextStyle.current.copy(fontSize = 9.sp)
                        )

                        OutlinedTextField(
                            value = editExchangeRate,
                            onValueChange = { editExchangeRate = it },
                            label = { Text("Rate", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 9.sp) // Smaller font for rate
                        )
                    }

                    // Currency display (read-only)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Currency: ",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp
                            )
                            Text(
                                text = editForeignCurrency,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = when (editForeignCurrency) {
                                    "CNY" -> MaterialTheme.colorScheme.secondary
                                    "USDT" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(Currency cannot be changed when editing)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Show calculated net values
                    val calcTzs = when (editForeignCurrency) {
                        "CNY" -> {
                            // For CNY: Net = (CNY amount * Rate) - TZS received
                            ((removeCommas(editForeignAmount).toDoubleOrNull() ?: 0.0) * (editExchangeRate.toDoubleOrNull() ?: 0.0)) -
                                    (removeCommas(editTzsReceived).toDoubleOrNull() ?: 0.0)
                        }
                        "USDT" -> {
                            // For USDT: Net = TZS received - (USDT amount * Rate) [original logic]
                            (removeCommas(editTzsReceived).toDoubleOrNull() ?: 0.0) -
                                    ((removeCommas(editForeignAmount).toDoubleOrNull() ?: 0.0) * (editExchangeRate.toDoubleOrNull() ?: 0.0))
                        }
                        else -> {
                            // Default to USDT logic
                            (removeCommas(editTzsReceived).toDoubleOrNull() ?: 0.0) -
                                    ((removeCommas(editForeignAmount).toDoubleOrNull() ?: 0.0) * (editExchangeRate.toDoubleOrNull() ?: 0.0))
                        }
                    }

                    val calcForeign = if ((editExchangeRate.toDoubleOrNull() ?: 0.0) > 0) {
                        calcTzs / (editExchangeRate.toDoubleOrNull() ?: 1.0)
                    } else 0.0

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                text = "Calculated Net Values:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Net TZS: ${formatNumber(calcTzs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Net ${editForeignCurrency}: ${formatNumber(calcForeign)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (calcForeign >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTzsReceived = removeCommas(editTzsReceived).toDoubleOrNull()
                        val newForeignAmount = removeCommas(editForeignAmount).toDoubleOrNull()
                        val newExchangeRate = editExchangeRate.toDoubleOrNull()

                        if (newTzsReceived != null && newForeignAmount != null && newExchangeRate != null && newExchangeRate > 0) {
                            editingTransaction?.let { oldTransaction ->
                                val updatedTransaction = oldTransaction.copy(
                                    tzsReceived = newTzsReceived,
                                    foreignGiven = newForeignAmount,
                                    // foreignCurrency = editForeignCurrency, // Removed - currency cannot be changed
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
                    Text("Save", fontSize = 9.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingTransaction = null
                }) {
                    Text("Cancel", fontSize = 9.sp)
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
        // Show all transactions view - MODIFIED FOR CUMULATIVE CALCULATIONS
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

                // Transactions by Date - MODIFIED FOR CUMULATIVE CALCULATIONS
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

                    // Calculate cumulative positions
                    val cumulativePositions = mutableMapOf<String, CumulativePositions>()

                    // Sort dates in ascending order for cumulative calculation
                    val sortedDates = groupedTransactions.keys.sortedBy {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).parse(it)
                    }

                    var cumulativeTzs = 0.0
                    var cumulativeCny = 0.0
                    var cumulativeUsdt = 0.0

                    sortedDates.forEach { dateString ->
                        val dayTransactions = groupedTransactions[dateString] ?: emptyList()

                        // Add this day's net positions to the cumulative totals
                        val dayTzs = dayTransactions.sumOf { it.netTzs }
                        val dayCny = dayTransactions.filter { it.foreignCurrency == "CNY" }.sumOf { it.netForeign }
                        val dayUsdt = dayTransactions.filter { it.foreignCurrency == "USDT" }.sumOf { it.netForeign }

                        cumulativeTzs += dayTzs
                        cumulativeCny += dayCny
                        cumulativeUsdt += dayUsdt

                        cumulativePositions[dateString] = CumulativePositions(
                            cumulativeTzs = cumulativeTzs,
                            cumulativeCny = cumulativeCny,
                            cumulativeUsdt = cumulativeUsdt
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedTransactions.forEach { (dateString, dayTransactions) ->
                            item {
                                TransactionDateGroup(
                                    dateString = dateString,
                                    transactions = dayTransactions,
                                    cumulativePositions = cumulativePositions[dateString],
                                    onUpdateTransaction = { transaction ->
                                        viewModel.updateTransaction(transaction)
                                    },
                                    onDeleteTransaction = { transaction ->
                                        deletingTransaction = transaction
                                        showDeleteDialog = true
                                    },
                                    formatWithCommas = ::formatWithCommas,
                                    removeCommas = ::removeCommas,
                                    isValidNumericInput = ::isValidNumericInput
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
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
                            )

                            // USDT Rate
                            OutlinedTextField(
                                value = defaultUsdtRate,
                                onValueChange = { viewModel.updateDefaultUsdtRate(it) },
                                label = { Text("USDT Rate", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
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

                // Optimized Transaction Table Section with auto-currency
                Card {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Add New Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }

                            OutlinedButton(
                                onClick = { addNewRow() }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add Row", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Table Header without currency column (since it's auto-set)
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
                                modifier = Modifier.weight(3f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                            Text(
                                text = defaultCurrency,
                                modifier = Modifier.weight(2.5f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp,
                                color = when (defaultCurrency) {
                                    "CNY" -> MaterialTheme.colorScheme.secondary
                                    "USDT" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = "RATE",
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 11.sp
                            )
                            // Space for negative toggle and delete button
                            Column(
                                modifier = Modifier.width(50.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "±",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "DEL",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        // Transaction Rows without currency dropdown
                        transactionRows.forEachIndexed { index, row ->
                            // Track which field is focused in this row
                            var focusedField by remember(row.id) { mutableStateOf<String?>(null) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // TZS Column
                                var isFocused by remember(row.id) { mutableStateOf(false) }

                                OutlinedTextField(
                                    value = row.tzs,
                                    onValueChange = { newValue ->
                                        if (isValidNumericInput(newValue)) {
                                            updateRow(row.id, row.copy(tzs = newValue))
                                        }
                                    },
                                    placeholder = { Text("0", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .weight(3f)
                                        .onFocusChanged { focusState ->
                                            val wasFocused = isFocused
                                            isFocused = focusState.isFocused

                                            // Update focused field tracker
                                            focusedField = if (focusState.isFocused) "tzs" else null

                                            if (wasFocused && !focusState.isFocused) {
                                                val currentValue = row.tzs
                                                if (currentValue.isNotBlank() && currentValue != "-") {
                                                    val formatted = formatWithCommas(currentValue)
                                                    if (formatted != currentValue) {
                                                        updateRow(row.id, row.copy(tzs = formatted))
                                                    }
                                                }
                                            } else if (!wasFocused && focusState.isFocused) {
                                                val currentValue = row.tzs
                                                if (currentValue.isNotBlank() && currentValue.contains(",")) {
                                                    val clean = removeCommas(currentValue)
                                                    updateRow(row.id, row.copy(tzs = clean))
                                                }
                                            }
                                        },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                )

                                // Foreign Amount Column (no dropdown, just amount)
                                var isFocusedAmount by remember(row.id) { mutableStateOf(false) }

                                OutlinedTextField(
                                    value = row.foreignAmount,
                                    onValueChange = { newValue ->
                                        if (isValidNumericInput(newValue)) {
                                            updateRow(row.id, row.copy(foreignAmount = newValue))
                                        }
                                    },
                                    placeholder = { Text("0", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .weight(2.5f)
                                        .onFocusChanged { focusState ->
                                            val wasFocused = isFocusedAmount
                                            isFocusedAmount = focusState.isFocused

                                            // Update focused field tracker
                                            focusedField = if (focusState.isFocused) "foreign" else null

                                            if (wasFocused && !focusState.isFocused) {
                                                val currentValue = row.foreignAmount
                                                if (currentValue.isNotBlank() && currentValue != "-") {
                                                    val formatted = formatWithCommas(currentValue)
                                                    if (formatted != currentValue) {
                                                        updateRow(row.id, row.copy(foreignAmount = formatted))
                                                    }
                                                }
                                            } else if (!wasFocused && focusState.isFocused) {
                                                val currentValue = row.foreignAmount
                                                if (currentValue.isNotBlank() && currentValue.contains(",")) {
                                                    val clean = removeCommas(currentValue)
                                                    updateRow(row.id, row.copy(foreignAmount = clean))
                                                }
                                            }
                                        },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                                )

                                // RATE Column
                                OutlinedTextField(
                                    value = row.rate,
                                    onValueChange = { updateRow(row.id, row.copy(rate = it)) },
                                    placeholder = {
                                        Text(if (row.currency == "CNY") defaultCnyRate else defaultUsdtRate, fontSize = 10.sp)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier.weight(1.5f),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )

                                // Negative toggle and delete button column
                                Column(
                                    modifier = Modifier.width(50.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    // Negative toggle button
                                    IconButton(
                                        onClick = {
                                            when (focusedField) {
                                                "tzs" -> {
                                                    val currentValue = row.tzs
                                                    val newValue = when {
                                                        currentValue.isBlank() -> "-"
                                                        currentValue.startsWith("-") -> currentValue.substring(1)
                                                        currentValue == "0" -> "-"
                                                        else -> "-$currentValue"
                                                    }
                                                    updateRow(row.id, row.copy(tzs = newValue))
                                                }
                                                "foreign" -> {
                                                    val currentValue = row.foreignAmount
                                                    val newValue = when {
                                                        currentValue.isBlank() -> "-"
                                                        currentValue.startsWith("-") -> currentValue.substring(1)
                                                        currentValue == "0" -> "-"
                                                        else -> "-$currentValue"
                                                    }
                                                    updateRow(row.id, row.copy(foreignAmount = newValue))
                                                }
                                            }
                                        },
                                        enabled = focusedField != null,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Toggle negative",
                                            tint = if (focusedField != null) {
                                                val currentValue = when (focusedField) {
                                                    "tzs" -> row.tzs
                                                    "foreign" -> row.foreignAmount
                                                    else -> ""
                                                }
                                                if (currentValue.startsWith("-")) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.primary
                                            } else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = { removeRow(row.id) },
                                        enabled = transactionRows.size > 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete row",
                                            tint = if (transactionRows.size > 1) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
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

// MODIFIED TransactionDateGroup to include cumulative positions with smaller fonts
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDateGroup(
    dateString: String,
    transactions: List<TransactionEntity>,
    cumulativePositions: CumulativePositions?,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    formatWithCommas: (String) -> String,
    removeCommas: (String) -> String,
    isValidNumericInput: (String) -> Boolean
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
            modifier = Modifier.padding(10.dp) // Reduced padding
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
                    fontSize = 13.sp // Reduced font size
                )
                Text(
                    text = "${transactions.size} transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp // Reduced font size
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Elastic Table Header (removed NET TZS column)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(6.dp), // Reduced padding
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "TZS Received",
                    modifier = Modifier.weight(3.2f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp // Reduced font size
                )
                Text(
                    text = "Foreign Amount",
                    modifier = Modifier.weight(3f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp // Reduced font size
                )
                Text(
                    text = "Currency",
                    modifier = Modifier.weight(1.5f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp // Reduced font size
                )
                Text(
                    text = "Rate",
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp // Reduced font size
                )
                Spacer(modifier = Modifier.width(70.dp)) // Space for action buttons
            }

            // Transaction Rows - Elastic layout without NET TZS column, with negative support
            transactions.forEach { transaction ->
                val isEditing = editingTransactionId == transaction.id
                val editData = editingValues[transaction.id]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(6.dp), // Reduced padding
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing && editData != null) {
                        // Editable TZS field - Elastic sizing with negative support
                        var isFocusedEditTzsList by remember(transaction.id) { mutableStateOf(false) }

                        OutlinedTextField(
                            value = editData.tzs,
                            onValueChange = { newValue ->
                                if (isValidNumericInput(newValue)) {
                                    editingValues = editingValues + (transaction.id to editData.copy(tzs = newValue))
                                }
                            },
                            label = { Text("TZS", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(3.2f)
                                .widthIn(min = 70.dp)
                                .onFocusChanged { focusState ->
                                    val wasFocused = isFocusedEditTzsList
                                    isFocusedEditTzsList = focusState.isFocused

                                    if (wasFocused && !focusState.isFocused) {
                                        val currentValue = editData.tzs
                                        if (currentValue.isNotBlank() && currentValue != "-") {
                                            val formatted = formatWithCommas(currentValue)
                                            if (formatted != currentValue) {
                                                editingValues = editingValues + (transaction.id to editData.copy(tzs = formatted))
                                            }
                                        }
                                    } else if (!wasFocused && focusState.isFocused) {
                                        val currentValue = editData.tzs
                                        if (currentValue.isNotBlank() && currentValue.contains(",")) {
                                            val clean = removeCommas(currentValue)
                                            editingValues = editingValues + (transaction.id to editData.copy(tzs = clean))
                                        }
                                    }
                                },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp) // Reduced font size
                        )

                        // Editable Foreign Amount field - Elastic sizing with negative support
                        var isFocusedEditForeignList by remember(transaction.id) { mutableStateOf(false) }

                        OutlinedTextField(
                            value = editData.foreignAmount,
                            onValueChange = { newValue ->
                                if (isValidNumericInput(newValue)) {
                                    editingValues = editingValues + (transaction.id to editData.copy(foreignAmount = newValue))
                                }
                            },
                            label = { Text("${editData.currency}", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(3f)
                                .widthIn(min = 70.dp)
                                .onFocusChanged { focusState ->
                                    val wasFocused = isFocusedEditForeignList
                                    isFocusedEditForeignList = focusState.isFocused

                                    if (wasFocused && !focusState.isFocused) {
                                        val currentValue = editData.foreignAmount
                                        if (currentValue.isNotBlank() && currentValue != "-") {
                                            val formatted = formatWithCommas(currentValue)
                                            if (formatted != currentValue) {
                                                editingValues = editingValues + (transaction.id to editData.copy(foreignAmount = formatted))
                                            }
                                        }
                                    } else if (!wasFocused && focusState.isFocused) {
                                        val currentValue = editData.foreignAmount
                                        if (currentValue.isNotBlank() && currentValue.contains(",")) {
                                            val clean = removeCommas(currentValue)
                                            editingValues = editingValues + (transaction.id to editData.copy(foreignAmount = clean))
                                        }
                                    }
                                },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp) // Reduced font size
                        )

                        // Currency Display - Read-only
                        Text(
                            text = editData.currency,
                            modifier = Modifier.weight(1.5f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, // Reduced font size
                            color = when (editData.currency) {
                                "CNY" -> MaterialTheme.colorScheme.secondary
                                "USDT" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        // Editable Rate - Elastic sizing
                        OutlinedTextField(
                            value = editData.rate,
                            onValueChange = { newValue ->
                                editingValues = editingValues + (transaction.id to editData.copy(rate = newValue))
                            },
                            label = { Text("Rate", fontSize = 9.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .weight(2f)
                                .widthIn(min = 50.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp) // Reduced font size
                        )

                        // Action Buttons (Save/Cancel)
                        Row(
                            modifier = Modifier.width(70.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    // Save changes (currency cannot be changed)
                                    val updatedTransaction = transaction.copy(
                                        tzsReceived = removeCommas(editData.tzs).toDoubleOrNull() ?: 0.0,
                                        foreignGiven = removeCommas(editData.foreignAmount).toDoubleOrNull() ?: 0.0,
                                        // foreignCurrency = editData.currency, // Removed - currency cannot be changed
                                        exchangeRate = editData.rate.toDoubleOrNull() ?: 0.0,
                                        notes = "", // No notes
                                        lastModified = Date()
                                    )
                                    onUpdateTransaction(updatedTransaction)
                                    editingTransactionId = null
                                    editingValues = editingValues - transaction.id
                                },
                                modifier = Modifier.size(28.dp) // Reduced size
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp) // Reduced size
                                )
                            }

                            IconButton(
                                onClick = {
                                    // Cancel editing
                                    editingTransactionId = null
                                    editingValues = editingValues - transaction.id
                                },
                                modifier = Modifier.size(28.dp) // Reduced size
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp) // Reduced size
                                )
                            }
                        }
                    } else {
                        // Display mode - Elastic layout without NET TZS
                        Text(
                            text = formatNumber(transaction.tzsReceived),
                            modifier = Modifier.weight(3.2f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp, // Reduced font size
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = formatNumber(transaction.foreignGiven),
                            modifier = Modifier.weight(3f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 11.sp, // Reduced font size
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = transaction.foreignCurrency,
                            modifier = Modifier.weight(1.5f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, // Reduced font size
                            color = when (transaction.foreignCurrency) {
                                "CNY" -> MaterialTheme.colorScheme.secondary
                                "USDT" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Text(
                            text = formatNumber(transaction.exchangeRate),
                            modifier = Modifier.weight(2f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 10.sp // Reduced font size
                        )

                        // Action Buttons (Edit/Delete)
                        Row(
                            modifier = Modifier.width(70.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    // Start editing with comma-formatted values
                                    editingTransactionId = transaction.id
                                    editingValues = editingValues + (transaction.id to EditableTransactionData(
                                        tzs = formatNumber(transaction.tzsReceived),
                                        foreignAmount = formatNumber(transaction.foreignGiven),
                                        currency = transaction.foreignCurrency,
                                        rate = transaction.exchangeRate.toString(),
                                        notes = "" // No notes
                                    ))
                                },
                                modifier = Modifier.size(28.dp) // Reduced size
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Edit",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp) // Reduced size
                                )
                            }

                            IconButton(
                                onClick = { onDeleteTransaction(transaction) },
                                modifier = Modifier.size(28.dp) // Reduced size
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp) // Reduced size
                                )
                            }
                        }
                    }
                }
            }

            // Date Net Positions (SMALLER FONTS)
            Spacer(modifier = Modifier.height(4.dp)) // Reduced spacing
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(6.dp) // Reduced padding
                ) {
                    Text(
                        text = "Net Positions for $dateString",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp // Reduced font size
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmallNetPositionItem("TZS", dateTzs)
                        SmallNetPositionItem("CNY", dateCny)
                        SmallNetPositionItem("USDT", dateUsdt)
                    }
                }
            }

            // CUMULATIVE POSITIONS (NEW SECTION)
            cumulativePositions?.let { cumulative ->
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp) // Compact padding
                    ) {
                        Text(
                            text = "📊 Cumulative Positions (through $dateString)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 10.sp // Small font size
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SmallNetPositionItem("TZS", cumulative.cumulativeTzs)
                            SmallNetPositionItem("CNY", cumulative.cumulativeCny)
                            SmallNetPositionItem("USDT", cumulative.cumulativeUsdt)
                        }
                    }
                }
            }
        }
    }
}

// NEW COMPONENT: Smaller version of NetPositionItem for space efficiency
@Composable
private fun SmallNetPositionItem(
    currency: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = currency,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp // Very small font
        )
        Text(
            text = formatNumberWithCommas(amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontSize = 10.sp // Small font
        )
    }
}

// Note: Using formatNumberWithCommas function from SimpleMainPage.kt

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
    val formatted = String.format("%.2f", number)
    val parts = formatted.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else "00"

    // Handle negative sign
    val isNegative = integerPart.startsWith("-")
    val absoluteIntegerPart = if (isNegative) integerPart.substring(1) else integerPart

    // Add commas to integer part
    val formattedInteger = if (absoluteIntegerPart.length > 3) {
        absoluteIntegerPart.reversed().chunked(3).joinToString(",").reversed()
    } else {
        absoluteIntegerPart
    }

    val sign = if (isNegative) "-" else ""
    return "$sign$formattedInteger.$decimalPart"
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