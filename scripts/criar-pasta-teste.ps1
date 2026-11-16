# Script PowerShell para criar uma pasta temporária de testes populada com
# arquivos fictícios de todas as categorias, duplicados, conflitos de nome,
# arquivo com acento e 'ç' e arquivo vazio (0 bytes).
# NENHUM dado real do usuário é utilizado ou modificado.

param(
   [string]$TargetDir = (Join-Path ([System.IO.Path]::GetTempPath()) "mcp-organizer-teste")
)

$ErrorActionPreference = "Stop"

if (Test-Path -LiteralPath $TargetDir) {
   Remove-Item -LiteralPath $TargetDir -Recurse -Force
}

New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $TargetDir "subpasta_antiga") -Force | Out-Null

# 1. Arquivos de todas as 9 categorias na raiz
[System.IO.File]::WriteAllText((Join-Path $TargetDir "foto_praia.jpg"), ("IMG_DATA_" * 50), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "banner_campanha.psd"), ("PSD_LAYER_" * 120), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "contrato_prestacao.pdf"), ("PDF_TEXTO_" * 80), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "orcamento_2026.xlsx"), ("XLSX_CELL_" * 60), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "slides_defesa.pptx"), ("PPTX_SLIDE_" * 90), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "aula_gravada.mp4"), ("VIDEO_STREAM_" * 500), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "entrevista_podcast.mp3"), ("AUDIO_FRAME_" * 200), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "backup_projeto.zip"), ("ZIP_ARCHIVE_" * 300), [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "LEIAME_SEM_EXTENSAO"), "Arquivo sem extensao classificado em Outros.", [System.Text.Encoding]::UTF8)

# 2. Dois arquivos duplicados (conteúdo idêntico em locais/nomes diferentes)
$conteudoDuplicado = "CONTEUDO_100_PORCENTO_IDENTICO_PARA_TESTE_SHA256`n" * 40
[System.IO.File]::WriteAllText((Join-Path $TargetDir "relatorio_original.pdf"), $conteudoDuplicado, [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "subpasta_antiga\relatorio_copia_identica.pdf"), $conteudoDuplicado, [System.Text.Encoding]::UTF8)

# 3. Dois arquivos com o MESMO NOME em pastas diferentes (para testar conflito nome (1).ext ao organizar recursivamente
#    ou quando a pasta de destino já possui um arquivo de mesmo nome)
New-Item -ItemType Directory -Path (Join-Path $TargetDir "Imagens") -Force | Out-Null
[System.IO.File]::WriteAllText((Join-Path $TargetDir "Imagens\logo.png"), "LOGO_JA_NA_PASTA_IMAGENS", [System.Text.Encoding]::UTF8)
[System.IO.File]::WriteAllText((Join-Path $TargetDir "logo.png"), "LOGO_SOLTO_NA_RAIZ_QUE_GERA_CONFLITO", [System.Text.Encoding]::UTF8)

# 4. Arquivo com acentuação e 'ç' no nome
[System.IO.File]::WriteAllText((Join-Path $TargetDir "declaração_de_matrícula_2026.txt"), "Texto com acentuação: ç, ã, é, í, ó, ú.", [System.Text.Encoding]::UTF8)

# 5. Arquivo vazio (0 bytes) — deve ser ignorado pelo detector de duplicados
New-Item -ItemType File -Path (Join-Path $TargetDir "arquivo_vazio.txt") -Force | Out-Null

# Ajusta datas de modificação para testar listar_mais_antigos e filtro modified_after/before
(Get-Item (Join-Path $TargetDir "subpasta_antiga\relatorio_copia_identica.pdf")).LastWriteTime = [datetime]"2022-03-15 10:00:00"
(Get-Item (Join-Path $TargetDir "orcamento_2026.xlsx")).LastWriteTime = [datetime]"2024-07-20 14:30:00"

Write-Host "================================================================" -ForegroundColor Green
Write-Host " Pasta temporária de teste criada com sucesso!" -ForegroundColor Green
Write-Host " Caminho: $TargetDir" -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Green
Write-Host 'Para definir no PowerShell: $env:MCP_FILES_ROOT = "' -NoNewline
Write-Host $TargetDir -NoNewline -ForegroundColor Cyan
Write-Host '"'
