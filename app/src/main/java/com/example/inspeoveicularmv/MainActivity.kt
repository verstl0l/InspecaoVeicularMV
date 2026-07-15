package com.example.inspecaoveicularmv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.inspecaoveicularmv.data.AppDatabase
import com.example.inspecaoveicularmv.data.VistoriaEntity
import com.example.inspecaoveicularmv.databinding.ActivityMainBinding
import com.example.inspecaoveicularmv.databinding.ItemVistoriaBinding
import com.example.inspecaoveicularmv.databinding.ItemSubvistoriaBinding
import com.example.inspecaoveicularmv.databinding.ItemSemFotoBinding
import com.example.inspecaoveicularmv.databinding.ItemSemFotoNaBinding
import com.example.inspecaoveicularmv.databinding.ItemCombustivelSliderBinding
import com.example.inspecaoveicularmv.databinding.ItemObservacoesFinaisBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var database: AppDatabase

    // Listas de itens
    private val itensSimples = ArrayList<ItemVistoria>()
    private val itensMultiplos = ArrayList<ItemVistoriaMultipla>()
    private val itensSemFoto = ArrayList<ItemVistoria>()
    private val itensSemFotoNA = ArrayList<ItemVistoria>()
    private val itensComExtra = ArrayList<ItemVistoria>()

    private var nivelCombustivel: String = "Reserva"
    private var observacoesFinais: String = ""

    // Controle de foto
    private var currentPhotoUri: Uri? = null
    private var currentItemIndex: Int = -1
    private var currentSubItemIndex: Int = -1
    private var currentPhotoType: String = ""
    private var currentImageView: ImageView? = null
    private var currentVistoriaId: Int = 0

    // Referências para os bindings dos itens (para atualização)
    private val itemSimplesBindings = ArrayList<ItemVistoriaBinding>()
    private val itemMultiplosBindings = ArrayList<ItemVistoriaBinding>()
    private val itemSemFotoBindings = ArrayList<ItemSemFotoBinding>()
    private val itemSemFotoNABindings = ArrayList<ItemSemFotoNaBinding>()
    private val itemComExtraBindings = ArrayList<ItemVistoriaBinding>()
    private var observacoesFinaisBinding: ItemObservacoesFinaisBinding? = null
    private var combustivelBinding: ItemCombustivelSliderBinding? = null

    // Permissões
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            mostrarDialogoPermissoes()
        }
    }

    // Tirar foto
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            when (currentPhotoType) {
                "simples" -> {
                    if (currentItemIndex in itensSimples.indices) {
                        processImageSimples(currentPhotoUri!!, currentItemIndex)
                    }
                }
                "multiplo" -> {
                    if (currentItemIndex in itensMultiplos.indices && currentSubItemIndex >= 0) {
                        processImageMultiplo(currentPhotoUri!!, currentItemIndex, currentSubItemIndex)
                    }
                }
                "extra" -> {
                    if (currentItemIndex in itensComExtra.indices) {
                        processImageExtra(currentPhotoUri!!, currentItemIndex)
                    }
                }
            }
        } else {
            Toast.makeText(this, "Falha ao tirar foto", Toast.LENGTH_SHORT).show()
        }
    }

    // Selecionar da galeria
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            when (currentPhotoType) {
                "simples" -> {
                    if (currentItemIndex in itensSimples.indices) {
                        processImageSimples(it, currentItemIndex)
                    }
                }
                "multiplo" -> {
                    if (currentItemIndex in itensMultiplos.indices && currentSubItemIndex >= 0) {
                        processImageMultiplo(it, currentItemIndex, currentSubItemIndex)
                    }
                }
                "extra" -> {
                    if (currentItemIndex in itensComExtra.indices) {
                        processImageExtra(it, currentItemIndex)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPermissoes()
        configurarDataHora()
        configurarTodosItens()

        pdfGenerator = PdfGenerator(this)
        database = AppDatabase.getDatabase(this)

        // Lógica de Inicialização (Novo ou Editar)
        val vistoriaId = intent.getIntExtra("VISTORIA_ID", 0)
        val novaVistoria = intent.getBooleanExtra("NOVA_VISTORIA", false)

        if (novaVistoria) {
            limparTodosOsCampos()
        } else if (vistoriaId != 0) {
            carregarVistoriaDoBanco(vistoriaId)
        } else {
            // Comportamento padrão (carregar último rascunho se existir)
            carregarDados()
        }

        // CAPITALIZAR TEXTOS
        capitalizarTexto(binding.etVeiculo)
        capitalizarTexto(binding.etFuncionario)

        // CONFIGURAR PLACA
        configurarPlacaMaiuscula()

        // AUTO SAVE
        configurarAutoSave()
        
        pdfGenerator = PdfGenerator(this)
        database = AppDatabase.getDatabase(this)

        binding.btnSalvarVistoria.setOnClickListener {
            salvarVistoriaManual()
        }

        binding.btnGerarPDF.setOnClickListener {
            gerarPDFCompleto()
        }
    }

    // ============ CICLO DE VIDA ============

    override fun onPause() {
        super.onPause()
        salvarDados()
    }

    override fun onStop() {
        super.onStop()
        salvarDados()
    }

    override fun onDestroy() {
        super.onDestroy()
        salvarDados()
    }

    // ============ CONFIGURAÇÕES ============

    private fun configurarPlacaMaiuscula() {
        binding.etPlaca.addTextChangedListener(object : android.text.TextWatcher {
            private var isEditing = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isEditing || s == null || s.isEmpty()) return
                isEditing = true
                val text = s.toString().uppercase(Locale.getDefault())
                if (s.toString() != text) {
                    s.replace(0, s.length, text)
                }
                isEditing = false
            }
        })
    }

    private fun validarPlaca(placa: String): Boolean {
        val placaLimpa = placa.replace(" ", "").replace("-", "").uppercase()
        val regexAntiga = Regex("^[A-Z]{3}[0-9]{4}$")
        val regexMercosul = Regex("^[A-Z]{3}[0-9][A-Z][0-9]{2}$")
        return regexAntiga.matches(placaLimpa) || regexMercosul.matches(placaLimpa)
    }

    private fun capitalizarTexto(view: View) {
        if (view is EditText) {
            view.addTextChangedListener(object : android.text.TextWatcher {
                private var isEditing = false
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (isEditing || s == null || s.isEmpty()) return
                    isEditing = true
                    val text = s.toString()
                    val capitalized = text.split(" ").joinToString(" ") { palavra ->
                        if (palavra.isNotEmpty()) {
                            palavra.substring(0, 1).uppercase() + palavra.substring(1).lowercase()
                        } else {
                            palavra
                        }
                    }
                    if (text != capitalized) {
                        s.replace(0, s.length, capitalized)
                    }
                    isEditing = false
                }
            })
        }
    }

    private fun configurarAutoSave() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                salvarDados()
            }
        }

        with(binding) {
            etVeiculo.addTextChangedListener(watcher)
            etPlaca.addTextChangedListener(watcher)
            etKm.addTextChangedListener(watcher)
            etData.addTextChangedListener(watcher)
            etHora.addTextChangedListener(watcher)
            etFuncionario.addTextChangedListener(watcher)
        }
    }
    // ============ DATA E HORA ============

    private fun configurarDataHora() {
        val dataFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val horaFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dataAtual = Date()

        binding.etData.setText(dataFormat.format(dataAtual))
        binding.etHora.setText(horaFormat.format(dataAtual))

        binding.etData.setOnClickListener {
            mostrarCalendario(binding.etData)
        }

        binding.etHora.setOnClickListener {
            mostrarRelogio(binding.etHora)
        }
    }

    private fun mostrarCalendario(editText: EditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val dataFormatada = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear)
                editText.setText(dataFormatada)
                salvarDados()
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun mostrarRelogio(editText: EditText) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = android.app.TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val horaFormatada = String.format("%02d:%02d", selectedHour, selectedMinute)
                editText.setText(horaFormatada)
                salvarDados()
            },
            hour, minute, true
        )
        timePickerDialog.show()
    }

    // ============ CONFIGURAR TODOS OS ITENS ============

    private fun configurarTodosItens() {
        // ITENS SIMPLES
        val itensSimplesNomes = listOf(
            "ESTEPE", "MACACO", "TRIÂNGULO", "CHAVE DE RODA",
            "CORREIAS", "FARÓIS", "LUZ DE FREIO", "LUZ DE RÉ"
        )

        for (nome in itensSimplesNomes) {
            val item = ItemVistoria(nome)
            itensSimples.add(item)
            adicionarItemSimples(item, itensSimples.size - 1)
        }

        // ITENS MÚLTIPLOS
        val pneus = ItemVistoriaMultipla(
            "PNEUS DE RODAGEM",
            listOf("Dianteiro Esquerdo", "Dianteiro Direito", "Traseiro Esquerdo", "Traseiro Direito")
        )
        itensMultiplos.add(pneus)
        adicionarItemMultiplo(pneus, itensMultiplos.size - 1)

        val lataria = ItemVistoriaMultipla(
            "LATARIA",
            listOf("Frente", "Trás", "Esquerda", "Direita", "Cima")
        )
        itensMultiplos.add(lataria)
        adicionarItemMultiplo(lataria, itensMultiplos.size - 1)

        val retrovisores = ItemVistoriaMultipla(
            "RETROVISORES",
            listOf("Esquerdo", "Direito")
        )
        itensMultiplos.add(retrovisores)
        adicionarItemMultiplo(retrovisores, itensMultiplos.size - 1)

        val limpador = ItemVistoriaMultipla(
            "LIMPADOR DE PARA-BRISA",
            listOf("Esquerdo", "Direito")
        )
        itensMultiplos.add(limpador)
        adicionarItemMultiplo(limpador, itensMultiplos.size - 1)

        val lanternas = ItemVistoriaMultipla(
            "LANTERNAS",
            listOf("Dianteira", "Traseira")
        )
        itensMultiplos.add(lanternas)
        adicionarItemMultiplo(lanternas, itensMultiplos.size - 1)

        val setas = ItemVistoriaMultipla(
            "LUZ DE DIREÇÃO (SETAS)",
            listOf("Dianteira Esq", "Dianteira Dir", "Traseira Esq", "Traseira Dir")
        )
        itensMultiplos.add(setas)
        adicionarItemMultiplo(setas, itensMultiplos.size - 1)

        val pisca = ItemVistoriaMultipla(
            "PISCA ALERTA",
            listOf("Esquerdo", "Direito")
        )
        itensMultiplos.add(pisca)
        adicionarItemMultiplo(pisca, itensMultiplos.size - 1)

        val limpeza = ItemVistoriaMultipla(
            "LIMPEZA",
            listOf("Interna", "Externa", "Frente", "Trás")
        )
        itensMultiplos.add(limpeza)
        adicionarItemMultiplo(limpeza, itensMultiplos.size - 1)

        // ITENS SEM FOTO
        val itensSemFotoNomes = listOf(
            "FREIO DE MÃO", "ALARME", "FECHADURAS DAS PORTAS",
            "NÍVEL DE ÓLEO DO FREIO", "NÍVEL DE ÁGUA"
        )

        for (nome in itensSemFotoNomes) {
            val item = ItemVistoria(nome)
            itensSemFoto.add(item)
            adicionarItemSemFoto(item, itensSemFoto.size - 1)
        }

        // ITEM SEM FOTO COM N/A
        val aguaBateria = ItemVistoria("ÁGUA DE BATERIA")
        itensSemFotoNA.add(aguaBateria)
        adicionarItemSemFotoNA(aguaBateria, 0)

        // ITENS COM EXTRA
        val extintor = ItemVistoria("EXTINTOR")
        itensComExtra.add(extintor)
        adicionarItemComExtra(extintor, 0, "Validade")

        val oleoMotor = ItemVistoria("NÍVEL DE ÓLEO DO MOTOR")
        itensComExtra.add(oleoMotor)
        adicionarItemComExtra(oleoMotor, 1, "KM última troca")

        // COMBUSTÍVEL
        adicionarItemCombustivel()

        // OBSERVAÇÕES FINAIS
        adicionarObservacoesFinais()
    }

    // ============ ADICIONAR ITENS ============

    private fun adicionarItemSimples(item: ItemVistoria, index: Int) {
        val itemBinding = ItemVistoriaBinding.inflate(
            LayoutInflater.from(this), binding.containerItens, false
        )
        itemBinding.tvNomeItem.text = item.nome
        itemSimplesBindings.add(itemBinding)

        val subBinding = ItemSubvistoriaBinding.inflate(
            LayoutInflater.from(this), itemBinding.containerSubItens, false
        )
        subBinding.tvNomeSubItem.text = "Foto"

        val finalIndex = index
        val imageView = subBinding.ivFotoSubItem

        imageView.setOnClickListener {
            itensSimples[finalIndex].fotoPath?.let { path ->
                mostrarImagemGrande(path)
            } ?: Toast.makeText(this, "Tire uma foto primeiro", Toast.LENGTH_SHORT).show()
        }

        subBinding.btnTirarFotoSubItem.setOnClickListener {
            currentPhotoType = "simples"
            currentItemIndex = finalIndex
            currentImageView = imageView
            tirarFoto()
        }

        subBinding.btnSelecionarFotoSubItem.setOnClickListener {
            currentPhotoType = "simples"
            currentItemIndex = finalIndex
            currentImageView = imageView
            selecionarFoto()
        }

        subBinding.btnOkSubItem.setOnClickListener {
            itensSimples[finalIndex].status = "OK"
            subBinding.tvStatusSubItem.text = "Status: OK ✓"
            subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#4CAF50"))
            itemBinding.tvStatusItem.text = "Status: OK ✓"
            itemBinding.tvStatusItem.setTextColor(Color.parseColor("#4CAF50"))
            salvarDados()
        }

        subBinding.btnIrregularSubItem.setOnClickListener {
            itensSimples[finalIndex].status = "IRREGULAR"
            subBinding.tvStatusSubItem.text = "Status: IRREGULAR ✗"
            subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#F44336"))
            itemBinding.tvStatusItem.text = "Status: IRREGULAR ✗"
            itemBinding.tvStatusItem.setTextColor(Color.parseColor("#F44336"))
            salvarDados()
        }

        itemBinding.etObservacaoItem.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                itensSimples[finalIndex].observacao = s.toString()
            }
        })

        itemBinding.containerSubItens.addView(subBinding.root)
        binding.containerItens.addView(itemBinding.root)
    }

    private fun adicionarItemMultiplo(item: ItemVistoriaMultipla, index: Int) {
        val itemBinding = ItemVistoriaBinding.inflate(
            LayoutInflater.from(this), binding.containerItens, false
        )
        itemBinding.tvNomeItem.text = item.nome
        itemMultiplosBindings.add(itemBinding)

        for (subIndex in item.subItens.indices) {
            val subBinding = ItemSubvistoriaBinding.inflate(
                LayoutInflater.from(this), itemBinding.containerSubItens, false
            )
            subBinding.tvNomeSubItem.text = item.subItens[subIndex]

            val finalIndex = index
            val finalSubIndex = subIndex
            val imageView = subBinding.ivFotoSubItem

            imageView.setOnClickListener {
                item.fotoPaths[finalSubIndex]?.let { path ->
                    mostrarImagemGrande(path)
                } ?: Toast.makeText(this, "Tire uma foto primeiro", Toast.LENGTH_SHORT).show()
            }

            subBinding.btnTirarFotoSubItem.setOnClickListener {
                currentPhotoType = "multiplo"
                currentItemIndex = finalIndex
                currentSubItemIndex = finalSubIndex
                currentImageView = imageView
                tirarFoto()
            }

            subBinding.btnSelecionarFotoSubItem.setOnClickListener {
                currentPhotoType = "multiplo"
                currentItemIndex = finalIndex
                currentSubItemIndex = finalSubIndex
                currentImageView = imageView
                selecionarFoto()
            }

            subBinding.btnOkSubItem.setOnClickListener {
                item.statusSubItens[finalSubIndex] = "OK"
                subBinding.tvStatusSubItem.text = "Status: OK ✓"
                subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#4CAF50"))
                atualizarStatusMultiplo(item, itemBinding)
                salvarDados()
            }

            subBinding.btnIrregularSubItem.setOnClickListener {
                item.statusSubItens[finalSubIndex] = "IRREGULAR"
                subBinding.tvStatusSubItem.text = "Status: IRREGULAR ✗"
                subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#F44336"))
                atualizarStatusMultiplo(item, itemBinding)
                salvarDados()
            }

            itemBinding.containerSubItens.addView(subBinding.root)
        }

        itemBinding.etObservacaoItem.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                item.observacao = s.toString()
            }
        })

        binding.containerItens.addView(itemBinding.root)
    }

    private fun atualizarStatusMultiplo(item: ItemVistoriaMultipla, binding: ItemVistoriaBinding) {
        val status = item.getStatusGeral()
        when (status) {
            "OK" -> {
                binding.tvStatusItem.text = "Status: OK ✓"
                binding.tvStatusItem.setTextColor(Color.parseColor("#4CAF50"))
            }
            "IRREGULAR" -> {
                binding.tvStatusItem.text = "Status: IRREGULAR ✗"
                binding.tvStatusItem.setTextColor(Color.parseColor("#F44336"))
            }
            else -> {
                binding.tvStatusItem.text = "Status: Aguardando"
                binding.tvStatusItem.setTextColor(Color.parseColor("#999999"))
            }
        }
    }

    private fun adicionarItemSemFoto(item: ItemVistoria, index: Int) {
        val binding = ItemSemFotoBinding.inflate(
            LayoutInflater.from(this), this.binding.containerItens, false
        )
        binding.tvNomeSemFoto.text = item.nome
        itemSemFotoBindings.add(binding)

        val finalIndex = index

        binding.btnOkSemFoto.setOnClickListener {
            itensSemFoto[finalIndex].status = "OK"
            binding.tvStatusSemFoto.text = "Status: OK ✓"
            binding.tvStatusSemFoto.setTextColor(Color.parseColor("#4CAF50"))
            salvarDados()
        }

        binding.btnIrregularSemFoto.setOnClickListener {
            itensSemFoto[finalIndex].status = "IRREGULAR"
            binding.tvStatusSemFoto.text = "Status: IRREGULAR ✗"
            binding.tvStatusSemFoto.setTextColor(Color.parseColor("#F44336"))
            salvarDados()
        }

        this.binding.containerItens.addView(binding.root)
    }

    private fun adicionarItemSemFotoNA(item: ItemVistoria, index: Int) {
        val binding = ItemSemFotoNaBinding.inflate(
            LayoutInflater.from(this), this.binding.containerItens, false
        )
        binding.tvNomeSemFotoNA.text = item.nome
        itemSemFotoNABindings.add(binding)

        val finalIndex = index

        binding.btnOkSemFotoNA.setOnClickListener {
            itensSemFotoNA[finalIndex].status = "OK"
            binding.tvStatusSemFotoNA.text = "Status: OK ✓"
            binding.tvStatusSemFotoNA.setTextColor(Color.parseColor("#4CAF50"))
            salvarDados()
        }

        binding.btnIrregularSemFotoNA.setOnClickListener {
            itensSemFotoNA[finalIndex].status = "IRREGULAR"
            binding.tvStatusSemFotoNA.text = "Status: IRREGULAR ✗"
            binding.tvStatusSemFotoNA.setTextColor(Color.parseColor("#F44336"))
            salvarDados()
        }

        binding.btnNaoSeAplicaSemFotoNA.setOnClickListener {
            itensSemFotoNA[finalIndex].status = "NAO_SE_APLICA"
            binding.tvStatusSemFotoNA.text = "Status: N/A"
            binding.tvStatusSemFotoNA.setTextColor(Color.parseColor("#FF9800"))
            salvarDados()
        }

        this.binding.containerItens.addView(binding.root)
    }

    private fun adicionarItemComExtra(item: ItemVistoria, index: Int, hint: String) {
        val itemBinding = ItemVistoriaBinding.inflate(
            LayoutInflater.from(this), binding.containerItens, false
        )
        itemBinding.tvNomeItem.text = item.nome
        itemComExtraBindings.add(itemBinding)

        val subBinding = ItemSubvistoriaBinding.inflate(
            LayoutInflater.from(this), itemBinding.containerSubItens, false
        )
        subBinding.tvNomeSubItem.text = "Foto"

        val finalIndex = index
        val imageView = subBinding.ivFotoSubItem

        imageView.setOnClickListener {
            itensSimples[finalIndex].fotoPath?.let { path ->
                mostrarImagemGrande(path)
            } ?: Toast.makeText(this, "Tire uma foto primeiro", Toast.LENGTH_SHORT).show()
        }

        subBinding.btnTirarFotoSubItem.setOnClickListener {
            currentPhotoType = "extra"
            currentItemIndex = finalIndex
            currentImageView = imageView
            tirarFoto()
        }

        subBinding.btnSelecionarFotoSubItem.setOnClickListener {
            currentPhotoType = "extra"
            currentItemIndex = finalIndex
            currentImageView = imageView
            selecionarFoto()
        }

        subBinding.btnOkSubItem.setOnClickListener {
            itensComExtra[finalIndex].status = "OK"
            subBinding.tvStatusSubItem.text = "Status: OK ✓"
            subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#4CAF50"))
            itemBinding.tvStatusItem.text = "Status: OK ✓"
            itemBinding.tvStatusItem.setTextColor(Color.parseColor("#4CAF50"))
            salvarDados()
        }

        subBinding.btnIrregularSubItem.setOnClickListener {
            itensComExtra[finalIndex].status = "IRREGULAR"
            subBinding.tvStatusSubItem.text = "Status: IRREGULAR ✗"
            subBinding.tvStatusSubItem.setTextColor(Color.parseColor("#F44336"))
            itemBinding.tvStatusItem.text = "Status: IRREGULAR ✗"
            itemBinding.tvStatusItem.setTextColor(Color.parseColor("#F44336"))
            salvarDados()
        }

        itemBinding.containerSubItens.addView(subBinding.root)

        // Campo extra
        if (hint == "Validade") {
            val etExtra = EditText(this)
            etExtra.hint = hint
            etExtra.setPadding(16, 16, 16, 16)
            etExtra.background = ContextCompat.getDrawable(this, android.R.drawable.editbox_background)
            etExtra.inputType = android.text.InputType.TYPE_NULL
            etExtra.isFocusable = false
            etExtra.isClickable = true
            etExtra.setOnClickListener {
                mostrarCalendario(etExtra)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            etExtra.layoutParams = params
            itemBinding.containerSubItens.addView(etExtra)

            etExtra.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    itensComExtra[finalIndex].valorExtra = s.toString()
                }
            })
        } else {
            val etExtra = EditText(this)
            etExtra.hint = hint
            etExtra.setPadding(16, 16, 16, 16)
            etExtra.background = ContextCompat.getDrawable(this, android.R.drawable.editbox_background)
            etExtra.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            etExtra.layoutParams = params
            itemBinding.containerSubItens.addView(etExtra)

            etExtra.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    itensComExtra[finalIndex].valorExtra = s.toString()
                }
            })
        }

        itemBinding.etObservacaoItem.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                itensComExtra[finalIndex].observacao = s.toString()
            }
        })

        binding.containerItens.addView(itemBinding.root)
    }

    private fun adicionarItemCombustivel() {
        val binding = ItemCombustivelSliderBinding.inflate(
            LayoutInflater.from(this), this.binding.containerItens, false
        )
        combustivelBinding = binding

        val niveis = listOf("Reserva", "1/4", "1/2", "3/4", "Cheio")

        // Configurar cor da bolinha
        binding.seekBarCombustivel.thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2196F3"))
        binding.seekBarCombustivel.progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2196F3"))

        binding.seekBarCombustivel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val nivel = niveis[progress]
                nivelCombustivel = nivel
                binding.tvNivelCombustivel.text = "Nível selecionado: $nivel"
                salvarDados()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        this.binding.containerItens.addView(binding.root)
    }

    private fun adicionarObservacoesFinais() {
        val binding = ItemObservacoesFinaisBinding.inflate(
            LayoutInflater.from(this), this.binding.containerItens, false
        )
        observacoesFinaisBinding = binding

        binding.etObservacoesFinais.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                observacoesFinais = s.toString()
                // Não chamamos salvarDados() aqui para evitar excesso de escrita no disco, 
                // o autoSave e o onPause já cuidam disso.
            }
        })

        this.binding.containerItens.addView(binding.root)
    }

    // ============ MÉTODOS DE FOTO ============

    private fun tirarFoto() {
        val photoFile = criarArquivoFoto()
        photoFile?.let {
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            takePictureLauncher.launch(currentPhotoUri)
        }
    }

    private fun selecionarFoto() {
        pickImageLauncher.launch("image/*")
    }

    private fun criarArquivoFoto(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return try {
            File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        } catch (e: IOException) {
            Toast.makeText(this, "Erro ao criar arquivo", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun processImageSimples(uri: Uri, index: Int) {
        try {
            val bitmap = redimensionarBitmap(uri)
            val path = salvarImagem(bitmap, "simples_$index")
            itensSimples[index].fotoPath = path
            currentImageView?.load(File(path))
            currentImageView = null
            salvarDados()
            Toast.makeText(this, "Foto carregada!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Erro ao processar imagem", e)
        }
    }

    private fun processImageMultiplo(uri: Uri, itemIndex: Int, subIndex: Int) {
        try {
            val bitmap = redimensionarBitmap(uri)
            val path = salvarImagem(bitmap, "multiplo_${itemIndex}_$subIndex")
            itensMultiplos[itemIndex].fotoPaths[subIndex] = path
            currentImageView?.load(File(path))
            currentImageView = null
            salvarDados()
            Toast.makeText(this, "Foto carregada!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Erro ao processar imagem", e)
        }
    }

    private fun processImageExtra(uri: Uri, index: Int) {
        try {
            val bitmap = redimensionarBitmap(uri)
            val path = salvarImagem(bitmap, "extra_$index")
            itensComExtra[index].fotoPath = path
            currentImageView?.load(File(path))
            currentImageView = null
            salvarDados()
            Toast.makeText(this, "Foto carregada!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao processar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Erro ao processar imagem", e)
        }
    }

    private fun salvarImagem(bitmap: Bitmap, nome: String): String {
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "IMG_${nome}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file.absolutePath
    }

    private fun redimensionarBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Não foi possível abrir URI")
        
        // Decodificar apenas dimensões primeiro para evitar OOM
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calcular escala
        val targetSize = 600 // Aumentei um pouco para melhor qualidade no PDF
        var scale = 1
        while (options.outWidth / scale / 2 >= targetSize && options.outHeight / scale / 2 >= targetSize) {
            scale *= 2
        }

        // Decodificar com escala
        val inputStream2 = contentResolver.openInputStream(uri)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val scaledBitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
        inputStream2?.close()

        return Bitmap.createScaledBitmap(scaledBitmap!!, targetSize, (scaledBitmap.height * targetSize / scaledBitmap.width), true)
    }

    // ============ SALVAR E CARREGAR DADOS ============

    private fun salvarDados() {
        val sharedPref = getSharedPreferences("vistoria_prefs", MODE_PRIVATE)
        val editor = sharedPref.edit()

        // Dados do veículo
        editor.putString("veiculo", binding.etVeiculo.text.toString())
        editor.putString("placa", binding.etPlaca.text.toString())
        editor.putString("km", binding.etKm.text.toString())
        editor.putString("data", binding.etData.text.toString())
        editor.putString("hora", binding.etHora.text.toString())
        editor.putString("funcionario", binding.etFuncionario.text.toString())

        // Itens Simples
        for (i in itensSimples.indices) {
            val item = itensSimples[i]
            editor.putString("simples_status_$i", item.status)
            editor.putString("simples_obs_$i", item.observacao)
            editor.putString("simples_foto_$i", item.fotoPath)
        }
        editor.putInt("simples_count", itensSimples.size)

        // Itens Múltiplos
        for (i in itensMultiplos.indices) {
            val item = itensMultiplos[i]
            for (j in item.subItens.indices) {
                editor.putString("multiplo_status_${i}_$j", item.statusSubItens[j])
                editor.putString("multiplo_foto_${i}_$j", item.fotoPaths[j])
            }
            editor.putString("multiplo_obs_$i", item.observacao)
        }
        editor.putInt("multiplos_count", itensMultiplos.size)

        // Itens Sem Foto
        for (i in itensSemFoto.indices) {
            editor.putString("semfoto_status_$i", itensSemFoto[i].status)
        }
        editor.putInt("semfoto_count", itensSemFoto.size)

        // Itens Sem Foto com N/A
        for (i in itensSemFotoNA.indices) {
            editor.putString("semfotona_status_$i", itensSemFotoNA[i].status)
        }
        editor.putInt("semfotona_count", itensSemFotoNA.size)

        // Itens com Extra
        for (i in itensComExtra.indices) {
            val item = itensComExtra[i]
            editor.putString("extra_status_$i", item.status)
            editor.putString("extra_valor_$i", item.valorExtra)
            editor.putString("extra_obs_$i", item.observacao)
            editor.putString("extra_foto_$i", item.fotoPath)
        }
        editor.putInt("extra_count", itensComExtra.size)

        // Combustível
        editor.putString("combustivel", nivelCombustivel)

        // Observações Finais
        observacoesFinaisBinding?.let {
            observacoesFinais = it.etObservacoesFinais.text.toString()
        }
        editor.putString("observacoes_finais", observacoesFinais)

        editor.apply()
        Log.d("MainActivity", "Dados salvos com sucesso!")
    }

    private fun carregarDados() {
        val sharedPref = getSharedPreferences("vistoria_prefs", MODE_PRIVATE)

        // Dados do veículo
        binding.etVeiculo.setText(sharedPref.getString("veiculo", ""))
        binding.etPlaca.setText(sharedPref.getString("placa", ""))
        binding.etKm.setText(sharedPref.getString("km", ""))
        binding.etData.setText(sharedPref.getString("data", ""))
        binding.etHora.setText(sharedPref.getString("hora", ""))
        binding.etFuncionario.setText(sharedPref.getString("funcionario", ""))

        // Itens Simples
        val simplesCount = sharedPref.getInt("simples_count", 0)
        if (simplesCount > 0 && itensSimples.isNotEmpty()) {
            for (i in 0 until minOf(simplesCount, itensSimples.size)) {
                val item = itensSimples[i]
                item.status = sharedPref.getString("simples_status_$i", "Aguardando") ?: "Aguardando"
                item.observacao = sharedPref.getString("simples_obs_$i", "") ?: ""
                val fotoPath = sharedPref.getString("simples_foto_$i", null)
                if (fotoPath != null) {
                    item.fotoPath = fotoPath
                    if (i < itemSimplesBindings.size) {
                        val container = itemSimplesBindings[i].containerSubItens
                        for (j in 0 until container.childCount) {
                            val child = container.getChildAt(j)
                            if (child is android.view.ViewGroup) {
                                val imageView = child.findViewById<ImageView>(R.id.ivFotoSubItem)
                                imageView?.load(File(fotoPath))
                                break
                            }
                        }
                    }
                }
                // Atualizar status
                if (i < itemSimplesBindings.size) {
                    val binding = itemSimplesBindings[i]
                    if (item.status == "OK") {
                        binding.tvStatusItem.text = "Status: OK ✓"
                        binding.tvStatusItem.setTextColor(Color.parseColor("#4CAF50"))
                    } else if (item.status == "IRREGULAR") {
                        binding.tvStatusItem.text = "Status: IRREGULAR ✗"
                        binding.tvStatusItem.setTextColor(Color.parseColor("#F44336"))
                    }
                }
            }
        }

        // Itens Múltiplos
        val multiplosCount = sharedPref.getInt("multiplos_count", 0)
        if (multiplosCount > 0 && itensMultiplos.isNotEmpty()) {
            for (i in 0 until minOf(multiplosCount, itensMultiplos.size)) {
                val item = itensMultiplos[i]
                for (j in item.subItens.indices) {
                    item.statusSubItens[j] = sharedPref.getString("multiplo_status_${i}_$j", "Aguardando") ?: "Aguardando"
                    val path = sharedPref.getString("multiplo_foto_${i}_$j", null)
                    if (path != null) {
                        item.fotoPaths[j] = path
                        if (i < itemMultiplosBindings.size) {
                            val container = itemMultiplosBindings[i].containerSubItens
                            if (j < container.childCount) {
                                val subItemView = container.getChildAt(j)
                                val imageView = subItemView.findViewById<ImageView>(R.id.ivFotoSubItem)
                                imageView?.load(File(path))
                            }
                        }
                    }
                }
                item.observacao = sharedPref.getString("multiplo_obs_$i", "") ?: ""
                if (i < itemMultiplosBindings.size) {
                    atualizarStatusMultiplo(item, itemMultiplosBindings[i])
                }
            }
        }

        // Itens com Extra
        val extraCount = sharedPref.getInt("extra_count", 0)
        if (extraCount > 0 && itensComExtra.isNotEmpty()) {
            for (i in 0 until minOf(extraCount, itensComExtra.size)) {
                val item = itensComExtra[i]
                item.status = sharedPref.getString("extra_status_$i", "Aguardando") ?: "Aguardando"
                item.valorExtra = sharedPref.getString("extra_valor_$i", "") ?: ""
                item.observacao = sharedPref.getString("extra_obs_$i", "") ?: ""
                val path = sharedPref.getString("extra_foto_$i", null)
                if (path != null) {
                    item.fotoPath = path
                    if (i < itemComExtraBindings.size) {
                        val container = itemComExtraBindings[i].containerSubItens
                        for (k in 0 until container.childCount) {
                            val child = container.getChildAt(k)
                            val imageView = child.findViewById<ImageView>(R.id.ivFotoSubItem)
                            if (imageView != null) {
                                imageView.load(File(path))
                                break
                            }
                        }
                    }
                }
            }
        }

        // Combustível
        nivelCombustivel = sharedPref.getString("combustivel", "Reserva") ?: "Reserva"
        combustivelBinding?.let { binding ->
            val niveis = listOf("Reserva", "1/4", "1/2", "3/4", "Cheio")
            val index = niveis.indexOf(nivelCombustivel)
            if (index >= 0) {
                binding.seekBarCombustivel.progress = index
                binding.tvNivelCombustivel.text = "Nível selecionado: $nivelCombustivel"
            }
        }

        // Observações Finais
        observacoesFinais = sharedPref.getString("observacoes_finais", "") ?: ""
        observacoesFinaisBinding?.etObservacoesFinais?.setText(observacoesFinais)

        Log.d("MainActivity", "Dados carregados com sucesso!")
    }

    // ============ GERAR PDF ============

    private fun gerarPDFCompleto() {
        val veiculo = binding.etVeiculo.text.toString().trim()
        val placa = binding.etPlaca.text.toString().trim()
        val km = binding.etKm.text.toString().trim()
        val funcionario = binding.etFuncionario.text.toString().trim()

        if (veiculo.isEmpty() || placa.isEmpty() || km.isEmpty() || funcionario.isEmpty()) {
            Toast.makeText(this, "Preencha todos os dados do veículo!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validarPlaca(placa)) {
            AlertDialog.Builder(this)
                .setTitle("Placa Inválida")
                .setMessage("Formato de placa inválido!\n\nUse um dos formatos:\n• Placa Antiga: ABC-1234\n• Placa Mercosul: ABC1D23")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Verificar itens incompletos (Fotos ou Status em Aguardando)
        val pendencias = mutableListOf<String>()

        for (item in itensSimples) {
            if (item.fotoPath == null || item.status == "Aguardando") {
                pendencias.add("• ${item.nome} (Falta foto ou status)")
            }
        }

        for (item in itensMultiplos) {
            for (i in item.subItens.indices) {
                if (item.fotoPaths[i] == null || item.statusSubItens[i] == "Aguardando") {
                    pendencias.add("• ${item.nome} - ${item.subItens[i]}")
                }
            }
        }

        for (item in itensSemFoto) {
            if (item.status == "Aguardando") {
                pendencias.add("• ${item.nome} (Falta status)")
            }
        }

        for (item in itensSemFotoNA) {
            if (item.status == "Aguardando") {
                pendencias.add("• ${item.nome} (Falta status)")
            }
        }

        for (item in itensComExtra) {
            if (item.fotoPath == null || item.status == "Aguardando" || item.valorExtra.isEmpty()) {
                pendencias.add("• ${item.nome} (Falta foto, status ou valor)")
            }
        }

        if (pendencias.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Pendências Encontradas")
                .setMessage("Não é possível gerar o PDF. Verifique os seguintes itens:\n\n${pendencias.take(10).joinToString("\n")}${if(pendencias.size > 10) "\n... e mais ${pendencias.size - 10}" else ""}")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnGerarPDF.isEnabled = false

        try {
            val pdfFile = pdfGenerator.gerarRelatorio(
                veiculo, placa, km,
                binding.etData.text.toString(),
                binding.etHora.text.toString(),
                funcionario,
                itensSimples,
                itensMultiplos,
                itensSemFoto,
                itensSemFotoNA,
                itensComExtra,
                nivelCombustivel,
                observacoesFinais
            )

            if (pdfFile != null) {
                salvarNoBancoDeDados(veiculo, placa, km, funcionario)
                enviarPDF(pdfFile)
                // Opcional: finish() aqui se quiser voltar ao histórico após enviar
            } else {
                Toast.makeText(this, "Erro ao criar PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Erro ao gerar PDF", e)
        } finally {
            binding.progressBar.visibility = View.GONE
            binding.btnGerarPDF.isEnabled = true
        }
    }

    private fun mostrarImagemGrande(path: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_foto_grande, null)
        val ivGrande = dialogView.findViewById<ImageView>(R.id.ivFotoGrande)
        ivGrande.load(File(path))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun salvarVistoriaManual() {
        val veiculo = binding.etVeiculo.text.toString().trim()
        val placa = binding.etPlaca.text.toString().trim()

        if (veiculo.isEmpty() || placa.isEmpty()) {
            Toast.makeText(this, "Preencha pelo menos Veículo e Placa!", Toast.LENGTH_SHORT).show()
            return
        }

        salvarNoBancoDeDados(veiculo, placa, binding.etKm.text.toString(), binding.etFuncionario.text.toString())
        Toast.makeText(this, "Vistoria salva com sucesso!", Toast.LENGTH_SHORT).show()
        finish() // Volta para o histórico
    }

    private fun carregarVistoriaDoBanco(id: Int) {
        lifecycleScope.launch {
            val vistoria = database.vistoriaDao().getVistoriaById(id)
            vistoria?.let {
                currentVistoriaId = it.id
                preencherCamposComVistoria(it)
            }
        }
    }

    private fun limparTodosOsCampos() {
        currentVistoriaId = 0
        binding.etVeiculo.setText("")
        binding.etPlaca.setText("")
        binding.etKm.setText("")
        binding.etFuncionario.setText("")
        configurarDataHora() // Reseta data/hora para atual
        
        // Resetar itens (limpar fotos e status)
        for (item in itensSimples) {
            item.status = "Aguardando"
            item.fotoPath = null
            item.observacao = ""
        }
        // ... repopular o container ou atualizar os bindings existentes
        // Para simplificar e garantir que a UI reflita, vamos recriar os itens
        binding.containerItens.removeAllViews()
        itensSimples.clear()
        itensMultiplos.clear()
        itensSemFoto.clear()
        itensSemFotoNA.clear()
        itensComExtra.clear()
        itemSimplesBindings.clear()
        itemMultiplosBindings.clear()
        itemSemFotoBindings.clear()
        itemSemFotoNABindings.clear()
        itemComExtraBindings.clear()
        configurarTodosItens()
    }

    private fun preencherCamposComVistoria(v: VistoriaEntity) {
        binding.etVeiculo.setText(v.veiculo)
        binding.etPlaca.setText(v.placa)
        binding.etKm.setText(v.km)
        binding.etData.setText(v.data)
        binding.etHora.setText(v.hora)
        binding.etFuncionario.setText(v.funcionario)

        // Nota: Como os itens são gerados dinamicamente no configurarTodosItens, 
        // precisamos garantir que os objetos nas listas (itensSimples, etc) 
        // recebam os valores carregados e a UI seja atualizada.
        
        // Vamos carregar os dados nas listas e forçar a atualização da UI
        // Para evitar duplicidade, vamos recriar a UI com os dados carregados
        
        binding.containerItens.removeAllViews()
        itensSimples.clear()
        itensMultiplos.clear()
        itensSemFoto.clear()
        itensSemFotoNA.clear()
        itensComExtra.clear()
        itemSimplesBindings.clear()
        itemMultiplosBindings.clear()
        itemSemFotoBindings.clear()
        itemSemFotoNABindings.clear()
        itemComExtraBindings.clear()

        // Repopular listas com dados do banco
        itensSimples.addAll(v.itensSimples)
        itensMultiplos.addAll(v.itensMultiplos)
        itensSemFoto.addAll(v.itensSemFoto)
        itensSemFotoNA.addAll(v.itensSemFotoNA)
        itensComExtra.addAll(v.itensComExtra)
        nivelCombustivel = v.nivelCombustivel
        observacoesFinais = v.observacoesFinais

        // Recriar Bindings na UI
        for (i in itensSimples.indices) adicionarItemSimples(itensSimples[i], i)
        for (i in itensMultiplos.indices) adicionarItemMultiplo(itensMultiplos[i], i)
        for (i in itensSemFoto.indices) adicionarItemSemFoto(itensSemFoto[i], i)
        for (i in itensSemFotoNA.indices) adicionarItemSemFotoNA(itensSemFotoNA[i], i)
        for (i in itensComExtra.indices) {
            val hint = if (itensComExtra[i].nome == "EXTINTOR") "Validade" else "KM última troca"
            adicionarItemComExtra(itensComExtra[i], i, hint)
        }
        adicionarItemCombustivel()
        adicionarObservacoesFinais()

        // Atualizar visual das fotos e status na UI recém criada
        // (A lógica de adicionarItem... já usa os valores do objeto 'item')
        // Mas precisamos carregar as imagens no ImageView usando Coil
        atualizarImagensEStatus()
    }

    private fun atualizarImagensEStatus() {
        // Itens Simples
        for (i in itensSimples.indices) {
            val item = itensSimples[i]
            val binding = itemSimplesBindings[i]
            item.fotoPath?.let { path ->
                val container = binding.containerSubItens
                val subItemView = container.getChildAt(0)
                val imageView = subItemView.findViewById<ImageView>(R.id.ivFotoSubItem)
                imageView.load(File(path))
            }
            if (item.status == "OK") {
                binding.tvStatusItem.text = "Status: OK ✓"
                binding.tvStatusItem.setTextColor(Color.parseColor("#4CAF50"))
            } else if (item.status == "IRREGULAR") {
                binding.tvStatusItem.text = "Status: IRREGULAR ✗"
                binding.tvStatusItem.setTextColor(Color.parseColor("#F44336"))
            }
            binding.etObservacaoItem.setText(item.observacao)
        }

        // Itens Múltiplos
        for (i in itensMultiplos.indices) {
            val item = itensMultiplos[i]
            val binding = itemMultiplosBindings[i]
            val container = binding.containerSubItens
            for (j in item.subItens.indices) {
                val subItemView = container.getChildAt(j)
                val imageView = subItemView.findViewById<ImageView>(R.id.ivFotoSubItem)
                val tvStatus = subItemView.findViewById<TextView>(R.id.tvStatusSubItem)
                
                item.fotoPaths[j]?.let { path -> imageView.load(File(path)) }
                
                if (item.statusSubItens[j] == "OK") {
                    tvStatus.text = "Status: OK ✓"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                } else if (item.statusSubItens[j] == "IRREGULAR") {
                    tvStatus.text = "Status: IRREGULAR ✗"
                    tvStatus.setTextColor(Color.parseColor("#F44336"))
                }
            }
            atualizarStatusMultiplo(item, binding)
            binding.etObservacaoItem.setText(item.observacao)
        }
        
        // Combustível
        combustivelBinding?.let { b ->
            val niveis = listOf("Reserva", "1/4", "1/2", "3/4", "Cheio")
            val index = niveis.indexOf(nivelCombustivel)
            if (index >= 0) {
                b.seekBarCombustivel.progress = index
                b.tvNivelCombustivel.text = "Nível selecionado: $nivelCombustivel"
            }
        }

        // Obs Finais
        observacoesFinaisBinding?.etObservacoesFinais?.setText(observacoesFinais)
    }

    private fun salvarNoBancoDeDados(veiculo: String, placa: String, km: String, funcionario: String) {
        val vistoria = VistoriaEntity(
            id = currentVistoriaId,
            veiculo = veiculo,
            placa = placa,
            km = km,
            data = binding.etData.text.toString(),
            hora = binding.etHora.text.toString(),
            funcionario = funcionario,
            itensSimples = itensSimples,
            itensMultiplos = itensMultiplos,
            itensSemFoto = itensSemFoto,
            itensSemFotoNA = itensSemFotoNA,
            itensComExtra = itensComExtra,
            nivelCombustivel = nivelCombustivel,
            observacoesFinais = observacoesFinais
        )

        lifecycleScope.launch {
            try {
                database.vistoriaDao().insert(vistoria)
                Log.d("MainActivity", "Vistoria salva no banco de dados!")
            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao salvar no banco", e)
            }
        }
    }

    private fun enviarPDF(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Relatório de Vistoria Veicular")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Enviar vistoria como PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao enviar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ============ PERMISSÕES ============

    private fun verificarPermissoes() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun mostrarDialogoPermissoes() {
        AlertDialog.Builder(this)
            .setTitle("Permissões Necessárias")
            .setMessage("Este app precisa de permissões para câmera e armazenamento.")
            .setPositiveButton("Abrir Configurações") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}