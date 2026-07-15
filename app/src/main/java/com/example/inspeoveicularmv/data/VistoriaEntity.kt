package com.example.inspecaoveicularmv.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.inspecaoveicularmv.ItemVistoria
import com.example.inspecaoveicularmv.ItemVistoriaMultipla

@Entity(tableName = "vistorias")
data class VistoriaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val veiculo: String,
    val placa: String,
    val km: String,
    val data: String,
    val hora: String,
    val funcionario: String,
    val itensSimples: List<ItemVistoria>,
    val itensMultiplos: List<ItemVistoriaMultipla>,
    val itensSemFoto: List<ItemVistoria>,
    val itensSemFotoNA: List<ItemVistoria>,
    val itensComExtra: List<ItemVistoria>,
    val nivelCombustivel: String,
    val observacoesFinais: String,
    val timestamp: Long = System.currentTimeMillis()
)
