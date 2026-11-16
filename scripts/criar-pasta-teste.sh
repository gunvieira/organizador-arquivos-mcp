#!/usr/bin/env bash
# Script Bash para criar uma pasta temporária de testes populada com
# arquivos fictícios de todas as categorias, duplicados, conflitos de nome,
# arquivo com acento e 'ç' e arquivo vazio (0 bytes).
# NENHUM dado real do usuário é utilizado ou modificado.

set -euo pipefail

TARGET_DIR="${1:-${TMPDIR:-/tmp}/mcp-organizer-teste}"

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR/subpasta_antiga"
mkdir -p "$TARGET_DIR/Imagens"

# 1. Arquivos de todas as 9 categorias na raiz
printf 'IMG_DATA_%.0s' {1..50} > "$TARGET_DIR/foto_praia.jpg"
printf 'PSD_LAYER_%.0s' {1..120} > "$TARGET_DIR/banner_campanha.psd"
printf 'PDF_TEXTO_%.0s' {1..80} > "$TARGET_DIR/contrato_prestacao.pdf"
printf 'XLSX_CELL_%.0s' {1..60} > "$TARGET_DIR/orcamento_2026.xlsx"
printf 'PPTX_SLIDE_%.0s' {1..90} > "$TARGET_DIR/slides_defesa.pptx"
printf 'VIDEO_STREAM_%.0s' {1..500} > "$TARGET_DIR/aula_gravada.mp4"
printf 'AUDIO_FRAME_%.0s' {1..200} > "$TARGET_DIR/entrevista_podcast.mp3"
printf 'ZIP_ARCHIVE_%.0s' {1..300} > "$TARGET_DIR/backup_projeto.zip"
printf 'Arquivo sem extensao classificado em Outros.\n' > "$TARGET_DIR/LEIAME_SEM_EXTENSAO"

# 2. Dois arquivos duplicados (conteúdo idêntico em pastas diferentes)
printf 'CONTEUDO_100_PORCENTO_IDENTICO_PARA_TESTE_SHA256\n%.0s' {1..40} > "$TARGET_DIR/relatorio_original.pdf"
cp "$TARGET_DIR/relatorio_original.pdf" "$TARGET_DIR/subpasta_antiga/relatorio_copia_identica.pdf"

# 3. Dois arquivos com o MESMO NOME em pastas diferentes (para testar conflito nome (1).ext)
printf 'LOGO_JA_NA_PASTA_IMAGENS\n' > "$TARGET_DIR/Imagens/logo.png"
printf 'LOGO_SOLTO_NA_RAIZ_QUE_GERA_CONFLITO\n' > "$TARGET_DIR/logo.png"

# 4. Arquivo com acentuação e 'ç' no nome
printf 'Texto com acentuação: ç, ã, é, í, ó, ú.\n' > "$TARGET_DIR/declaração_de_matrícula_2026.txt"

# 5. Arquivo vazio (0 bytes)
: > "$TARGET_DIR/arquivo_vazio.txt"

# Ajusta datas de modificação
touch -t 202203151000 "$TARGET_DIR/subpasta_antiga/relatorio_copia_identica.pdf" 2>/dev/null || true
touch -t 202407201430 "$TARGET_DIR/orcamento_2026.xlsx" 2>/dev/null || true

echo "================================================================"
echo " Pasta temporária de teste criada com sucesso!"
echo " Caminho: $TARGET_DIR"
echo "================================================================"
echo "Para definir no terminal: export MCP_FILES_ROOT=\"$TARGET_DIR\""
