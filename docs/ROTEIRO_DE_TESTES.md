# 🧪 Roteiro Completo de Testes — Organizador de Arquivos MCP

Este documento descreve o passo a passo para validar todas as funcionalidades, casos de erro e integrações do projeto **Organizador de Arquivos MCP** (`br.edu.ifpr:mcp_organizer`).

---

## 1. Pré-requisitos e Verificação do Ambiente

Abra um terminal (PowerShell ou Prompt de Comando) e confira as versões instaladas:

```powershell
java -version   # Requer Java 25 (ex.: build 25+37-LTS)
mvn -v          # Requer Apache Maven 3.9+ rodando sobre o JDK 25
node -v         # Opcional: Node.js 20+ / 22+ para rodar o MCP Inspector via npx
npx -v
docker -v       # Opcional: Docker Desktop para rodar o MCP Inspector via Docker Compose
```

---

## 2. Geração da Pasta Temporária de Teste

Nunca execute testes manuais sobre suas pastas pessoais. O projeto inclui scripts em `scripts/` que criam uma pasta temporária isolada (`%TEMP%\mcp-organizer-teste` no Windows ou `/tmp/mcp-organizer-teste` no Linux/macOS) contendo:
- Arquivos fictícios das **9 categorias** (`Imagens`, `Design`, `Documentos`, `Planilhas`, `Apresentacoes`, `Videos`, `Audios`, `Compactados`, `Outros`);
- **Dois arquivos duplicados** (`relatorio_original.pdf` e `subpasta_antiga/relatorio_copia_identica.pdf`) com exatamente o mesmo conteúdo SHA-256;
- **Dois arquivos com o mesmo nome em pastas diferentes** (`logo.png` na raiz e `Imagens/logo.png`) para exercitar a política de conflito de nomes (`logo (1).png`);
- **Um arquivo com acentuação e `ç` no nome** (`declaração_de_matrícula_2026.txt`);
- **Um arquivo vazio (`0 bytes`)** (`arquivo_vazio.txt`), que deve ser ignorado pela detecção de duplicados.

### No Windows (PowerShell):
Na raiz do projeto (`organizador-arquivos-mcp`), execute:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\criar-pasta-teste.ps1
```

### No Linux / macOS / Git Bash:
```bash
bash ./scripts/criar-pasta-teste.sh
```

---

## 3. Build e Testes Automatizados (Unitários e Integração)

Execute os comandos abaixo na raiz do projeto (`organizador-arquivos-mcp`), nesta ordem exata:

```bash
# 1. Executa os 88 testes unitários (*Test.java) via maven-surefire-plugin
mvn clean test

# 2. Gera o JAR do servidor em server/mcp_organizer_server.jar (+ server/libs/)
mvn clean package -Pserver -DskipTests

# 3. Gera o JAR do cliente em target/dist-client/mcp_organizer_client.jar (+ target/dist-client/libs/)
mvn clean package -Pclient -DskipTests

