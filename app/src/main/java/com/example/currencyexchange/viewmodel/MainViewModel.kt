package com.example.currencyexchange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.example.currencyexchange.data.repository.*
import com.example.currencyexchange.data.dao.PartnerSummary
import com.example.currencyexchange.data.backup.*

// Data class for analysis results
data class AnalysisResult(
    val totalTzsReceived: Double,
    val totalCnySold: Double,
    val totalUsdtSold: Double,
    val totalTransactions: Int,
    val cnyTransactions: Int,
    val usdtTransactions: Int,
    val dateRange: String
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val backupRepository: BackupRepository
) : ViewModel() {

    val partners = partnerRepository.getAllActivePartners()

    private val _selectedPartnerId = MutableStateFlow(0L)
    val selectedPartnerId = _selectedPartnerId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredPartners = combine(partners, searchQuery) { partnerList, query ->
        if (query.isBlank()) {
            partnerList
        } else {
            partnerList.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectPartner(partnerId: Long) {
        _selectedPartnerId.value = partnerId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addPartner(name: String) {
        viewModelScope.launch {
            try {
                // Check if partner with this name already exists
                val currentPartners = partners.first()
                val isDuplicate = currentPartners.any {
                    it.name.trim().equals(name.trim(), ignoreCase = true)
                }

                if (!isDuplicate) {
                    val partnerId = partnerRepository.addPartner(name)
                    _selectedPartnerId.value = partnerId
                }
                // If duplicate exists, we don't add it (validation should have caught this in UI)
            } catch (e: Exception) {
                // Handle error - could add error state management here
            }
        }
    }

    fun updatePartnerName(partnerId: Long, newName: String) {
        viewModelScope.launch {
            try {
                val partner = partnerRepository.getPartnerById(partnerId)
                if (partner != null) {
                    // Check if the new name conflicts with existing partners (excluding current partner)
                    val currentPartners = partners.first()
                    val isDuplicate = currentPartners.any {
                        it.name.trim().equals(newName.trim(), ignoreCase = true) && it.id != partnerId
                    }

                    if (!isDuplicate) {
                        val updatedPartner = partner.copy(name = newName.trim())
                        partnerRepository.updatePartner(updatedPartner)
                    }
                    // If duplicate exists, we don't update it (validation should have caught this in UI)
                }
            } catch (e: Exception) {
                // Handle error - could add error state management here
            }
        }
    }

    fun deletePartner(partnerId: Long) {
        viewModelScope.launch {
            partnerRepository.deletePartner(partnerId)
            if (_selectedPartnerId.value == partnerId) {
                _selectedPartnerId.value = 0L
            }
        }
    }

    suspend fun getPartnerSummaryByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ) = transactionRepository.getPartnerSummaryByDateRange(
        partnerId,
        getStartOfDay(startDate),
        getEndOfDay(endDate)
    )

    suspend fun getTransactionsByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ) = transactionRepository.getTransactionsByDateRange(
        partnerId,
        getStartOfDay(startDate),
        getEndOfDay(endDate)
    )

    suspend fun getDefaultExchangeRates() = exchangeRateRepository.getAllDefaultRates()

    suspend fun getCumulativeNetPositions(): PartnerSummary {
        val currentPartners = partners.first()

        if (currentPartners.isEmpty()) {
            return PartnerSummary(
                totalNetTzs = 0.0,
                totalNetCny = 0.0,
                totalNetUsdt = 0.0,
                transactionCount = 0
            )
        }

        var cumulativeTzs = 0.0
        var cumulativeCny = 0.0
        var cumulativeUsdt = 0.0
        var cumulativeTransactions = 0

        currentPartners.forEach { partner ->
            try {
                val summary = partnerRepository.getPartnerSummary(partner.id)
                cumulativeTzs += summary.totalNetTzs
                cumulativeCny += summary.totalNetCny
                cumulativeUsdt += summary.totalNetUsdt
                cumulativeTransactions += summary.transactionCount
            } catch (e: Exception) {
                // Skip partners with no transactions or errors
            }
        }

        return PartnerSummary(
            totalNetTzs = cumulativeTzs,
            totalNetCny = cumulativeCny,
            totalNetUsdt = cumulativeUsdt,
            transactionCount = cumulativeTransactions
        )
    }

    // NEW ANALYSIS FUNCTIONS
    suspend fun performCrossPartnerAnalysis(startDate: Date, endDate: Date): AnalysisResult {
        val currentPartners = partners.first()

        // Make dates inclusive by setting time to start and end of day
        val inclusiveStartDate = getStartOfDay(startDate)
        val inclusiveEndDate = getEndOfDay(endDate)

        var totalTzsReceived = 0.0
        var totalCnySold = 0.0
        var totalUsdtSold = 0.0
        var totalTransactions = 0
        var cnyTransactions = 0
        var usdtTransactions = 0

        currentPartners.forEach { partner ->
            try {
                val transactions = transactionRepository.getTransactionsByDateRange(
                    partnerId = partner.id,
                    startDate = inclusiveStartDate,
                    endDate = inclusiveEndDate
                )

                transactions.forEach { transaction ->
                    totalTzsReceived += transaction.tzsReceived
                    totalTransactions++

                    when (transaction.foreignCurrency) {
                        "CNY" -> {
                            totalCnySold += transaction.foreignGiven
                            cnyTransactions++
                        }
                        "USDT" -> {
                            totalUsdtSold += transaction.foreignGiven
                            usdtTransactions++
                        }
                    }
                }
            } catch (e: Exception) {
                // Skip partners with errors
            }
        }

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateRange = if (isSameDay(startDate, endDate)) {
            dateFormat.format(startDate)
        } else {
            "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
        }

        return AnalysisResult(
            totalTzsReceived = totalTzsReceived,
            totalCnySold = totalCnySold,
            totalUsdtSold = totalUsdtSold,
            totalTransactions = totalTransactions,
            cnyTransactions = cnyTransactions,
            usdtTransactions = usdtTransactions,
            dateRange = dateRange
        )
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { time = date1 }
        val cal2 = java.util.Calendar.getInstance().apply { time = date2 }

        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun getStartOfDay(date: Date): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun getEndOfDay(date: Date): Date {
        val calendar = java.util.Calendar.getInstance()
        calendar.time = date
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        return calendar.time
    }

    // Export functions for date range reports
    suspend fun exportDateRangeToPdf(
        outputStream: java.io.OutputStream,
        partnerName: String,
        transactions: List<com.example.currencyexchange.data.entities.TransactionEntity>,
        startDate: Date,
        endDate: Date,
        summary: PartnerSummary
    ): Boolean {
        return try {
            generatePdfReport(outputStream, partnerName, transactions, startDate, endDate, summary)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportDateRangeToExcel(
        outputStream: java.io.OutputStream,
        partnerName: String,
        transactions: List<com.example.currencyexchange.data.entities.TransactionEntity>,
        startDate: Date,
        endDate: Date,
        summary: PartnerSummary
    ): Boolean {
        return try {
            generateExcelReport(outputStream, partnerName, transactions, startDate, endDate, summary)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generatePdfReport(
        outputStream: java.io.OutputStream,
        partnerName: String,
        transactions: List<com.example.currencyexchange.data.entities.TransactionEntity>,
        startDate: Date,
        endDate: Date,
        summary: PartnerSummary
    ) {
        // Generate professional HTML report
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        val htmlContent = buildString {
            append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Currency Exchange Report - $partnerName</title>
                <style>
                    body { 
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
                        margin: 30px; 
                        background-color: #f8f9fa;
                        color: #030303;
                        font-weight: bold;
                        line-height: 1.6;
                    }
                    .container {
                        background-color: white;
                        padding: 30px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .header { 
                        text-align: center; 
                        margin-bottom: 40px; 
                        border-bottom: 3px solid #007bff;
                        padding-bottom: 20px;
                    }
                    .header h1 {
                        color: #007bff;
                        margin-bottom: 10px;
                        font-size: 28px;
                    }
                    .header h2 {
                        color: #495057;
                        margin-bottom: 5px;
                        font-size: 20px;
                    }
                    .info-section { 
                        margin-bottom: 30px; 
                    }
                    .info-section h3 {
                        color: #007bff;
                        border-bottom: 2px solid #e9ecef;
                        padding-bottom: 5px;
                        margin-bottom: 15px;
                    }
                    .summary-table, .transaction-table { 
                        width: 100%; 
                        border-collapse: collapse; 
                        margin-bottom: 20px;
                        font-size: 14px;
                    }
                    th, td { 
                        border: 1px solid #dee2e6; 
                        padding: 12px 8px; 
                        text-align: left; 
                    }
                    th { 
                        background-color: #007bff; 
                        color: white;
                        font-weight: bold; 
                        text-align: center;
                    }
                    .amount { color: #030303; font-weight: bold; text-align: right; font-family: 'Calibri', monospace; }
                    .positive { color: #28a745; font-weight: bold; }
                    .negative { color: #dc3545; font-weight: bold; }
                    .total-row { 
                        background-color: #e3f2fd; 
                        font-weight: bold; 
                    }
                    .daily-summary-row {
                        background-color: #fff3cd;
                        font-weight: bold;
                        font-style: italic;
                    }
                    .cumulative-summary-row {
                        background-color: #d1ecf1;
                        font-weight: bold;
                        font-style: italic;
                    }
                    .currency-cell {
                        text-align: center;
                        font-weight: bold;
                        background-color: #f8f9fa;
                    }
                    .date-cell {
                        font-family: 'Courier New', monospace;
                        background-color: #f8f9fa;
                    }
                    @media print {
                        body { margin: 0; background-color: white; }
                        .container { box-shadow: none; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>💰 Currency Exchange Report</h1>
                        <h2>Client: $partnerName</h2>
                        <p><strong>Date Range:</strong> ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}</p>
                        <p><strong>Generated:</strong> ${dateTimeFormat.format(Date())}</p>
                    </div>
                    
                    <div class="info-section">
                        <h3>📊 Summary</h3>
                        <table class="summary-table">
                            <tr><th>Currency</th><th>Net Position</th><th>Status</th></tr>
                            <tr class="${if (summary.totalNetTzs >= 0) "positive" else "negative"}">
                                <td class="currency-cell">TZS</td>
                                <td class="amount ${if (summary.totalNetTzs >= 0) "positive" else "negative"}">${formatNumber(summary.totalNetTzs)}</td>
                                <td style="text-align: center;">${if (summary.totalNetTzs >= 0) "✅ Credit" else "❌ Debit"}</td>
                            </tr>
                            <tr class="${if (summary.totalNetCny >= 0) "positive" else "negative"}">
                                <td class="currency-cell">CNY</td>
                                <td class="amount ${if (summary.totalNetCny >= 0) "positive" else "negative"}">${formatNumber(summary.totalNetCny)}</td>
                                <td style="text-align: center;">${if (summary.totalNetCny >= 0) "✅ Credit" else "❌ Debit"}</td>
                            </tr>
                            <tr class="${if (summary.totalNetUsdt >= 0) "positive" else "negative"}">
                                <td class="currency-cell">USDT</td>
                                <td class="amount ${if (summary.totalNetUsdt >= 0) "positive" else "negative"}">${formatNumber(summary.totalNetUsdt)}</td>
                                <td style="text-align: center;">${if (summary.totalNetUsdt >= 0) "✅ Credit" else "❌ Debit"}</td>
                            </tr>
                            <tr class="total-row">
                                <td><strong>Total Transactions</strong></td>
                                <td class="amount"><strong>${transactions.size}</strong></td>
                                <td style="text-align: center;"><strong>📈 Records</strong></td>
                            </tr>
                        </table>
                    </div>
                    
                    <div class="info-section">
                        <h3>📋 Transaction Details</h3>
                        <table class="transaction-table">
                            <tr>
                                <th>Date</th>
                                <th>TZS Received</th>
                                <th>Foreign Amount</th>
                                <th>Currency</th>
                                <th>Exchange Rate</th>
                                <th>Net TZS</th>
                            </tr>
        """)

            if (transactions.isEmpty()) {
                append("""
                <tr>
                    <td colspan="6" style="text-align: center; padding: 30px; color: #6c757d; font-style: italic;">
                        No transactions found in the selected date range
                    </td>
                </tr>
            """)
            } else {
                // Group transactions by date
                val transactionsByDate = transactions.groupBy {
                    dateFormat.format(it.date)
                }.toSortedMap(compareBy {
                    dateFormat.parse(it)
                })

                // Variables for cumulative calculations
                var cumulativeTzsReceived = 0.0
                var cumulativeNetTzs = 0.0

                transactionsByDate.forEach { (dateString, dayTransactions) ->
                    // Calculate daily totals
                    val dailyTzsReceived = dayTransactions.sumOf { it.tzsReceived }
                    val dailyNetTzs = dayTransactions.sumOf { transaction ->
                        // Net TZS = Foreign Amount * Exchange Rate
                        transaction.foreignGiven * transaction.exchangeRate
                    }

                    // Get the last transaction's exchange rate for the day
                    val lastTransactionRate = dayTransactions.lastOrNull()?.exchangeRate ?: 1.0
                    val lastTransactionCurrency = dayTransactions.lastOrNull()?.foreignCurrency ?: "CNY"

                    // Calculate daily net and net foreign
                    val dailyNet = dailyTzsReceived - dailyNetTzs
                    val dailyNetForeign = if (lastTransactionRate > 0) dailyNet / lastTransactionRate else 0.0

                    // Update cumulative totals
                    cumulativeTzsReceived += dailyTzsReceived
                    cumulativeNetTzs += dailyNetTzs
                    val cumulativeNet = cumulativeTzsReceived - cumulativeNetTzs
                    val cumulativeNetForeign = if (lastTransactionRate > 0) cumulativeNet / lastTransactionRate else 0.0

                    // Add individual transactions for this date
                    dayTransactions.sortedBy { it.date }.forEach { transaction ->
                        val netTzs = transaction.foreignGiven * transaction.exchangeRate
                        append("""
                        <tr>
                            <td class="date-cell">${dateFormat.format(transaction.date)}</td>
                            <td class="amount">${formatNumber(transaction.tzsReceived)}</td>
                            <td class="amount">${formatNumber(transaction.foreignGiven)}</td>
                            <td class="currency-cell">${transaction.foreignCurrency}</td>
                            <td class="amount">${formatNumber(transaction.exchangeRate)}</td>
                            <td class="amount ${if (netTzs >= 0) "positive" else "negative"}">${formatNumber(netTzs)}</td>
                        </tr>
                    """)
                    }

                    // Add daily summary row
                    append("""
                    <tr class="daily-summary-row">
                        <td class="date-cell"><strong>📅 Daily Total ($dateString)</strong></td>
                        <td class="amount"><strong>${formatNumber(dailyTzsReceived)}</strong></td>
                        <td class="amount ${if (dailyNetForeign >= 0) "positive" else "negative"}"><strong>${formatNumber(dailyNetForeign)}</strong></td>
                        <td class="currency-cell"><strong>$lastTransactionCurrency</strong></td>
                        <td class="amount"><strong>${formatNumber(lastTransactionRate)}</strong></td>
                        <td class="amount ${if (dailyNet >= 0) "positive" else "negative"}"><strong>${formatNumber(dailyNet)}</strong></td>
                    </tr>
                """)

                    // Add cumulative summary row
                    append("""
                    <tr class="cumulative-summary-row">
                        <td class="date-cell"><strong>📊 Cumulative Total (through $dateString)</strong></td>
                        <td class="amount"><strong>${formatNumber(cumulativeTzsReceived)}</strong></td>
                        <td class="amount ${if (cumulativeNetForeign >= 0) "positive" else "negative"}"><strong>${formatNumber(cumulativeNetForeign)}</strong></td>
                        <td class="currency-cell"><strong>$lastTransactionCurrency</strong></td>
                        <td class="amount"><strong>${formatNumber(lastTransactionRate)}</strong></td>
                        <td class="amount ${if (cumulativeNet >= 0) "positive" else "negative"}"><strong>${formatNumber(cumulativeNet)}</strong></td>
                    </tr>
                """)
                }
            }

            append("""
                        </table>
                    </div>
                    
                    <div style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d; font-size: 12px;">
                       
                        
                        <p>Generated time • ${dateTimeFormat.format(Date())}</p>
                    </div>
                </div>
            </body>
            </html>
        """)
        }

        outputStream.write(htmlContent.toByteArray(Charsets.UTF_8))
        outputStream.close()
    }

    private fun generateExcelReport(
        outputStream: java.io.OutputStream,
        partnerName: String,
        transactions: List<com.example.currencyexchange.data.entities.TransactionEntity>,
        startDate: Date,
        endDate: Date,
        summary: PartnerSummary
    ) {
        // Generate CSV format that Excel can open with proper formatting
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()) // Changed to avoid commas
        val dateTimeFormat = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault()) // Changed to avoid commas

        val csvContent = buildString {
            // Helper function to format numbers with commas
            fun formatNumber(number: Double): String {
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
            // Header information
            appendLine("Currency Exchange Report")
            appendLine("Partner,$partnerName")
            appendLine("Date Range,${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}")
            appendLine("Generated on,${dateTimeFormat.format(Date())}")
            appendLine() // Empty line

            // Enhanced Summary section with formulas
            appendLine("SUMMARY")
            appendLine("Currency,Net Position Formula,Status")

            // Find the last exchange rates for CNY and USDT
            val lastCnyRate = transactions.filter { it.foreignCurrency == "CNY" }.firstOrNull()?.exchangeRate ?: 376.0
            val lastUsdtRate = transactions.filter { it.foreignCurrency == "USDT" }.firstOrNull()?.exchangeRate ?: 2380.0

            // Calculate the starting row for transaction data (header info + summary + headers = approximately row 11)
            val dataStartRow = 15
            val dataEndRow = dataStartRow + transactions.size - 1

            appendLine("TZS,\"=SUMIF(D$dataStartRow:D$dataEndRow,\"\"CNY\"\",B$dataStartRow:B$dataEndRow)+SUMIF(D$dataStartRow:D$dataEndRow,\"\"USD\"\",B$dataStartRow:B$dataEndRow)-(SUMIF(D$dataStartRow:D$dataEndRow,\"\"CNY\"\",F$dataStartRow:F$dataEndRow)+SUMIF(D$dataStartRow:D$dataEndRow,\"\"USD\"\",F$dataStartRow:F$dataEndRow))\",${if (summary.totalNetTzs >= 0) "Credit" else "Debit"}")
            appendLine("CNY,\"=(SUMIF(D$dataStartRow:D$dataEndRow,\"\"CNY\"\",B$dataStartRow:B$dataEndRow)-SUMIF(D$dataStartRow:D$dataEndRow,\"\"CNY\"\",F$dataStartRow:F$dataEndRow))/$lastCnyRate\",${if (summary.totalNetCny >= 0) "Credit" else "Debit"}")
            appendLine("USD,\"=(SUMIF(D$dataStartRow:D$dataEndRow,\"\"USD\"\",B$dataStartRow:B$dataEndRow)-SUMIF(D$dataStartRow:D$dataEndRow,\"\"USD\"\",F$dataStartRow:F$dataEndRow))/$lastUsdtRate\",${if (summary.totalNetUsdt >= 0) "Credit" else "Debit"}")
            appendLine("Total Transactions,${transactions.size},Records")
            appendLine() // Empty line

            // Transaction details header (6 columns only)
            appendLine("TRANSACTION DETAILS")
            appendLine("Date,TZS Received,Foreign Amount,Currency,Exchange Rate,Net TZS")

            // Transaction data (continuous, no daily/cumulative summaries)
            if (transactions.isEmpty()) {
                appendLine("No transactions found in selected date range,,,,,")
            } else {
                transactions.sortedBy { it.date }.forEach { transaction ->
                    // Calculate Net TZS as (Foreign Amount × Exchange Rate)
                    val netTzs = transaction.foreignGiven * transaction.exchangeRate
                    appendLine("${dateFormat.format(transaction.date)},${transaction.tzsReceived},${transaction.foreignGiven},${transaction.foreignCurrency},${transaction.exchangeRate},$netTzs")
                }
            }

            appendLine() // Empty line

            // Enhanced formulas section


            appendLine("Transaction Count,\"=COUNTA(A$dataStartRow:A$dataEndRow)\",Count of transaction rows")



            appendLine()



        }

        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
        outputStream.close()
    }

    private fun formatNumber(number: Double): String {
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

    // Backup and Restore Functions
    suspend fun createBackup(): BackupData {
        return backupRepository.createBackup()
    }

    fun exportBackupToFile(backupData: BackupData, outputStream: FileOutputStream): Boolean {
        return backupRepository.exportBackupToFile(backupData, outputStream)
    }

    fun parseBackupFromFile(inputStream: InputStream): BackupData? {
        return backupRepository.parseBackupFromFile(inputStream)
    }

    suspend fun restoreBackup(backupData: BackupData): RestoreResult {
        return backupRepository.restoreBackup(backupData)
    }

    fun generateBackupFileName(): String {
        return backupRepository.generateBackupFileName()
    }

    suspend fun updateDefaultExchangeRate(currency: String, rate: Double) {
        try {
            exchangeRateRepository.setDefaultRate(currency, rate)
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
        }
    }
}