package com.example.currencyexchange.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.currencyexchange.data.entities.PartnerEntity

@Dao
interface PartnerDao {
    @Query("""
        SELECT p.* FROM partners p
        LEFT JOIN (
            SELECT partnerId, SUM(netTzs) as totalNetTzs
            FROM transactions 
            GROUP BY partnerId
        ) t ON p.id = t.partnerId
        WHERE p.isActive = 1 
        ORDER BY ABS(COALESCE(t.totalNetTzs, 0)) DESC, p.name ASC
    """)
    fun getAllActivePartners(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE id = :partnerId")
    suspend fun getPartnerById(partnerId: Long): PartnerEntity?

    @Query("SELECT * FROM partners WHERE name LIKE '%' || :searchQuery || '%' AND isActive = 1")
    fun searchPartners(searchQuery: String): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND isActive = 1")
    suspend fun getPartnerByName(name: String): PartnerEntity?

    @Query("SELECT * FROM partners WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND isActive = 1 AND id != :excludeId")
    suspend fun getPartnerByNameExcluding(name: String, excludeId: Long): PartnerEntity?

    @Query("SELECT COUNT(*) FROM partners WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND isActive = 1")
    suspend fun countPartnersByName(name: String): Int

    @Query("SELECT COUNT(*) FROM partners WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND isActive = 1 AND id != :excludeId")
    suspend fun countPartnersByNameExcluding(name: String, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Query("UPDATE partners SET isActive = 0 WHERE id = :partnerId")
    suspend fun deletePartner(partnerId: Long)

    @Query("SELECT COUNT(*) FROM partners WHERE isActive = 1")
    suspend fun getActivePartnerCount(): Int
}