# 4. Executa os testes de integração (*IT.java) contra o JAR real do servidor via maven-failsafe-plugin
mvn clean verify -Pserver
```

> ⚠️ **Atenção:** O comando `mvn clean verify -Pserver` executa o goal `clean`, que apaga a pasta `target/` (onde fica `target/dist-client/mcp_organizer_client.jar`). Por isso, antes de iniciar o teste manual do cliente próprio na seção 4 abaixo, rode novamente:
> ```bash
> mvn package -Pclient -DskipTests
> ```

---

## 4. Teste Manual com o Cliente MCP Próprio (`McpOrganizerClient`)

Conforme definido no `pom.xml` (perfil `client`), o JAR executável do cliente é gerado em:
`target/dist-client/mcp_organizer_client.jar` (e espera encontrar o servidor em `../../server/mcp_organizer_server.jar` ou `server/mcp_organizer_server.jar`).

### 4.1 Como iniciar o cliente

Defina a variável `MCP_FILES_ROOT` apontando para a pasta temporária criada no item 2 e execute o cliente:

**No PowerShell:**
```powershell
$env:MCP_FILES_ROOT = Join-Path ([System.IO.Path]::GetTempPath()) "mcp-organizer-teste"
cd target\dist-client
java -jar mcp_organizer_client.jar
```

**No Prompt de Comando (CMD):**
```cmd
set MCP_FILES_ROOT=%TEMP%\mcp-organizer-teste
cd target\dist-client
java -jar mcp_organizer_client.jar
```

*(Alternativamente, passe o caminho direto por argumento `java -jar mcp_organizer_client.jar "%TEMP%\mcp-organizer-teste"` ou propriedade `java -Dmcp.files.root="%TEMP%\mcp-organizer-teste" -jar mcp_organizer_client.jar`).*

### 4.2 Sequência de Execução no Menu Interativo

Escolha **`1 - Ferramentas (Tools)`** no menu principal e execute na sequência:

1. **`auth_path`:**
   - Parâmetros: *(nenhum)*.
   - **Verificação:** Retorna o caminho absoluto de `mcp-organizer-teste\`.
2. **`escanear_pasta`:**
   - `path`: *(deixe em branco para usar `.`)*
   - `recursive`: `true`
   - **Verificação:** Exibe o total de arquivos (14 arquivos), o espaço ocupado em cada uma das 9 categorias e a amostra de arquivos (incluindo `declaração_de_matrícula_2026.txt`).
3. **`buscar_arquivos`:**
   - `name`: `*.pdf` · `category`: `Documentos` · demais opcionais em branco · `recursive`: `true` · `max_results`: `20`
   - **Verificação:** Lista os 3 arquivos PDF (`contrato_prestacao.pdf`, `relatorio_original.pdf` e `subpasta_antiga/relatorio_copia_identica.pdf`).
4. **`encontrar_duplicados`:**
   - `path`: *(em branco)* · `recursive`: `true` · `min_size`: `1`
   - **Verificação:** Encontra **1 grupo de duplicados** (`relatorio_original.pdf` e `subpasta_antiga/relatorio_copia_identica.pdf`), informa o espaço desperdiçado e **ignora** `arquivo_vazio.txt` (0 bytes).
5. **`listar_maiores`:**
   - `path`: *(em branco)* · `limit`: `5` · `recursive`: `true`
   - **Verificação:** Lista os 5 maiores arquivos em ordem decrescente (`1. aula_gravada.mp4`, `2. backup_projeto.zip`, etc.).
6. **`listar_mais_antigos`:**
   - `path`: *(em branco)* · `limit`: `5` · `recursive`: `true`
   - **Verificação:** Lista primeiro `subpasta_antiga/relatorio_copia_identica.pdf` (`2022-03-15`) seguido de `orcamento_2026.xlsx` (`2024-07-20`).
7. **`sugerir_organizacao`:**
   - `path`: *(em branco)* · `recursive`: `false`
   - **Verificação:** Retorna um `plan_id` (UUID) e a lista de movimentos propostos para os arquivos soltos na raiz (ignorando `Imagens/logo.png` que já está organizado). **Confira no disco que nenhum arquivo foi movido ainda.** Copie o `plan_id`.
8. **`aplicar_organizacao`:**
   - `plan_id`: *(cole o UUID copiado no passo anterior)*
   - **Verificação:** Retorna `status CONCLUIDO`, informa os arquivos movidos e destaca **1 arquivo renomeado por conflito**: `logo.png -> Imagens/logo (1).png` (pois `Imagens/logo.png` já existia).
   - **Confira no disco:** Os arquivos da raiz foram movidos para `Imagens/`, `Design/`, `Documentos/`, `Planilhas/`, `Apresentacoes/`, `Videos/`, `Audios/`, `Compactados/` e `Outros/`, e tanto `Imagens/logo.png` quanto `Imagens/logo (1).png` existem intactos.
9. **`desfazer_organizacao`:**
   - `batch_id`: *(deixe em branco para reverter o último lote)*
   - **Verificação:** Todos os arquivos movidos retornam aos seus caminhos originais na raiz em ordem inversa (inclusive `Imagens/logo (1).png` volta a ser `logo.png` na raiz).

---

## 5. Teste com o MCP Inspector (`npx` e Docker Compose)

### 5.1 Opção A — Via `npx` (Local)
Na raiz do projeto (`organizador-arquivos-mcp`), com o servidor já empacotado (`server/mcp_organizer_server.jar`):

```powershell
$TestDir = Join-Path ([System.IO.Path]::GetTempPath()) "mcp-organizer-teste"
npx @modelcontextprotocol/inspector -e MCP_FILES_ROOT="$TestDir" java -jar server/mcp_organizer_server.jar
```

### 5.2 Opção B — Via Docker Compose
1. Crie um arquivo `.env` na raiz do projeto (baseado no `.env.example`) apontando `MCP_FILES_HOST` para a pasta temporária criada:
   ```env
   MCP_FILES_HOST=C:/Users/SEU_USUARIO/AppData/Local/Temp/mcp-organizer-teste
   MCP_CONTAINER=/opt/mcp
   MCP_FILES_CONTAINER=${MCP_CONTAINER}/files
   ```
2. Suba o contêiner:
   ```bash
   docker compose up -d --build mcp-inspector
   ```
3. Abra `http://127.0.0.1:6274` no navegador.

