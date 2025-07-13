package com.example.currencyexchange.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PersistentBalanceItem(
    val id: String,
    val description: String,
    val amount: String,
    val currency: String,
    val rate: String,
    val isNetPosition: Boolean = false,
    val isFixedType: Boolean = false
)

@Serializable
data class BalancesState(
    val balanceItems: List<PersistentBalanceItem>,
    val defaultCnyRate: String,
    val defaultUsdtRate: String
)

@Singleton
class BalancesRepository @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "balances_prefs",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val KEY_BALANCES_STATE = "balances_state"
        private const val KEY_DEFAULT_CNY_RATE = "default_cny_rate"
        private const val KEY_DEFAULT_USDT_RATE = "default_usdt_rate"
    }

    suspend fun saveBalancesState(balancesState: BalancesState) = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(balancesState)
            prefs.edit()
                .putString(KEY_BALANCES_STATE, jsonString)
                .apply()
        } catch (e: Exception) {
            // Handle serialization error
            e.printStackTrace()
        }
    }

    suspend fun loadBalancesState(): BalancesState? = withContext(Dispatchers.IO) {
        try {
            val jsonString = prefs.getString(KEY_BALANCES_STATE, null)
            if (jsonString != null) {
                json.decodeFromString<BalancesState>(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            // Handle deserialization error or return null for fresh start
            e.printStackTrace()
            null
        }
    }

    suspend fun saveDefaultRates(cnyRate: String, usdtRate: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_DEFAULT_CNY_RATE, cnyRate)
            .putString(KEY_DEFAULT_USDT_RATE, usdtRate)
            .apply()
    }

    suspend fun loadDefaultRates(): Pair<String, String> = withContext(Dispatchers.IO) {
        val cnyRate = prefs.getString(KEY_DEFAULT_CNY_RATE, "376") ?: "376"
        val usdtRate = prefs.getString(KEY_DEFAULT_USDT_RATE, "2380") ?: "2380"
        Pair(cnyRate, usdtRate)
    }

    suspend fun clearBalancesState() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_BALANCES_STATE)
            .apply()
    }
}