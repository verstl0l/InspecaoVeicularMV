package com.example.inspecaoveicularmv

data class ItemVistoriaMultipla(
    val nome: String,
    val subItens: List<String>,
    var fotoPaths: MutableList<String?> = mutableListOf(),
    var status: String = "Aguardando",
    var observacao: String = ""
) {
    // Status por sub-item
    var statusSubItens: MutableList<String> = mutableListOf()

    init {
        // Inicializar status e fotos para cada sub-item
        for (i in subItens.indices) {
            statusSubItens.add("Aguardando")
            fotoPaths.add(null)
        }
    }

    fun getStatusGeral(): String {
        return if (statusSubItens.all { it == "OK" }) {
            "OK"
        } else if (statusSubItens.any { it == "IRREGULAR" }) {
            "IRREGULAR"
        } else {
            "Aguardando"
        }
    }
}