### 5.3 Passos dentro da interface web do MCP Inspector
1. Clique em **Connect** no painel lateral esquerdo.
2. Vá na aba **Tools** → clique em **List Tools** (devem aparecer as 9 tools) → selecione `escanear_pasta` (`{"recursive": true}`) e clique em **Run Tool**; depois selecione `encontrar_duplicados` (`{"recursive": true, "min_size": 1}`) e clique em **Run Tool**.
3. Vá na aba **Resources** → clique em **List Resources** (devem aparecer os 5 resources `app://...`) → clique em `app://estatisticas` e `app://regras-classificacao` para visualizar seus conteúdos.
4. Vá na aba **Prompts** → clique em **List Prompts** (devem aparecer os 3 prompts) → selecione `organizar_pasta` ou `encontrar_arquivo` (`descricao = "planilhas de orçamento"`), clique em **Get Prompt** e confira a instrução gerada.

---

## 6. Teste no Claude Desktop

1. Edite `%APPDATA%\Claude\claude_desktop_config.json` (ou o caminho MSIX equivalente):
   ```json
   {
     "mcpServers": {
       "organizador_arquivos": {
         "command": "java",
         "args": [
           "-jar",
           "C:\\Users\\meta\\Desktop\\Romualdo\\organizador-arquivos-mcp\\server\\mcp_organizer_server.jar"
         ],
         "env": {
           "MCP_FILES_ROOT": "C:\\Users\\meta\\AppData\\Local\\Temp\\mcp-organizer-teste"
         }
       }
     }
   }
   ```
2. **Reinicie o Claude Desktop por completo:** feche a janela, clique com o botão direito no ícone do Claude na bandeja do sistema (perto do relógio) → **Quit / Sair** (ou encerre pelo Gerenciador de Tarefas) e abra o aplicativo novamente.
3. Teste com as **3 frases abaixo**:
   - **Frase 1:** *"Use o prompt organizar_pasta para analisar a pasta raiz e me mostre o plano de organização antes de mover qualquer arquivo."*
   - **Frase 2:** *"Ache arquivos duplicados e me liste os maiores arquivos para dizer quanto espaço dá para liberar (lembre-se de não apagar nada)."*
   - **Frase 3:** *"Aplique o plano de organização gerado e, em seguida, desfaça a última organização para voltar tudo ao estado original."*

---

## 7. Casos de Erro Obrigatórios a Verificar

Execute os cenários abaixo (no Cliente Próprio ou no MCP Inspector) para confirmar que todos retornam `Erro: Sim` (`isError = true`) com mensagem amigável em português e sem stack trace:

