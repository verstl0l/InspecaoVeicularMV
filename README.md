# InspecaoVeicularMV 🚗📋

Um aplicativo Android profissional para vistorias e inspeções veiculares, desenvolvido em Kotlin com arquitetura moderna.

## 🚀 Funcionalidades

- **Checklist Completo:** Itens de vistoria simples, múltiplos (pneus, lataria) e com campos extras (validade de extintor, KM de troca de óleo).
- **Relatório em PDF:** Geração de laudos profissionais com layout inspirado no Google Forms, incluindo fotos grandes, badges de status coloridos e resumo de dados.
- **Histórico de Vistorias:** Banco de dados local (Room) para salvar, editar e excluir inspeções realizadas.
- **Visualizador de Fotos:** Toque nas miniaturas para visualizar as fotos em tamanho real antes de gerar o laudo.
- **Salvamento Automático:** Sistema de rascunho inteligente que salva o progresso em tempo real.
- **Validação de Itens:** Bloqueio de geração de PDF caso faltem fotos ou campos obrigatórios.

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** [Kotlin](https://kotlinlang.org/)
- **Arquitetura:** MVVM (parcial) / Modularização de PDF
- **Banco de Dados:** [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- **Carregamento de Imagem:** [Coil](https://coil-kt.github.io/coil/)
- **PDF:** Android PdfDocument API
- **View Binding:** Para manipulação segura da UI.
- **JSON:** [Gson](https://github.com/google/gson) para serialização de dados complexos.

## 📸 Screenshots
*(Adicione aqui os caminhos das imagens do app após subir para o GitHub)*

## 📦 Como Instalar

1. Clone o repositório:
   ```bash
   git clone https://github.com/SEU_USUARIO/InspecaoVeicularMV.git
   ```
2. Abra no **Android Studio**.
3. Sincronize o Gradle.
4. Execute em um dispositivo físico ou emulador (API 26+).

## 📄 Licença
Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para detalhes.

---
Desenvolvido por [Seu Nome]
