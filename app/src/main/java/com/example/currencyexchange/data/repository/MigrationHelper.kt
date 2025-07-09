package com.example.currencyexchange.data.repository

import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationHelper @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val partnerRepository: PartnerRepository
) {

    /**
     * Recalculates net positions for all existing CNY transactions
     * Call this function once after updating the app to fix existing data
     */
    suspend fun migrateCnyTransactionCalculations(): MigrationResult {
        var totalTransactions = 0
        var updatedTransactions = 0
        var errors = mutableListOf<String>()

        try {
            val partners = partnerRepository.getAllActivePartners().first()

            partners.forEach { partner ->
                try {
                    val transactions = transactionRepository.getTransactionsByPartner(partner.id).first()

                    transactions.forEach { transaction ->
                        totalTransactions++

                        // Only update CNY transactions
                        if (transaction.foreignCurrency == "CNY") {
                            try {
                                // Recalculate with new CNY logic
                                val newNetTzs = (transaction.foreignGiven * transaction.exchangeRate) - transaction.tzsReceived
                                val newNetForeign = if (transaction.exchangeRate > 0) newNetTzs / transaction.exchangeRate else 0.0

                                // Only update if the calculation has changed
                                if (transaction.netTzs != newNetTzs || transaction.netForeign != newNetForeign) {
                                    val updatedTransaction = transaction.copy(
                                        netTzs = newNetTzs,
                                        netForeign = newNetForeign,
                                        lastModified = Date()
                                    )

                                    transactionRepository.updateTransaction(updatedTransaction)
                                    updatedTransactions++
                                }
                            } catch (e: Exception) {
                                errors.add("Failed to update transaction ${transaction.id}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors.add("Failed to process transactions for partner ${partner.name}: ${e.message}")
                }
            }

            return MigrationResult(
                success = true,
                message = "Migration completed successfully",
                totalTransactions = totalTransactions,
                updatedTransactions = updatedTransactions,
                errors = errors
            )

        } catch (e: Exception) {
            return MigrationResult(
                success = false,
                message = "Migration failed: ${e.message}",
                totalTransactions = totalTransactions,
                updatedTransactions = updatedTransactions,
                errors = errors + listOf(e.message ?: "Unknown error")
            )
        }
    }
}

data class MigrationResult(
    val success: Boolean,
    val message: String,
    val totalTransactions: Int = 0,
    val updatedTransactions: Int = 0,
    val errors: List<String> = emptyList()
)