package com.example.inspecaoveicularmv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.inspecaoveicularmv.data.AppDatabase
import com.example.inspecaoveicularmv.data.VistoriaEntity
import com.example.inspecaoveicularmv.databinding.ActivityHistoricoBinding
import kotlinx.coroutines.launch

class HistoricoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoricoBinding
    private lateinit var adapter: VistoriaAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoricoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getDatabase(this)
        configurarRecyclerView()

        binding.fabNovaVistoria.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("NOVA_VISTORIA", true)
            startActivity(intent)
        }

        carregarVistorias()
    }

    override fun onResume() {
        super.onResume()
        carregarVistorias()
    }

    private fun configurarRecyclerView() {
        adapter = VistoriaAdapter(
            onVistoriaClick = { vistoria ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("VISTORIA_ID", vistoria.id)
                startActivity(intent)
            },
            onDeleteClick = { vistoria ->
                mostrarDialogoDeletar(vistoria)
            }
        )
        binding.rvHistorico.layoutManager = LinearLayoutManager(this)
        binding.rvHistorico.adapter = adapter
    }

    private fun carregarVistorias() {
        lifecycleScope.launch {
            val vistorias = database.vistoriaDao().getAllVistorias()
            adapter.setVistorias(vistorias)
        }
    }

    private fun mostrarDialogoDeletar(vistoria: VistoriaEntity) {
        AlertDialog.Builder(this)
            .setTitle("Deletar Vistoria")
            .setMessage("Tem certeza que deseja excluir a vistoria da placa ${vistoria.placa}?")
            .setPositiveButton("Sim") { _, _ ->
                lifecycleScope.launch {
                    database.vistoriaDao().delete(vistoria)
                    carregarVistorias()
                }
            }
            .setNegativeButton("Não", null)
            .show()
    }
}
