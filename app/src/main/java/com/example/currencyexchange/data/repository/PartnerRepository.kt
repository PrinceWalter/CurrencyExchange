package com.example.currencyexchange.data.repository

import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.example.currencyexchange.data.dao.*
import com.example.currencyexchange.data.entities.*

@Singleton
class PartnerRepository @Inject constructor(
    private val partnerDao: PartnerDao,
    private val transactionDao: TransactionDao
) {
    fun getAllActivePartners(): Flow<List<PartnerEntity>> = partnerDao.getAllActivePartners()

    fun searchPartners(query: String): Flow<List<PartnerEntity>> = partnerDao.searchPartners(query)

    suspend fun getPartnerById(partnerId: Long): PartnerEntity? = partnerDao.getPartnerById(partnerId)

    /**
     * Check if a partner name already exists (case-insensitive)
     */
    suspend fun isPartnerNameExists(name: String, excludeId: Long = -1): Boolean {
        return if (excludeId == -1L) {
            partnerDao.countPartnersByName(name.trim()) > 0
        } else {
            partnerDao.countPartnersByNameExcluding(name.trim(), excludeId) > 0
        }
    }

    /**
     * Add a new partner with duplicate name checking
     */
    suspend fun addPartner(name: String, notes: String = ""): Long {
        val trimmedName = name.trim()

        // Validate name
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Partner name cannot be empty")
        }

        if (trimmedName.length < 2) {
            throw IllegalArgumentException("Partner name must be at least 2 characters")
        }

        if (trimmedName.length > 50) {
            throw IllegalArgumentException("Partner name cannot exceed 50 characters")
        }

        // Check for duplicates
        if (isPartnerNameExists(trimmedName)) {
            throw IllegalArgumentException("A partner with this name already exists")
        }

        val partner = PartnerEntity(
            name = trimmedName,
            notes = notes.trim(),
            createdAt = Date()
        )
        return partnerDao.insertPartner(partner)
    }

    /**
     * Update an existing partner with duplicate name checking
     */
    suspend fun updatePartner(partner: PartnerEntity) {
        val trimmedName = partner.name.trim()

        // Validate name
        if (trimmedName.isBlank()) {
            throw IllegalArgumentException("Partner name cannot be empty")
        }

        if (trimmedName.length < 2) {
            throw IllegalArgumentException("Partner name must be at least 2 characters")
        }

        if (trimmedName.length > 50) {
            throw IllegalArgumentException("Partner name cannot exceed 50 characters")
        }

        // Check for duplicates (excluding current partner)
        if (isPartnerNameExists(trimmedName, partner.id)) {
            throw IllegalArgumentException("A partner with this name already exists")
        }

        val updatedPartner = partner.copy(name = trimmedName)
        partnerDao.updatePartner(updatedPartner)
    }

    /**
     * Update partner name specifically with validation
     */
    suspend fun updatePartnerName(partnerId: Long, newName: String) {
        val partner = getPartnerById(partnerId)
            ?: throw IllegalArgumentException("Partner not found")

        val updatedPartner = partner.copy(name = newName.trim())
        updatePartner(updatedPartner)
    }

    suspend fun deletePartner(partnerId: Long) {
        partnerDao.deletePartner(partnerId)
    }

    suspend fun getPartnerCount(): Int = partnerDao.getActivePartnerCount()

    suspend fun getPartnerSummary(partnerId: Long): PartnerSummary {
        return transactionDao.getPartnerSummary(partnerId)
    }
}