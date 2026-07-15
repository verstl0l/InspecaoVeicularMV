package com.example.inspecaoveicularmv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.inspecaoveicularmv.data.VistoriaEntity
import com.example.inspecaoveicularmv.databinding.ItemHistoricoVistoriaBinding

class VistoriaAdapter(
    private val onVistoriaClick: (VistoriaEntity) -> Unit,
    private val onDeleteClick: (VistoriaEntity) -> Unit
) : RecyclerView.Adapter<VistoriaAdapter.VistoriaViewHolder>() {

    private var vistorias = listOf<VistoriaEntity>()

    fun setVistorias(vistorias: List<VistoriaEntity>) {
        this.vistorias = vistorias
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VistoriaViewHolder {
        val binding = ItemHistoricoVistoriaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VistoriaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VistoriaViewHolder, position: Int) {
        holder.bind(vistorias[position])
    }

    override fun getItemCount() = vistorias.size

    inner class VistoriaViewHolder(private val binding: ItemHistoricoVistoriaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(vistoria: VistoriaEntity) {
            binding.tvPlacaHistorico.text = vistoria.placa
            binding.tvVeiculoHistorico.text = vistoria.veiculo
            binding.tvDataHoraHistorico.text = "${vistoria.data} - ${vistoria.hora}"

            binding.root.setOnClickListener { onVistoriaClick(vistoria) }
            binding.btnDeletarVistoria.setOnClickListener { onDeleteClick(vistoria) }
        }
    }
}
