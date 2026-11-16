# 🗂️ Organizador de Arquivos MCP — Servidor e Cliente em Java

Este projeto implementa um **Servidor MCP** e um **Cliente MCP** em um único projeto **Apache Maven** utilizando **Java 25** e o [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) (`io.modelcontextprotocol.sdk`), com comunicação síncrona via transporte **STDIO** (`McpServer.sync` / `McpClient.sync`).

O servidor permite que um assistente de IA (como o **Claude Desktop**), o **MCP Inspector** ou o **cliente interativo próprio** escaneie, pesquise, identifique arquivos duplicados, liste os maiores/mais antigos arquivos e organize automaticamente pastas bagunçadas em categorias, com **plano prévio obrigatório**, **journal de execução** e **reversão segura (undo)** — sem jamais apagar ou sobrescrever arquivos.

---

## 📋 Sumário

1. [Visão Geral e Arquitetura](#-visão-geral-e-arquitetura)
2. [Modelo de Segurança (Sandbox)](#-modelo-de-segurança-sandbox)
3. [Categorias de Classificação e Política de Conflito de Nomes](#-categorias-de-classificação-e-política-de-conflito-de-nomes)
4. [Ferramentas (Tools)](#-ferramentas-tools)
5. [Recursos (Resources)](#-recursos-resources)
6. [Prompts](#-prompts)
7. [Padrões de Projeto e Boas Práticas OO](#-padrões-de-projeto-e-boas-práticas-oo)
8. [Como Compilar e Executar os Testes (CLI e Eclipse IDE)](#-como-compilar-e-executar-os-testes-cli-e-eclipse-ide)
9. [Como Executar o Cliente MCP Próprio](#-como-executar-o-cliente-mcp-próprio)
10. [Como Testar no MCP Inspector (Docker Compose e npx)](#-como-testar-no-mcp-inspector-docker-compose-e-npx)
11. [Como Configurar no Claude Desktop](#-como-configurar-no-claude-desktop)

---

## 🏗️ Visão Geral e Arquitetura

O projeto está estruturado sob o pacote base `br.edu.ifpr.mcp_organizer`:

```text
br.edu.ifpr.mcp_organizer
 ├─ main/
 │   ├─ AppConsts.java              # Constantes de propriedade (mcp.files.root) e variável (MCP_FILES_ROOT)
 │   ├─ ServerComposition.java      # Composição manual de dependências (DI por construtor)
 │   ├─ McpOrganizerServer.java     # Aplicação Servidor MCP (STDIO síncrono)
 │   └─ McpOrganizerClient.java     # Aplicação Cliente MCP interativa (descoberta dinâmica)
 ├─ mcp/
 │   ├─ tools/                      # 9 classes de Tools (camada fina de protocolo)
 │   ├─ resources/                  # 5 classes de Resources (app://...)
 │   ├─ prompts/                    # 3 classes de Prompts parametrizados
 │   └─ utils/                      # Interfaces Mcp*Definition, McpConsts e helper ToolResults
 ├─ services/
 │   ├─ security/                   # SandboxPathResolver (validação de caminhos e bloqueio de symlinks/traversal)
 │   ├─ scan/                       # FileScanner e record FileInfo
 │   ├─ classification/             # FileClassifier, ExtensionClassifier, ClassificationRules, ClassifierRegistry
 │   ├─ search/                     # FileCriteria (Builder), FileSpecification (Specification) e FileSearchService
 │   ├─ duplicates/                 # HashStrategy, Sha256HashStrategy e DuplicateFinderService
 │   └─ organizer/                  # OrganizationPlanner, OrganizationExecutor, UndoService, ConflictResolver,
 │                                  # FileCommand, MoveFileCommand, OrganizationRepository, JsonFileOrganizationRepository
 └─ utils/json/schema/
     └─ SchemaLoader.java           # Leitor de arquivos JSON Schema do classpath via Jackson 3
```

---

## 🔒 Modelo de Segurança (Sandbox)

Todas as operações do servidor obedecem às seguintes garantias:

1. **Diretório Raiz Autorizado (`MCP_FILES_ROOT` / `-Dmcp.files.root`):** O servidor só inicia se o diretório raiz estiver configurado, existir no disco, for um diretório real e não for link simbólico. Caso contrário, falha imediatamente gravando erro em `stderr`.
2. **Isolamento via `SandboxPathResolver`:** Todos os serviços validam os caminhos recebidos através de `SandboxPathResolver`:
   - Rejeita caminhos absolutos (`/etc/...`, `C:\...`, `\\...`);
   - Rejeita navegação para diretório pai (`..`);
   - Normaliza e resolve o caminho real (`toRealPath(LinkOption.NOFOLLOW_LINKS)`) garantindo que permaneça estritamente dentro da raiz autorizada;
   - Rejeita links simbólicos (`NOFOLLOW_LINKS`) em qualquer nível do caminho;
   - Bloqueia acesso direto e ignora nos escaneamentos a pasta interna `<raiz>/.mcp-organizer/` (onde ficam `plans.json` e `history.json`).
3. **Sem Exclusão de Arquivos:** Não existe nenhuma ferramenta capaz de apagar arquivos. O servidor apenas consulta ou move arquivos dentro da raiz (com suporte a desfazer).
4. **Limites de Varredura:** Máximo de **20.000 entradas** examinadas por operação, profundidade máxima de **32 níveis** e limite de resultados entre **1 e 100** (com aviso explícito no texto de retorno quando qualquer limite é atingido).
5. **Logs Exclusivos em `stderr`:** Configurado em `src/main/resources/logback.xml` (`<target>System.err</target>`) para jamais poluir o canal `stdout` utilizado pelo protocolo JSON-RPC do MCP.

---

## 📂 Categorias de Classificação e Política de Conflito de Nomes

### Categorias Padrão (`src/main/resources/classification-rules.json`)
A classificação é feita pela extensão do arquivo (sem diferenciar maiúsculas de minúsculas):

| Categoria | Extensões |
|---|---|
| `Imagens` | `jpg`, `jpeg`, `png`, `gif`, `webp`, `bmp`, `svg`, `heic` |
| `Design` | `psd`, `ai`, `xcf`, `indd`, `sketch`, `fig`, `cdr` |
| `Documentos` | `pdf`, `doc`, `docx`, `odt`, `txt`, `rtf`, `md` |
| `Planilhas` | `xls`, `xlsx`, `ods`, `csv` |
| `Apresentacoes` | `ppt`, `pptx`, `odp` |
| `Videos` | `mp4`, `mkv`, `avi`, `mov`, `webm` |
| `Audios` | `mp3`, `wav`, `flac`, `ogg`, `m4a` |
| `Compactados` | `zip`, `rar`, `7z`, `tar`, `gz` |
| `Outros` | Arquivos sem extensão ou com extensão não listada acima |

### Política de Conflito de Nomes (Nunca Sobrescrever)
- **Ao aplicar uma organização (`aplicar_organizacao`):** O servidor **nunca sobrescreve** um arquivo já existente na pasta de destino. Se `<Categoria>/arquivo.ext` já existir, o `ConflictResolver` gera automaticamente um nome livre acrescentando sufixo numérico antes da extensão: `arquivo (1).ext`, `arquivo (2).ext`, `arquivo (3).ext` (ou `README (1)` para arquivos sem extensão). O caminho efetivo (`actualTargetRelativePath`) é gravado no journal para que o `desfazer_organizacao` saiba exatamente qual arquivo reverter.
- **Ao desfazer uma organização (`desfazer_organizacao`):** Os movimentos do lote são revertidos em **ordem inversa**. Se o caminho de origem original tiver sido ocupado por um novo arquivo criado após a organização, o servidor **não sobrescreve** o arquivo: ele pula a reversão daquele item e reporta o conflito detalhadamente na resposta.

---

## 🧰 Ferramentas (Tools)

Os esquemas JSON de entrada ficam em `src/main/resources/json-schemas/`. Todas as ferramentas retornam `TextContent` em português e sinalizam `isError = true` em caso de erro de validação ou execução.

| Tool | Parâmetros | Exemplo de Entrada | Resultado / Saída | Erros Possíveis (`isError=true`) |
|---|---|---|---|---|
| `auth_path` | *(nenhum)* | `{}` | Caminho absoluto da raiz autorizada terminado com separador (`\`). | Raiz não configurada. |
| `escanear_pasta` | `path` *(string, opc., padrão `.`)*<br>`recursive` *(boolean, opc., padrão `true`)* | `{"path": ".", "recursive": true}` | Total de arquivos, bytes totais, contagem e espaço por categoria, amostra de até 15 arquivos e avisos de limite. | Pasta inexistente, caminho fora da raiz (`../`), caminho absoluto ou que aponte para arquivo regular. |
| `buscar_arquivos` | `name` *(glob, opc.)*<br>`category` *(string, opc.)*<br>`extensions` *(array de strings, opc.)*<br>`min_size` *(int >= 0, opc.)*<br>`max_size` *(int >= 0, opc.)*<br>`modified_after` *(ISO `YYYY-MM-DD`, opc.)*<br>`modified_before` *(ISO `YYYY-MM-DD`, opc.)*<br>`recursive` *(boolean, padrão `true`)*<br>`max_results` *(1..100, padrão `20`)* | `{"name": "*.pdf", "category": "Documentos", "min_size": 100, "max_results": 20}` | Lista formatada `caminho \| tamanho \| modificado \| categoria` e aviso se truncado por `max_results`. | `min_size > max_size`, `modified_after > modified_before`, data mal formada, categoria inexistente, `max_results` fora de `1..100`, padrão `name` contendo `/` ou `\`. |
| `encontrar_duplicados` | `path` *(string, opc., padrão `.`)*<br>`recursive` *(boolean, padrão `true`)*<br>`min_size` *(int >= 1, padrão `1`)* | `{"path": ".", "recursive": true, "min_size": 1}` | Pipeline em 3 etapas (tamanho → hash parcial 64 KB → SHA-256 completo). Lista grupos de duplicados e o **espaço total desperdiçado**. Ignora arquivos de 0 bytes. | Pasta inexistente/fora da raiz, `min_size < 0`. |
| `listar_maiores` | `path` *(string, opc., padrão `.`)*<br>`limit` *(1..100, padrão `10`)*<br>`recursive` *(boolean, padrão `true`)* | `{"limit": 10, "recursive": true}` | Lista numerada dos maiores arquivos em ordem decrescente de tamanho. | Pasta inexistente/fora da raiz, `limit` fora de `1..100`. |
| `listar_mais_antigos` | `path` *(string, opc., padrão `.`)*<br>`limit` *(1..100, padrão `10`)*<br>`recursive` *(boolean, padrão `true`)* | `{"limit": 10, "recursive": true}` | Lista numerada dos arquivos mais antigos por data de modificação (ordem crescente). | Pasta inexistente/fora da raiz, `limit` fora de `1..100`. |
| `sugerir_organizacao` | `path` *(string, opc., padrão `.`)*<br>`recursive` *(boolean, padrão `false`)* | `{"path": ".", "recursive": false}` | Gera e persiste um plano com `plan_id` (UUID) e a lista de movimentos `origem -> <Categoria>/<arquivo>`. Ignora arquivos que já estão na pasta da categoria correta. **Não move nada.** | Pasta inexistente, fora da raiz ou não é diretório. |
| `aplicar_organizacao` | `plan_id` *(string UUID, **obrigatório**)* | `{"plan_id": "550e8400-e29b-41d4-a716-446655440000"}` | Revalida caminhos, grava journal (`PENDENTE` → `CONCLUIDO`), resolve conflitos (`nome (1).ext`) e move os arquivos. Retorna `batch_id`, total movido e renomeados. | `plan_id` ausente ou inexistente, plano já aplicado anteriormente, ou falha parcial no meio do lote (para imediatamente, marca como `PARCIAL` e informa o que já foi movido). |
| `desfazer_organizacao` | `batch_id` *(string UUID, opcional)* | `{}` | Reverte em **ordem inversa** o último lote aplicado e ainda não desfeito (ou o `batch_id` informado), marcando-o como `DESFEITO`. Não sobrescreve locais originais ocupados. | Sem histórico pendente para desfazer, `batch_id` inexistente ou lote já desfeito. |

---

## 📚 Recursos (Resources)

| URI | Nome | Descrição |
|---|---|---|
| `app://about` | `about` | Nome, versão e descrição do servidor MCP. |
| `app://root-info` | `root-info` | Caminho do diretório raiz autorizado, se é diretório e se está habilitado para leitura. |
| `app://estatisticas` | `estatisticas` | Estatísticas completas da raiz autorizada: total de arquivos e espaço ocupado por categoria. |
| `app://historico` | `historico` | Histórico de lotes de organização (`batch_id`, `plan_id`, status `CONCLUIDO`/`PARCIAL`/`DESFEITO`, datas e quantidade de movimentos). |
| `app://regras-classificacao` | `regras-classificacao` | Categoria padrão (`Outros`) e mapa completo de categorias e extensões em uso. |

---

## 🪄 Prompts

| Prompt | Argumentos | Comportamento / Instruções Geradas |
|---|---|---|
| `organizar_pasta` | `pasta` *(opcional, padrão `.`)* | Instrui a IA a executar `escanear_pasta` → `sugerir_organizacao` → **mostrar o plano ao usuário e aguardar confirmação explícita** → só então chamar `aplicar_organizacao`. |
| `liberar_espaco` | `pasta` *(opcional, padrão `.`)* | Instrui a IA a combinar `listar_maiores` + `encontrar_duplicados`, calcular o espaço recuperável e sugerir o que revisar manualmente. **Reforça que a IA nunca deve apagar nada.** |
| `encontrar_arquivo` | `descricao` *(**obrigatório**)* | Traduz a descrição em linguagem natural para os filtros da tool `buscar_arquivos` (`name`, `category`, `extensions`, `min_size`, `max_size`, `modified_after`, `modified_before`). Lança `IllegalArgumentException` se `descricao` estiver ausente. |

---

## 🧩 Padrões de Projeto e Boas Práticas OO

- **Strategy:**
  - `FileClassifier` (`ExtensionClassifier`): permite variar o algoritmo de classificação de arquivos.
  - `HashStrategy` (`Sha256HashStrategy`): encapsula o cálculo de hash parcial (64 KB) e completo (SHA-256) usado por `DuplicateFinderService`.
- **Factory / Registry:**
  - `ClassifierRegistry`: registra e fornece instâncias de `FileClassifier` por chave (`extension`).
- **Command:**
  - `FileCommand` e `MoveFileCommand`: encapsulam a movimentação de arquivo com métodos `execute()` e `undo()`, garantindo que nenhuma operação use `REPLACE_EXISTING`.
- **Builder + Specification:**
  - `FileCriteria.Builder`: construção legível e imutável dos filtros opcionais de busca.
  - `FileSpecification`: predicados componíveis (`and`) para nome glob, categoria, extensões, tamanhos e datas ISO.
- **Repository:**
  - `OrganizationRepository` e `JsonFileOrganizationRepository`: isolam a persistência de planos (`plans.json`) e histórico/journal (`history.json`) em `<raiz>/.mcp-organizer/`.
- **SOLID, DRY, KISS, YAGNI:**
  - **SRP:** Tools finas apenas extraem argumentos e delegam aos Services; cada Service tem uma única responsabilidade.
  - **OCP:** Adicionar uma nova tool, resource ou prompt exige apenas criar a classe implementando `Mcp*Definition` e adicioná-la à lista em `ServerComposition`.
  - **DIP / Injeção por Construtor:** Todas as dependências (incluindo `Path root` e `SandboxPathResolver`) são injetadas via construtor em `ServerComposition`, sem chamadas estáticas ocultas dentro das tools/resources.
  - **DRY:** `ToolResults` centraliza a construção de `CallToolResult` de sucesso e erro.

---

## ⚙️ Como Compilar e Executar os Testes (CLI e Eclipse IDE)

### 1. Via Linha de Comando (CLI)

Execute os comandos abaixo a partir da raiz do projeto (`organizador-arquivos-mcp`), respeitando a ordem:

```bash
# 1. Executar todos os testes unitários (*Test.java)
mvn clean test

# 2. Gerar o pacote do SERVIDOR em server/mcp_organizer_server.jar (+ server/libs/)
mvn clean package -Pserver -DskipTests

# 3. Gerar o pacote do CLIENTE em target/dist-client/mcp_organizer_client.jar (+ target/dist-client/libs/)
mvn clean package -Pclient -DskipTests

# 4. Executar o build do servidor + testes de integração (*IT.java) contra o JAR real
mvn clean verify -Pserver
```

> ⚠️ **OBSERVAÇÃO IMPORTANTE SOBRE A ORDEM DOS BUILDS:**
> 1. Os testes de integração (`*IT`) sobem o arquivo `server/mcp_organizer_server.jar`. Quando você executa `mvn clean verify -Pserver`, o próprio ciclo `package` do perfil `server` gera `server/mcp_organizer_server.jar` antes da fase `integration-test`.
> 2. Sempre que você roda um comando com `clean` (como `mvn clean package -Pserver -DskipTests` ou `mvn clean verify -Pserver`), o diretório `target/` é apagado — o que **remove o artefato do cliente (`target/dist-client/`)**. Portanto, para usar o cliente interativo, gere **primeiro** o servidor (`-Pserver`) e **depois** o cliente (`mvn package -Pclient -DskipTests` ou `mvn clean package -Pclient -DskipTests`, já que o servidor fica salvo na pasta `server/` fora de `target/`).

### 2. Importação e Execução no Eclipse IDE

1. No Eclipse IDE, selecione **File** > **Import...** > **Maven** > **Existing Maven Projects** e escolha a pasta `organizador-arquivos-mcp`.
2. Clique com o botão direito no projeto > **Run As** > **Maven build...**:
   - Na aba **Main**, informe no campo **Goals**:
     - Para testes unitários: `clean test`
     - Para gerar o servidor: `clean package -Pserver -DskipTests`
     - Para gerar o cliente: `clean package -Pclient -DskipTests`
     - Para rodar os testes de integração: `clean verify -Pserver`
   - Na aba **JRE**, no campo **VM arguments**, informe:
     ```text
     --enable-native-access=ALL-UNNAMED
     ```
3. Clique em **Run**.

---

## 💻 Como Executar o Cliente MCP Próprio

Após gerar o servidor (`mvn clean package -Pserver -DskipTests`) e em seguida o cliente (`mvn clean package -Pclient -DskipTests`), entre na pasta `target/dist-client` (ou execute da raiz do projeto):

```bash
cd target/dist-client
```

O cliente aceita o diretório autorizado de **3 formas** (e repassa automaticamente ao servidor via STDIO):

### Forma 1 — Argumento de linha de comando
```bash
java -jar mcp_organizer_client.jar "C:\caminho\para\pasta_de_teste"
```

### Forma 2 — Variável de ambiente (`MCP_FILES_ROOT`)
No **Prompt de Comando (CMD)**:
```cmd
set MCP_FILES_ROOT=C:\caminho\para\pasta_de_teste
java -jar mcp_organizer_client.jar
```
No **PowerShell**:
```powershell
$env:MCP_FILES_ROOT = "C:\caminho\para\pasta_de_teste"
java -jar mcp_organizer_client.jar
```

### Forma 3 — Propriedade da JVM (`-Dmcp.files.root`)
```bash
java -Dmcp.files.root="C:\caminho\para\pasta_de_teste" -jar mcp_organizer_client.jar
```

---

## 🔍 Como Testar no MCP Inspector (Docker Compose e `npx`)

Antes de iniciar o Inspector, certifique-se de ter gerado o JAR do servidor:
```bash
mvn clean package -Pserver -DskipTests
```

### Opção A — Via `npx` (direto na máquina local, sem Docker)
```bash
npx @modelcontextprotocol/inspector -e MCP_FILES_ROOT="C:\caminho\para\pasta_de_teste" java -jar server/mcp_organizer_server.jar
```
Abra o endereço exibido no terminal (normalmente `http://127.0.0.1:6274`) no navegador.

### Opção B — Via Docker Compose (como no exemplo do professor)
1. Copie `.env.example` para `.env` na raiz do projeto e defina `MCP_FILES_HOST` com um caminho existente na sua máquina:
   ```env
   MCP_FILES_HOST=C:/caminho/para/pasta_de_teste
   MCP_CONTAINER=/opt/mcp
   MCP_FILES_CONTAINER=${MCP_CONTAINER}/files
   ```
2. Na raiz do projeto, execute:
   ```bash
   docker compose up -d --build mcp-inspector
   ```
3. Acesse `http://127.0.0.1:6274` no navegador. Para encerrar depois, use `docker compose down`.

---

## 🤖 Como Configurar no Claude Desktop

1. Gere o JAR do servidor (`mvn clean package -Pserver -DskipTests`).
2. Abra o arquivo `claude_desktop_config.json`:
   - Windows (instalador padrão): `%APPDATA%\Claude\claude_desktop_config.json`
   - Windows (MSIX / Windows Store): `%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude_desktop_config.json`
3. Adicione a entrada em `mcpServers` (usando `env` ou `-Dmcp.files.root`):

```json
{
  "mcpServers": {
    "organizador_arquivos": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\caminho\\absoluto\\para\\organizador-arquivos-mcp\\server\\mcp_organizer_server.jar"
      ],
      "env": {
        "MCP_FILES_ROOT": "C:\\caminho\\absoluto\\para\\pasta_de_teste"
      }
    }
  }
}
```

4. Salve o arquivo, **encerre completamente** o Claude Desktop (inclusive na bandeja do sistema / Gerenciador de Tarefas) e abra-o novamente.
