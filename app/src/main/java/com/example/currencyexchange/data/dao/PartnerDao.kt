package com.example.currencyexchange.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.currencyexchange.data.entities.PartnerEntity

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActivePartners(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE id = :partnerId")
    suspend fun getPartnerById(partnerId: Long): PartnerEntity?

    @Query("SELECT * FROM partners WHERE name LIKE '%' || :searchQuery || '%' AND isActive = 1")
    fun searchPartners(searchQuery: String): Flow<List<PartnerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Query("UPDATE partners SET isActive = 0 WHERE id = :partnerId")
    suspend fun deletePartner(partnerId: Long)

    @Query("SELECT COUNT(*) FROM partners WHERE isActive = 1")
    suspend fun getActivePartnerCount(): Int
}