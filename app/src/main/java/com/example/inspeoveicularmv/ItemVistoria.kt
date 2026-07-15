package com.example.inspecaoveicularmv

data class ItemVistoria(
    val nome: String,
    var fotoPath: String? = null,
    var status: String = "Aguardando", // "OK", "IRREGULAR", "NAO_SE_APLICA"
    var observacao: String = "",
    var valorExtra: String = "" // Para Validade, KM, etc.
)