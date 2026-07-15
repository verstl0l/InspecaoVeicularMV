package com.example.inspecaoveicularmv.data

import androidx.room.*

@Dao
interface VistoriaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vistoria: VistoriaEntity)

    @Update
    suspend fun update(vistoria: VistoriaEntity)

    @Delete
    suspend fun delete(vistoria: VistoriaEntity)

    @Query("SELECT * FROM vistorias ORDER BY timestamp DESC")
    suspend fun getAllVistorias(): List<VistoriaEntity>

    @Query("SELECT * FROM vistorias WHERE id = :id LIMIT 1")
    suspend fun getVistoriaById(id: Int): VistoriaEntity?

    @Query("SELECT * FROM vistorias WHERE placa = :placa LIMIT 1")
    suspend fun getVistoriaByPlaca(placa: String): VistoriaEntity?
}
