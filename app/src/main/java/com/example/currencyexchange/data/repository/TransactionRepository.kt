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
        val netTzs = tzsReceived - (foreignGiven * exchangeRate)
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
        val updatedTransaction = transaction.copy(
            lastModified = Date(),
            netTzs = transaction.tzsReceived - (transaction.foreignGiven * transaction.exchangeRate),
            netForeign = if (transaction.exchangeRate > 0)
                (transaction.tzsReceived - (transaction.foreignGiven * transaction.exchangeRate)) / transaction.exchangeRate
            else 0.0
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