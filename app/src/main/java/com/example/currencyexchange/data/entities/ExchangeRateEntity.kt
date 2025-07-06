package com.example.currencyexchange.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val currency: String, // "CNY" or "USDT"
    val rate: Double, // Rate to TZS
    val date: Date,
    val isDefault: Boolean = false,
    val source: String = "USER_INPUT"
)