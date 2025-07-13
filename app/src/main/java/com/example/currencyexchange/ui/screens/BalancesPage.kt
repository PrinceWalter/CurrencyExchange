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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.currencyexchange.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.*

// Data class for balance items
data class BalanceItem(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var amount: String = "",
    var currency: String = "TZS",
    var rate: String = "",
    val isNetPosition: Boolean = false,
    val isFixedType: Boolean = false // For ALIPAY (CNY) and USDT rows
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalancesPage(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    // State for balance items
    var balanceItems by remember { mutableStateOf(listOf<BalanceItem>()) }
    var isLoadingNetPosition by remember { mutableStateOf(true) }
    var netPositionTzs by remember { mutableStateOf(0.0) }

    // Default rates
    var defaultCnyRate by remember { mutableStateOf("376") }
    var defaultUsdtRate by remember { mutableStateOf("2380") }

    val coroutineScope = rememberCoroutineScope()

    // Load net position and default rates on startup
    LaunchedEffect(Unit) {
        try {
            // Get cumulative net positions
            val summary = viewModel.getCumulativeNetPositions()
            netPositionTzs = summary.totalNetTzs

            // Get default exchange rates
            val rates = viewModel.getDefaultExchangeRates()
            defaultCnyRate = rates["CNY"]?.toString() ?: "376"
            defaultUsdtRate = rates["USDT"]?.toString() ?: "2380"

            // Initialize with net position, ALIPAY, and USDT as fixed rows
            balanceItems = listOf(
                BalanceItem(
                    description = "Overall Net Position",
                    amount = formatNumberWithCommas(netPositionTzs),
                    currency = "TZS",
                    rate = "",
                    isNetPosition = true,
                    isFixedType = true
                ),
                BalanceItem(
                    description = "ALIPAY",
                    amount = "",
                    currency = "CNY",
                    rate = "",
                    isFixedType = true
                ),
                BalanceItem(
                    description = "USDT",
                    amount = "",
                    currency = "USDT",
                    rate = "",
                    isFixedType = true
                ),
                BalanceItem() // Empty TZS row to start adding balances
            )

        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoadingNetPosition = false
        }
    }

    // Helper functions
    fun formatNumberWithCommas(number: Double): String {
        val formatted = String.format("%.2f", number)
        val parts = formatted.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1] else "00"

        val isNegative = integerPart.startsWith("-")
        val absoluteIntegerPart = if (isNegative) integerPart.substring(1) else integerPart

        val formattedInteger = if (absoluteIntegerPart.length > 3) {
            absoluteIntegerPart.reversed().chunked(3).joinToString(",").reversed()
        } else {
            absoluteIntegerPart
        }

        val sign = if (isNegative) "-" else ""
        return "$sign$formattedInteger.$decimalPart"
    }

    fun formatWithCommas(number: String): String {
        if (number.isBlank()) return ""

        val cleanNumber = number.replace(",", "").filter { it.isDigit() || it == '.' || it == '-' }
        if (cleanNumber.isBlank()) return ""

        val isNegative = cleanNumber.startsWith("-")
        val absoluteNumber = if (isNegative) cleanNumber.substring(1) else cleanNumber

        val parts = absoluteNumber.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) ".${parts[1]}" else ""

        val formattedInteger = if (integerPart.length > 3) {
            integerPart.reversed().chunked(3).joinToString(",").reversed()
        } else {
            integerPart
        }

        val sign = if (isNegative) "-" else ""
        return "$sign$formattedInteger$decimalPart"
    }

    fun removeCommas(number: String): String {
        return number.replace(",", "")
    }

    fun updateBalanceItem(itemId: String, updatedItem: BalanceItem) {
        balanceItems = balanceItems.map { if (it.id == itemId) updatedItem else it }
    }

    fun addNewBalanceItem() {
        balanceItems = balanceItems + BalanceItem()
    }

    fun removeBalanceItem(itemId: String) {
        // Don't allow removing the net position item, fixed items, or if only essential items remain
        val item = balanceItems.find { it.id == itemId }
        if (item != null && !item.isNetPosition && !item.isFixedType && balanceItems.size > 4) {
            balanceItems = balanceItems.filter { it.id != itemId }
        }
    }

    // Calculate total TZS
    fun calculateTotalTzs(): Double {
        return balanceItems.sumOf { item ->
            val amount = removeCommas(item.amount).toDoubleOrNull() ?: 0.0
            when (item.currency) {
                "TZS" -> amount
                "CNY" -> {
                    val rate = if (item.rate.isNotBlank()) item.rate.toDoubleOrNull() ?: 0.0 else defaultCnyRate.toDoubleOrNull() ?: 376.0
                    amount * rate
                }
                "USDT" -> {
                    val rate = if (item.rate.isNotBlank()) item.rate.toDoubleOrNull() ?: 0.0 else defaultUsdtRate.toDoubleOrNull() ?: 2380.0
                    amount * rate
                }
                else -> 0.0
            }
        }
    }

    val totalTzs = calculateTotalTzs()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        TopAppBar(
            title = { Text("Balances Overview", fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (isLoadingNetPosition) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Loading net position...",
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Default Rates Card
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Default Exchange Rates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = defaultCnyRate,
                                onValueChange = { defaultCnyRate = it },
                                label = { Text("CNY Rate", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )

                            OutlinedTextField(
                                value = defaultUsdtRate,
                                onValueChange = { defaultUsdtRate = it },
                                label = { Text("USDT Rate", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(top = 8.dp)
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

                // Balances Table Card
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
                                text = "Balance Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Button(
                                onClick = { addNewBalanceItem() }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Table Header (optimized for Galaxy S8)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Description",
                                modifier = Modifier.weight(2.7f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Amount",
                                modifier = Modifier.weight(3.7f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "Rate",
                                modifier = Modifier.weight(2.3f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "TZS Equivalent",
                                modifier = Modifier.weight(2.3f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.width(20.dp)) // Delete button space (smaller for Galaxy S8)
                        }

                        // Balance Items
                        balanceItems.forEachIndexed { index, item ->
                            val tzsEquivalent = remember(item.amount, item.currency, item.rate, defaultCnyRate, defaultUsdtRate) {
                                val amount = removeCommas(item.amount).toDoubleOrNull() ?: 0.0
                                when (item.currency) {
                                    "TZS" -> amount
                                    "CNY" -> {
                                        val rate = if (item.rate.isNotBlank()) item.rate.toDoubleOrNull() ?: 0.0 else defaultCnyRate.toDoubleOrNull() ?: 376.0
                                        amount * rate
                                    }
                                    "USDT" -> {
                                        val rate = if (item.rate.isNotBlank()) item.rate.toDoubleOrNull() ?: 0.0 else defaultUsdtRate.toDoubleOrNull() ?: 2380.0
                                        amount * rate
                                    }
                                    else -> 0.0
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Description
                                if (item.isNetPosition || item.isFixedType) {
                                    // Read-only for net position and fixed items
                                    Text(
                                        text = item.description,
                                        modifier = Modifier.weight(2.7f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = when {
                                            item.isNetPosition -> MaterialTheme.colorScheme.primary
                                            item.currency == "CNY" -> MaterialTheme.colorScheme.secondary
                                            item.currency == "USDT" -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = item.description,
                                        onValueChange = { newValue ->
                                            updateBalanceItem(item.id, item.copy(description = newValue))
                                        },
                                        placeholder = { Text("", fontSize = 9.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(2.7f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 9.sp)
                                    )
                                }

                                // Amount (optimized for 9 digits on Galaxy S8)
                                if (item.isNetPosition) {
                                    // Read-only net position amount
                                    Text(
                                        text = item.amount,
                                        modifier = Modifier
                                            .weight(3.7f)
                                            .widthIn(min = 100.dp, max = 120.dp),
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (netPositionTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    var isFocusedAmount by remember(item.id) { mutableStateOf(false) }

                                    OutlinedTextField(
                                        value = item.amount,
                                        onValueChange = { newValue ->
                                            val cleanValue = newValue.replace(",", "")
                                            if (cleanValue.isEmpty() || cleanValue.all { it.isDigit() || it == '.' || it == '-' }) {
                                                updateBalanceItem(item.id, item.copy(amount = newValue))
                                            }
                                        },
                                        placeholder = { Text("0.00", fontSize = 9.sp) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(3.7f)
                                            .widthIn(min = 100.dp, max = 120.dp)
                                            .onFocusChanged { focusState ->
                                                val wasFocused = isFocusedAmount
                                                isFocusedAmount = focusState.isFocused

                                                if (wasFocused && !focusState.isFocused) {
                                                    val currentValue = item.amount
                                                    if (currentValue.isNotBlank()) {
                                                        val formatted = formatWithCommas(currentValue)
                                                        if (formatted != currentValue) {
                                                            updateBalanceItem(item.id, item.copy(amount = formatted))
                                                        }
                                                    }
                                                } else if (!wasFocused && focusState.isFocused) {
                                                    val currentValue = item.amount
                                                    if (currentValue.isNotBlank() && currentValue.contains(",")) {
                                                        val clean = removeCommas(currentValue)
                                                        updateBalanceItem(item.id, item.copy(amount = clean))
                                                    }
                                                }
                                            },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 9.sp)
                                    )
                                }

                                // Rate (optimized for 4 digits on Galaxy S8)
                                if (item.isNetPosition || item.currency == "TZS") {
                                    Text(
                                        text = "-",
                                        modifier = Modifier
                                            .weight(2.3f)
                                            .widthIn(min = 50.dp, max = 70.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    OutlinedTextField(
                                        value = item.rate,
                                        onValueChange = { newValue ->
                                            updateBalanceItem(item.id, item.copy(rate = newValue))
                                        },
                                        placeholder = {
                                            Text(
                                                if (item.currency == "CNY") defaultCnyRate else defaultUsdtRate,
                                                fontSize = 9.sp
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(2.5f)
                                            .widthIn(min = 50.dp, max = 70.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                                    )
                                }

                                // TZS Equivalent (optimized for Galaxy S8)
                                Text(
                                    text = formatNumberWithCommas(tzsEquivalent),
                                    modifier = Modifier
                                        .weight(2.3f)
                                        .widthIn(min = 90.dp, max = 120.dp),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.sp,
                                    color = if (tzsEquivalent >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )

                                // Delete button (sized for Galaxy S8)
                                if (item.isNetPosition || item.isFixedType) {
                                    Spacer(modifier = Modifier.width(20.dp))
                                } else {
                                    IconButton(
                                        onClick = { removeBalanceItem(item.id) },
                                        enabled = balanceItems.size > 4, // Keep at least 4 items (net position + 2 fixed + 1 TZS)
                                        modifier = Modifier.size(19.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = if (balanceItems.size > 4) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Total Row
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💰 TOTAL BALANCE",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${formatNumberWithCommas(totalTzs)} TZS",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (totalTzs >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}