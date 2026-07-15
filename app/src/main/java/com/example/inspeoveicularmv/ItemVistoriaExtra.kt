package com.example.inspecaoveicularmv

data class ItemVistoriaExtra(
    val nome: String,
    var status: String = "Aguardando", // "OK", "IRREGULAR", "NAO_SE_APLICA"
    var valorExtra: String = "", // Para validade, KM, etc.
    var observacao: String = ""
)