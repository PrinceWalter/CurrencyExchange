package com.example.currencyexchange.data.dao

import androidx.room.*
import com.example.currencyexchange.data.entities.ExchangeRateEntity

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates WHERE currency = :currency ORDER BY date DESC LIMIT 1")
    suspend fun getLatestRateForCurrency(currency: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE isDefault = 1")
    suspend fun getDefaultRates(): List<ExchangeRateEntity>

    @Query("SELECT * FROM exchange_rates WHERE currency = :currency AND isDefault = 1")
    suspend fun getDefaultRateForCurrency(currency: String): ExchangeRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ExchangeRateEntity): Long

    @Query("UPDATE exchange_rates SET isDefault = 0 WHERE currency = :currency")
    suspend fun clearDefaultForCurrency(currency: String)

    @Transaction
    suspend fun setDefaultRate(rate: ExchangeRateEntity) {
        clearDefaultForCurrency(rate.currency)
        insertRate(rate.copy(isDefault = true))
    }

    @Query("DELETE FROM exchange_rates WHERE id = :rateId")
    suspend fun deleteRate(rateId: Long)
}