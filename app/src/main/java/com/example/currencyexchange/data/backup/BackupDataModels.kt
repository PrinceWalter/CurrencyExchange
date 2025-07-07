package com.example.currencyexchange.data.backup

import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class BackupData(
    val metadata: BackupMetadata,
    val partners: List<BackupPartner>,
    val transactions: List<BackupTransaction>,
    val exchangeRates: List<BackupExchangeRate>
)

@Serializable
data class BackupMetadata(
    val version: String = "1.0",
    val exportDate: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val totalPartners: Int = 0,
    val totalTransactions: Int = 0,
    val totalExchangeRates: Int = 0,
    val deviceInfo: String = ""
)

@Serializable
data class BackupPartner(
    val name: String,
    val createdAt: Long,
    val isActive: Boolean = true,
    val notes: String = ""
)

@Serializable
data class BackupTransaction(
    val partnerName: String, // Use partner name instead of ID for portability
    val date: Long,
    val tzsReceived: Double,
    val foreignGiven: Double,
    val foreignCurrency: String,
    val exchangeRate: Double,
    val netTzs: Double,
    val netForeign: Double,
    val notes: String = "",
    val createdAt: Long,
    val lastModified: Long
)

@Serializable
data class BackupExchangeRate(
    val currency: String,
    val rate: Double,
    val date: Long,
    val isDefault: Boolean = false,
    val source: String = "USER_INPUT"
)

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val partnersAdded: Int = 0,
    val partnersUpdated: Int = 0,
    val transactionsAdded: Int = 0,
    val transactionsSkipped: Int = 0,
    val exchangeRatesAdded: Int = 0,
    val exchangeRatesUpdated: Int = 0,
    val errors: List<String> = emptyList()
)