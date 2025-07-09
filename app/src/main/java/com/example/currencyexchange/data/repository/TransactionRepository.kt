package com.example.currencyexchange.data.repository

import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.currencyexchange.data.dao.*
import com.example.currencyexchange.data.entities.*

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getTransactionsByPartner(partnerId: Long): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByPartner(partnerId)
    }

    suspend fun getTransactionsByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ): List<TransactionEntity> {
        return transactionDao.getTransactionsByDateRange(partnerId, startDate, endDate)
    }

    suspend fun addTransaction(
        partnerId: Long,
        date: Date,
        tzsReceived: Double,
        foreignGiven: Double,
        foreignCurrency: String,
        exchangeRate: Double,
        notes: String = ""
    ): Long {
        // Calculate net positions based on currency type
        val netTzs = when (foreignCurrency) {
            "CNY" -> {
                // For CNY: Net = (CNY amount * Rate) - TZS received
                (foreignGiven * exchangeRate) - tzsReceived
            }
            "USDT" -> {
                // For USDT: Net = TZS received - (USDT amount * Rate) [original logic]
                tzsReceived - (foreignGiven * exchangeRate)
            }
            else -> {
                // Default to USDT logic for any other currency
                tzsReceived - (foreignGiven * exchangeRate)
            }
        }

        val netForeign = if (exchangeRate > 0) netTzs / exchangeRate else 0.0

        val transaction = TransactionEntity(
            partnerId = partnerId,
            date = date,
            tzsReceived = tzsReceived,
            foreignGiven = foreignGiven,
            foreignCurrency = foreignCurrency,
            exchangeRate = exchangeRate,
            netTzs = netTzs,
            netForeign = netForeign,
            notes = notes,
            createdAt = Date(),
            lastModified = Date()
        )
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        // Recalculate net positions based on currency type
        val netTzs = when (transaction.foreignCurrency) {
            "CNY" -> {
                // For CNY: Net = (CNY amount * Rate) - TZS received
                (transaction.foreignGiven * transaction.exchangeRate) - transaction.tzsReceived
            }
            "USDT" -> {
                // For USDT: Net = TZS received - (USDT amount * Rate) [original logic]
                transaction.tzsReceived - (transaction.foreignGiven * transaction.exchangeRate)
            }
            else -> {
                // Default to USDT logic for any other currency
                transaction.tzsReceived - (transaction.foreignGiven * transaction.exchangeRate)
            }
        }

        val netForeign = if (transaction.exchangeRate > 0) netTzs / transaction.exchangeRate else 0.0

        val updatedTransaction = transaction.copy(
            lastModified = Date(),
            netTzs = netTzs,
            netForeign = netForeign
        )
        transactionDao.updateTransaction(updatedTransaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) = transactionDao.deleteTransaction(transaction)

    suspend fun getPartnerSummary(partnerId: Long): PartnerSummary {
        return transactionDao.getPartnerSummary(partnerId)
    }

    suspend fun getPartnerSummaryByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ): PartnerSummary {
        return transactionDao.getPartnerSummaryByDateRange(partnerId, startDate, endDate)
    }
}