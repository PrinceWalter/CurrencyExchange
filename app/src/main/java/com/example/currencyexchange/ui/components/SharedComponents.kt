package com.example.currencyexchange.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun NetPositionItem(
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatNumberWithCommas(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
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
    val formattedInteger = if (absoluteIntegerPart.length > 3) {
        absoluteIntegerPart.reversed().chunked(3).joinToString(",").reversed()
    } else {
        absoluteIntegerPart
    }

    val sign = if (isNegative) "-" else ""
    return "$sign$formattedInteger.$decimalPart"
}