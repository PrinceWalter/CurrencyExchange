package com.example.currencyexchange.data.repository

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.currencyexchange.data.backup.*
import com.example.currencyexchange.data.entities.*

@Singleton
class BackupRepository @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackup(): BackupData {
        val partners = partnerRepository.getAllActivePartners().first()
        val allTransactions = mutableListOf<TransactionEntity>()

        // Collect all transactions from all partners
        partners.forEach { partner ->
            val partnerTransactions = transactionRepository.getTransactionsByPartner(partner.id).first()
            allTransactions.addAll(partnerTransactions)
        }

        val exchangeRates = exchangeRateRepository.getAllDefaultRates()

        // Convert to backup format
        val backupPartners = partners.map { partner ->
            BackupPartner(
                name = partner.name,
                createdAt = partner.createdAt.time,
                isActive = partner.isActive,
                notes = partner.notes
            )
        }

        val backupTransactions = allTransactions.map { transaction ->
            val partner = partners.find { it.id == transaction.partnerId }
            BackupTransaction(
                partnerName = partner?.name ?: "Unknown",
                date = transaction.date.time,
                tzsReceived = transaction.tzsReceived,
                foreignGiven = transaction.foreignGiven,
                foreignCurrency = transaction.foreignCurrency,
                exchangeRate = transaction.exchangeRate,
                netTzs = transaction.netTzs,
                netForeign = transaction.netForeign,
                notes = transaction.notes,
                createdAt = transaction.createdAt.time,
                lastModified = transaction.lastModified.time
            )
        }

        val backupExchangeRates = exchangeRates.map { (currency, rate) ->
            BackupExchangeRate(
                currency = currency,
                rate = rate,
                date = System.currentTimeMillis(),
                isDefault = true,
                source = "DEFAULT"
            )
        }

        val metadata = BackupMetadata(
            version = "1.0",
            exportDate = System.currentTimeMillis(),
            appVersion = "1.0",
            totalPartners = backupPartners.size,
            totalTransactions = backupTransactions.size,
            totalExchangeRates = backupExchangeRates.size,
            deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        )

        return BackupData(
            metadata = metadata,
            partners = backupPartners,
            transactions = backupTransactions,
            exchangeRates = backupExchangeRates
        )
    }

    fun exportBackupToFile(backupData: BackupData, outputStream: FileOutputStream): Boolean {
        return try {
            val jsonString = json.encodeToString(backupData)
            outputStream.write(jsonString.toByteArray())
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun parseBackupFromFile(inputStream: InputStream): BackupData? {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<BackupData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun restoreBackup(backupData: BackupData): RestoreResult {
        val errors = mutableListOf<String>()
        var partnersAdded = 0
        var partnersUpdated = 0
        var transactionsAdded = 0
        var transactionsSkipped = 0
        var exchangeRatesAdded = 0
        var exchangeRatesUpdated = 0

        try {
            // Get existing data
            val existingPartners = partnerRepository.getAllActivePartners().first()
            val partnerNameToIdMap = mutableMapOf<String, Long>()

            // 1. Restore Partners (merge by name)
            for (backupPartner in backupData.partners) {
                try {
                    val existingPartner = existingPartners.find {
                        it.name.trim().equals(backupPartner.name.trim(), ignoreCase = true)
                    }

                    if (existingPartner != null) {
                        // Partner exists - update if backup data is newer
                        val backupDate = Date(backupPartner.createdAt)
                        if (backupDate.after(existingPartner.createdAt)) {
                            val updatedPartner = existingPartner.copy(
                                notes = if (backupPartner.notes.isNotBlank()) backupPartner.notes else existingPartner.notes,
                                isActive = backupPartner.isActive
                            )
                            partnerRepository.updatePartner(updatedPartner)
                            partnersUpdated++
                        }
                        partnerNameToIdMap[backupPartner.name] = existingPartner.id
                    } else {
                        // New partner - add it
                        val partnerId = partnerRepository.addPartner(
                            name = backupPartner.name,
                            notes = backupPartner.notes
                        )
                        partnerNameToIdMap[backupPartner.name] = partnerId
                        partnersAdded++
                    }
                } catch (e: Exception) {
                    errors.add("Failed to restore partner '${backupPartner.name}': ${e.message}")
                }
            }

            // 2. Restore Transactions (avoid duplicates)
            for (backupTransaction in backupData.transactions) {
                try {
                    val partnerId = partnerNameToIdMap[backupTransaction.partnerName]
                    if (partnerId == null) {
                        errors.add("Partner '${backupTransaction.partnerName}' not found for transaction")
                        continue
                    }

                    // Check for duplicate transactions
                    val existingTransactions = transactionRepository.getTransactionsByPartner(partnerId).first()
                    val isDuplicate = existingTransactions.any { existing ->
                        existing.date.time == backupTransaction.date &&
                                existing.tzsReceived == backupTransaction.tzsReceived &&
                                existing.foreignGiven == backupTransaction.foreignGiven &&
                                existing.foreignCurrency == backupTransaction.foreignCurrency &&
                                existing.exchangeRate == backupTransaction.exchangeRate
                    }

                    if (!isDuplicate) {
                        transactionRepository.addTransaction(
                            partnerId = partnerId,
                            date = Date(backupTransaction.date),
                            tzsReceived = backupTransaction.tzsReceived,
                            foreignGiven = backupTransaction.foreignGiven,
                            foreignCurrency = backupTransaction.foreignCurrency,
                            exchangeRate = backupTransaction.exchangeRate,
                            notes = backupTransaction.notes
                        )
                        transactionsAdded++
                    } else {
                        transactionsSkipped++
                    }
                } catch (e: Exception) {
                    errors.add("Failed to restore transaction: ${e.message}")
                }
            }

            // 3. Restore Exchange Rates (keep most recent)
            for (backupRate in backupData.exchangeRates) {
                try {
                    val currentRate = exchangeRateRepository.getDefaultRate(backupRate.currency)

                    if (currentRate == 0.0 || backupRate.isDefault) {
                        // No existing rate or backup has default rate - update
                        exchangeRateRepository.setDefaultRate(backupRate.currency, backupRate.rate)
                        if (currentRate == 0.0) {
                            exchangeRatesAdded++
                        } else {
                            exchangeRatesUpdated++
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Failed to restore exchange rate for ${backupRate.currency}: ${e.message}")
                }
            }

            return RestoreResult(
                success = true,
                message = "Backup restored successfully! Added $partnersAdded partners, $transactionsAdded transactions.",
                partnersAdded = partnersAdded,
                partnersUpdated = partnersUpdated,
                transactionsAdded = transactionsAdded,
                transactionsSkipped = transactionsSkipped,
                exchangeRatesAdded = exchangeRatesAdded,
                exchangeRatesUpdated = exchangeRatesUpdated,
                errors = errors
            )

        } catch (e: Exception) {
            return RestoreResult(
                success = false,
                message = "Failed to restore backup: ${e.message}",
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    fun generateBackupFileName(): String {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(Date())
        return "CurrencyExchange_Backup_$timestamp.json"
    }
}