package com.example.inspecaoveicularmv

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class PdfGenerator(private val context: Context) {

    fun gerarRelatorio(
        veiculo: String,
        placa: String,
        km: String,
        data: String,
        hora: String,
        funcionario: String,
        itensSimples: List<ItemVistoria>,
        itensMultiplos: List<ItemVistoriaMultipla>,
        itensSemFoto: List<ItemVistoria>,
        itensSemFotoNA: List<ItemVistoria>,
        itensComExtra: List<ItemVistoria>,
        nivelCombustivel: String,
        observacoesFinais: String
    ): File? {
        val pdfFile = File(context.cacheDir, "vistoria_${System.currentTimeMillis()}.pdf")

        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 800
            val pageHeight = 1200
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            canvas.drawColor(Color.parseColor("#F0F0F0"))

            val paint = Paint().apply { isAntiAlias = true }
            var yPos = 0f

            // Cabeçalho
            paint.color = Color.parseColor("#666666")
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 120f, paint)

            yPos = 75f
            paint.apply {
                color = Color.WHITE
                textSize = 36f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Relatório de Vistoria", (pageWidth / 2).toFloat(), yPos, paint)

            // Card Dados Veículo
            yPos = 140f
            drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, 200f)

            paint.apply {
                textAlign = Paint.Align.LEFT
                textSize = 20f
                color = Color.parseColor("#666666")
                isFakeBoldText = true
            }
            canvas.drawText("DADOS DO VEÍCULO", 40f, yPos + 40f, paint)

            paint.apply {
                textSize = 16f
                color = Color.BLACK
                isFakeBoldText = false
            }
            val dados = listOf(
                "Veículo: $veiculo", "Placa: $placa", "KM: $km",
                "Data: $data | Hora: $hora",
                "Funcionário: $funcionario"
            )
            var internalY = yPos + 75f
            for (dado in dados) {
                canvas.drawText(dado, 40f, internalY, paint)
                internalY += 25f
            }

            yPos += 220f

            fun checkPage(neededHeight: Float) {
                if (yPos + neededHeight > pageHeight - 50f) {
                    pdfDocument.finishPage(page)
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(Color.parseColor("#F0F0F0"))
                    yPos = 30f
                }
            }

            // ITENS SIMPLES
            for (item in itensSimples) {
                var scaledBitmap: Bitmap? = null
                var imageWidth = 0f
                var imageHeight = 0f

                if (item.fotoPath != null) {
                    val original = BitmapFactory.decodeFile(item.fotoPath)
                    original?.let {
                        scaledBitmap = resizeForPdf(it, 600)
                        imageWidth = scaledBitmap!!.width.toFloat()
                        imageHeight = scaledBitmap!!.height.toFloat()
                    }
                }

                val hasFoto = scaledBitmap != null
                val cardHeight = if (hasFoto) imageHeight + 120f else 110f
                checkPage(cardHeight)

                drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, cardHeight - 20f)

                paint.apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
                canvas.drawText(item.nome, 40f, yPos + 40f, paint)
                drawStatusBadge(canvas, (pageWidth - 180).toFloat(), yPos + 20f, item.status)

                if (item.observacao.isNotEmpty()) {
                    paint.apply { textSize = 14f; isFakeBoldText = false; color = Color.DKGRAY }
                    canvas.drawText("Obs: ${item.observacao}", 40f, yPos + 70f, paint)
                }

                if (hasFoto && scaledBitmap != null) {
                    val xCentered = (pageWidth - imageWidth) / 2f
                    canvas.drawBitmap(scaledBitmap!!, xCentered, yPos + 90f, null)
                }
                yPos += cardHeight
            }

            // ITENS MÚLTIPLOS
            for (item in itensMultiplos) {
                checkPage(100f)
                drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, 80f)
                paint.apply { textSize = 20f; isFakeBoldText = true; color = Color.parseColor("#666666") }
                canvas.drawText(item.nome.uppercase(), 40f, yPos + 50f, paint)
                yPos += 100f

                for (i in item.subItens.indices) {
                    var scaledSubBitmap: Bitmap? = null
                    var subImgW = 0f
                    var subImgH = 0f

                    if (item.fotoPaths[i] != null) {
                        val original = BitmapFactory.decodeFile(item.fotoPaths[i])
                        original?.let {
                            scaledSubBitmap = resizeForPdf(it, 500)
                            subImgW = scaledSubBitmap!!.width.toFloat()
                            subImgH = scaledSubBitmap!!.height.toFloat()
                        }
                    }

                    val hasFoto = scaledSubBitmap != null
                    val cardHeight = if (hasFoto) subImgH + 100f else 95f
                    checkPage(cardHeight)

                    drawFormsCard(canvas, 40f, yPos, pageWidth - 80f, cardHeight - 15f)

                    paint.apply { textSize = 16f; isFakeBoldText = true; color = Color.BLACK }
                    canvas.drawText(item.subItens[i], 60f, yPos + 40f, paint)
                    drawStatusBadge(canvas, (pageWidth - 220).toFloat(), yPos + 15f, item.statusSubItens[i])

                    if (hasFoto && scaledSubBitmap != null) {
                        val xCentered = (pageWidth - subImgW) / 2f
                        canvas.drawBitmap(scaledSubBitmap!!, xCentered, yPos + 70f, null)
                    }
                    yPos += cardHeight
                }
            }

            // OUTROS ITENS
            checkPage(100f)
            paint.apply { textSize = 20f; isFakeBoldText = true; color = Color.parseColor("#666666") }
            canvas.drawText("OUTROS ITENS", 30f, yPos + 40f, paint)
            yPos += 60f

            for (item in itensSemFoto + itensSemFotoNA) {
                checkPage(80f)
                drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, 65f)
                paint.apply { textSize = 16f; isFakeBoldText = false; color = Color.BLACK }
                canvas.drawText(item.nome, 40f, yPos + 40f, paint)
                drawStatusBadge(canvas, (pageWidth - 180).toFloat(), yPos + 15f, item.status)
                yPos += 80f
            }

            // ITENS COM EXTRA
            for (item in itensComExtra) {
                var scaledBitmap: Bitmap? = null
                var imageWidth = 0f
                var imageHeight = 0f

                if (item.fotoPath != null) {
                    val original = BitmapFactory.decodeFile(item.fotoPath)
                    original?.let {
                        scaledBitmap = resizeForPdf(it, 600)
                        imageWidth = scaledBitmap!!.width.toFloat()
                        imageHeight = scaledBitmap!!.height.toFloat()
                    }
                }

                val hasFoto = scaledBitmap != null
                val extraInfo = if (item.valorExtra.isNotEmpty()) " (${item.valorExtra})" else ""
                val cardHeight = if (hasFoto) imageHeight + 120f else 110f
                checkPage(cardHeight)

                drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, cardHeight - 20f)

                paint.apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
                canvas.drawText("${item.nome}$extraInfo", 40f, yPos + 40f, paint)
                drawStatusBadge(canvas, (pageWidth - 180).toFloat(), yPos + 20f, item.status)

                if (hasFoto && scaledBitmap != null) {
                    val xCentered = (pageWidth - imageWidth) / 2f
                    canvas.drawBitmap(scaledBitmap!!, xCentered, yPos + 90f, null)
                }
                yPos += cardHeight
            }

            // Combustível
            checkPage(100f)
            drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, 70f)
            paint.apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
            canvas.drawText("Combustível: $nivelCombustivel", 40f, yPos + 45f, paint)
            yPos += 90f

            // Observações Finais
            if (observacoesFinais.isNotEmpty()) {
                val lines = observacoesFinais.split("\n")
                val cardHeight = 70f + (lines.size * 25f)
                checkPage(cardHeight + 20f)
                drawFormsCard(canvas, 20f, yPos, pageWidth - 40f, cardHeight)

                paint.apply { color = Color.parseColor("#666666"); isFakeBoldText = true; textSize = 16f }
                canvas.drawText("Observações Finais:", 40f, yPos + 40f, paint)

                paint.apply { color = Color.DKGRAY; isFakeBoldText = false; textSize = 14f }
                var internalY = yPos + 75f
                for (line in lines) {
                    canvas.drawText(line, 40f, internalY, paint)
                    internalY += 25f
                }
            }

            pdfDocument.finishPage(page)
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            return pdfFile
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Erro ao criar PDF", e)
            return null
        }
    }

    private fun drawFormsCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 2f, Color.argb(50, 0, 0, 0))
        }
        canvas.drawRoundRect(x, y, x + w, y + h, 10f, 10f, paint)

        paint.apply {
            color = Color.parseColor("#666666")
            setShadowLayer(0f, 0f, 0f, 0)
        }
        canvas.drawRect(x, y, x + 8f, y + h, paint)
    }

    private fun drawStatusBadge(canvas: Canvas, x: Float, y: Float, status: String) {
        val paint = Paint().apply { isAntiAlias = true }
        val color = when (status) {
            "OK" -> Color.parseColor("#4CAF50")
            "IRREGULAR" -> Color.parseColor("#F44336")
            "NAO_SE_APLICA" -> Color.parseColor("#FF9800")
            else -> Color.LTGRAY
        }

        paint.color = color
        canvas.drawRoundRect(x, y, x + 150f, y + 40f, 20f, 20f, paint)

        paint.apply {
            this.color = Color.WHITE
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val text = if (status == "NAO_SE_APLICA") "N/A" else status
        canvas.drawText(text, x + 75f, y + 25f, paint)
    }

    private fun resizeForPdf(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toDouble() / bitmap.width.toDouble()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}
