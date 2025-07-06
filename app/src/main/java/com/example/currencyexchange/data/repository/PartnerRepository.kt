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

    suspend fun addPartner(name: String, notes: String = ""): Long {
        val partner = PartnerEntity(
            name = name.trim(),
            notes = notes.trim(),
            createdAt = Date()
        )
        return partnerDao.insertPartner(partner)
    }

    suspend fun updatePartner(partner: PartnerEntity) = partnerDao.updatePartner(partner)

    suspend fun deletePartner(partnerId: Long) {
        partnerDao.deletePartner(partnerId)
    }

    suspend fun getPartnerCount(): Int = partnerDao.getActivePartnerCount()

    suspend fun getPartnerSummary(partnerId: Long): PartnerSummary {
        return transactionDao.getPartnerSummary(partnerId)
    }
}