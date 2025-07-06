package com.example.currencyexchange.data.repository

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.currencyexchange.data.dao.ExchangeRateDao
import com.example.currencyexchange.data.entities.ExchangeRateEntity

@Singleton
class ExchangeRateRepository @Inject constructor(
    private val exchangeRateDao: ExchangeRateDao
) {
    suspend fun getDefaultRate(currency: String): Double {
        return exchangeRateDao.getDefaultRateForCurrency(currency)?.rate ?: when (currency) {
            "CNY" -> 376.0
            "USDT" -> 2380.0
            else -> 1.0
        }
    }

    suspend fun getLatestRate(currency: String): Double {
        return exchangeRateDao.getLatestRateForCurrency(currency)?.rate ?: getDefaultRate(currency)
    }

    suspend fun setDefaultRate(currency: String, rate: Double) {
        val rateEntity = ExchangeRateEntity(
            currency = currency,
            rate = rate,
            date = Date(),
            isDefault = true,
            source = "USER_INPUT"
        )
        exchangeRateDao.setDefaultRate(rateEntity)
    }

    suspend fun getAllDefaultRates(): Map<String, Double> {
        return exchangeRateDao.getDefaultRates().associate { it.currency to it.rate }
    }
}