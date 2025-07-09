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
    ) = transactionRepository.getPartnerSummaryByDateRange(partnerId, startDate, endDate)

    suspend fun getTransactionsByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ) = transactionRepository.getTransactionsByDateRange(partnerId, startDate, endDate)

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
                            color: #333;
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
                        .amount { text-align: right; font-family: 'Courier New', monospace; }
                        .positive { color: #28a745; font-weight: bold; }
                        .negative { color: #dc3545; font-weight: bold; }
                        .total-row { 
                            background-color: #e3f2fd; 
                            font-weight: bold; 
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
                        .notes-cell {
                            max-width: 200px;
                            word-wrap: break-word;
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
                            <h2>Customer: $partnerName</h2>
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
                                    <td class="currency-cell">USD</td>
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
                                    <th>CNY Given</th>
                                   
                                    <th>Exchange Rate</th>
                                    <th>Net TZS</th>
                                    <th>Net Foreign</th>
                                    
                                </tr>
            """)

            if (transactions.isEmpty()) {
                append("""
                    <tr>
                        <td colspan="8" style="text-align: center; padding: 30px; color: #6c757d; font-style: italic;">
                            No transactions found in the selected date range
                        </td>
                    </tr>
                """)
            } else {
                transactions.sortedBy { it.date }.forEach { transaction ->
                    append("""
                        <tr>
                            <td class="date-cell">${dateFormat.format(transaction.date)}</td>
                            <td class="amount">${formatNumber(transaction.tzsReceived)}</td>
                            <td class="amount">${formatNumber(transaction.foreignGiven)}</td>
                            
                            <td class="amount">${formatNumber(transaction.exchangeRate)}</td>
                            <td class="amount ${if (transaction.netTzs >= 0) "positive" else "negative"}">${formatNumber(transaction.netTzs)}</td>
                            <td class="amount ${if (transaction.netForeign >= 0) "positive" else "negative"}">${formatNumber(transaction.netForeign)}</td>
                            
                        </tr>
                    """)
                }
            }

            append("""
                            </table>
                        </div>
                        
                        <div style="margin-top: 40px; padding-top: 20px; border-top: 1px solid #dee2e6; text-align: center; color: #6c757d; font-size: 12px;">
                            <p>📄 To convert to PDF: Use your browser's Print function and select "Save as PDF"</p>
                            <p>Generated by Currency Exchange App • ${dateTimeFormat.format(Date())}</p>
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
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        val csvContent = buildString {
            // Header information
            appendLine("Currency Exchange Report")
            appendLine("Partner,$partnerName")
            appendLine("Date Range,${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}")
            appendLine("Generated on,${dateTimeFormat.format(Date())}")
            appendLine() // Empty line

            // Summary section
            appendLine("SUMMARY")
            appendLine("Currency,Net Position,Status")
            appendLine("TZS,${summary.totalNetTzs},${if (summary.totalNetTzs >= 0) "Credit" else "Debit"}")
            appendLine("CNY,${summary.totalNetCny},${if (summary.totalNetCny >= 0) "Credit" else "Debit"}")
            appendLine("USDT,${summary.totalNetUsdt},${if (summary.totalNetUsdt >= 0) "Credit" else "Debit"}")
            appendLine("Total Transactions,${transactions.size},Records")
            appendLine() // Empty line

            // Transaction details header
            appendLine("TRANSACTION DETAILS")
            appendLine("Date,TZS Received,Foreign Given,Currency,Exchange Rate,Net TZS,Net Foreign,Notes")

            // Transaction data
            if (transactions.isEmpty()) {
                appendLine("No transactions found in selected date range,,,,,,")
            } else {
                transactions.sortedBy { it.date }.forEach { transaction ->
                    val notes = transaction.notes.replace("\"", "\"\"") // Escape quotes for CSV
                    appendLine("${dateFormat.format(transaction.date)},${transaction.tzsReceived},${transaction.foreignGiven},${transaction.foreignCurrency},${transaction.exchangeRate},${transaction.netTzs},${transaction.netForeign},\"$notes\"")
                }
            }

            appendLine() // Empty line

            // Formulas section with explanations
            appendLine("EXCEL FORMULAS (Copy these to Excel cells for automatic calculations)")
            appendLine("Description,Formula,Explanation")
            appendLine("Total TZS Received,\"=SUMIF(D:D,\"\"CNY\"\",B:B)+SUMIF(D:D,\"\"USDT\"\",B:B)\",Sum TZS received from all currencies")
            appendLine("Total CNY Given,\"=SUMIF(D:D,\"\"CNY\"\",C:C)\",Sum all CNY given")
            appendLine("Total USDT Given,\"=SUMIF(D:D,\"\"USDT\"\",C:C)\",Sum all USDT given")
            appendLine("Net TZS Position,\"=SUM(F:F)\",Sum of all net TZS amounts")
            appendLine("Net CNY Position,\"=SUMIF(D:D,\"\"CNY\"\",G:G)\",Sum net foreign amounts for CNY")
            appendLine("Net USDT Position,\"=SUMIF(D:D,\"\"USDT\"\",G:G)\",Sum net foreign amounts for USDT")
            appendLine("Average Exchange Rate CNY,\"=AVERAGEIF(D:D,\"\"CNY\"\",E:E)\",Average CNY exchange rate")
            appendLine("Average Exchange Rate USDT,\"=AVERAGEIF(D:D,\"\"USDT\"\",E:E)\",Average USDT exchange rate")
            appendLine("Transaction Count,\"=COUNTA(A:A)-9\",Count of transaction rows (minus headers)")

            appendLine()
            appendLine("QUICK ANALYSIS")
            appendLine("Best CNY Rate,\"=MAX(IF(D:D=\"\"CNY\"\",E:E))\",Highest CNY rate received")
            appendLine("Worst CNY Rate,\"=MIN(IF(D:D=\"\"CNY\"\",E:E))\",Lowest CNY rate received")
            appendLine("Best USDT Rate,\"=MAX(IF(D:D=\"\"USDT\"\",E:E))\",Highest USDT rate received")
            appendLine("Worst USDT Rate,\"=MIN(IF(D:D=\"\"USDT\"\",E:E))\",Lowest USDT rate received")
            appendLine("Largest Transaction,\"=MAX(B:B)\",Largest TZS amount received")
            appendLine("Smallest Transaction,\"=MIN(IF(B:B>0,B:B))\",Smallest TZS amount received")

            appendLine()
            appendLine("NOTES:")
            appendLine("1. This CSV file opens perfectly in Microsoft Excel")
            appendLine("2. Copy the formulas above into Excel cells for automatic calculations")
            appendLine("3. Use array formulas (Ctrl+Shift+Enter) for complex formulas if needed")
            appendLine("4. Data rows start from row 9 in the Transaction Details section")
            appendLine("5. Positive values = Credit (money owed to you)")
            appendLine("6. Negative values = Debit (money you owe)")
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
}