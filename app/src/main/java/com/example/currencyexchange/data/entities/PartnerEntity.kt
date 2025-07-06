package com.example.currencyexchange.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Date = Date(),
    val isActive: Boolean = true,
    val notes: String = ""
)