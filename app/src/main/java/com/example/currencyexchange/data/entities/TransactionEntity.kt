package com.example.currencyexchange.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PartnerEntity::class,
            parentColumns = ["id"],
            childColumns = ["partnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partnerId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val partnerId: Long,
    val date: Date,
    val tzsReceived: Double,
    val foreignGiven: Double,
    val foreignCurrency: String, // "CNY" or "USDT"
    val exchangeRate: Double,
    val netTzs: Double,
    val netForeign: Double,
    val notes: String = "",
    val createdAt: Date = Date(),
    val lastModified: Date = Date()
)