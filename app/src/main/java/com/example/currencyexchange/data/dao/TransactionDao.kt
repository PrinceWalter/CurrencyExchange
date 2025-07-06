package com.example.currencyexchange.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date
import com.example.currencyexchange.data.entities.TransactionEntity

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE partnerId = :partnerId ORDER BY date DESC")
    fun getTransactionsByPartner(partnerId: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE partnerId = :partnerId 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC
    """)
    suspend fun getTransactionsByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE partnerId = :partnerId")
    suspend fun deleteAllTransactionsForPartner(partnerId: Long)

    // Summary queries
    @Query("""
        SELECT 
            SUM(netTzs) as totalNetTzs,
            SUM(CASE WHEN foreignCurrency = 'CNY' THEN netForeign ELSE 0 END) as totalNetCny,
            SUM(CASE WHEN foreignCurrency = 'USDT' THEN netForeign ELSE 0 END) as totalNetUsdt,
            COUNT(*) as transactionCount
        FROM transactions 
        WHERE partnerId = :partnerId
    """)
    suspend fun getPartnerSummary(partnerId: Long): PartnerSummary

    @Query("""
        SELECT 
            SUM(netTzs) as totalNetTzs,
            SUM(CASE WHEN foreignCurrency = 'CNY' THEN netForeign ELSE 0 END) as totalNetCny,
            SUM(CASE WHEN foreignCurrency = 'USDT' THEN netForeign ELSE 0 END) as totalNetUsdt,
            COUNT(*) as transactionCount
        FROM transactions 
        WHERE partnerId = :partnerId 
        AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun getPartnerSummaryByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ): PartnerSummary
}

// Data class for summary queries
data class PartnerSummary(
    val totalNetTzs: Double,
    val totalNetCny: Double,
    val totalNetUsdt: Double,
    val transactionCount: Int
)