1. **Travessia de diretório (`path` com `../`):** chamar `escanear_pasta` com `{"path": "../"}` → bloqueado pelo `SandboxPathResolver`.
2. **Caminho absoluto:** chamar `escanear_pasta` com `{"path": "C:\\Windows"}` ou `{"path": "/etc"}` → bloqueado pelo `SandboxPathResolver`.
3. **Acesso à pasta reservada `.mcp-organizer`:** chamar `escanear_pasta` com `{"path": ".mcp-organizer"}` → bloqueado.
4. **`plan_id` inexistente:** chamar `aplicar_organizacao` com `{"plan_id": "id-invalido-000"}` → erro informando que o plano não existe.
5. **Aplicar o mesmo plano duas vezes:** chamar `aplicar_organizacao` repetindo um `plan_id` já aplicado → erro informando que cada plano só pode ser aplicado uma única vez.
6. **Desfazer sem histórico pendente:** chamar `desfazer_organizacao` após já ter desfeito todos os lotes → erro informando que não há histórico pendente para desfazer.
7. **`max_results` ou `limit` fora do intervalo `1..100`:** chamar `buscar_arquivos` com `{"max_results": 0}` ou `{"max_results": 101}` → erro de validação.
8. **Data inválida ou intervalo invertido:** chamar `buscar_arquivos` com `{"modified_after": "31-02-2026"}` ou `{"min_size": 500, "max_size": 10}` → erro de validação.
9. **Conflito de nome no destino:** aplicar plano quando `Imagens/logo.png` já existe → não retorna erro nem sobrescreve; move como `Imagens/logo (1).png`.

---

## 8. Tabela Resumo de Resultados Esperados

| Passo | Comando / Ação | Resultado Esperado |
|---|---|---|
| **1. Testes Unitários** | `mvn clean test` | `Tests run: 88, Failures: 0, Errors: 0` (`BUILD SUCCESS`) em diretórios `@TempDir`. |
| **2. Build Servidor** | `mvn clean package -Pserver -DskipTests` | Cria `server/mcp_organizer_server.jar` e `server/libs/*.jar`. |
| **3. Build Cliente** | `mvn package -Pclient -DskipTests` | Cria `target/dist-client/mcp_organizer_client.jar` e `target/dist-client/libs/*.jar`. |
| **4. Testes Integração** | `mvn clean verify -Pserver` | Sobe o JAR real via STDIO e passa nos 9 testes de `McpOrganizerServerIT` (`BUILD SUCCESS`). |
| **5. `auth_path`** | Executar tool sem argumentos | `isError=false`; exibe caminho absoluto da pasta temporária terminado em `\`. |
| **6. `escanear_pasta`** | `{"path": ".", "recursive": true}` | `isError=false`; exibe total de arquivos, bytes por categoria e amostra (incluindo nome com `ç` e acentos). |
| **7. `buscar_arquivos`** | `{"name": "*.pdf", "category": "Documentos"}` | `isError=false`; lista apenas os PDFs com tamanho, data UTC e categoria. |
| **8. `encontrar_duplicados`** | `{"recursive": true, "min_size": 1}` | `isError=false`; agrupa `relatorio_original.pdf` e `relatorio_copia_identica.pdf`, calcula bytes desperdiçados e ignora `arquivo_vazio.txt`. |
| **9. `listar_maiores`** | `{"limit": 5, "recursive": true}` | `isError=false`; lista os 5 maiores arquivos em ordem decrescente de bytes. |
| **10. `listar_mais_antigos`** | `{"limit": 5, "recursive": true}` | `isError=false`; lista primeiro o arquivo de `2022-03-15`, depois o de `2024-07-20`. |
| **11. `sugerir_organizacao`** | `{"recursive": false}` | `isError=false`; retorna `plan_id` UUID e movimentos propostos. Nenhum arquivo é movido no disco. |
| **12. `aplicar_organizacao`** | `{"plan_id": "<UUID>"}` | `isError=false`; move arquivos para as pastas das categorias e resolve conflito criando `Imagens/logo (1).png`. |
| **13. `desfazer_organizacao`** | `{}` | `isError=false`; reverte o lote em ordem inversa, restaurando os arquivos na raiz. |
| **14. Casos de Erro** | `path="../"`, `plan_id` repetido, `max_results=200`, data inválida | `isError=true`; mensagem clara em português sem stack trace e sem alterar arquivos. |
