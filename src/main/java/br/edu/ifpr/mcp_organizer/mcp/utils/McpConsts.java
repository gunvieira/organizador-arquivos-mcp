package br.edu.ifpr.mcp_organizer.mcp.utils;

public final class McpConsts {
   // info sobre o servidor MCP
   public static final String APP_NAME        = "Organizador de Arquivos MCP";
   public static final String APP_VERSION     = "1.0.0";
   public static final String APP_DESCRIPTION =
      "Servidor MCP em Java para organização, consulta e gerenciamento de arquivos.";

   // =========================================================================
   // Ferramentas (Tools) e Atributos
   // =========================================================================
   // AuthPathTool
   public static final String APT_TOOL_NAME = "auth_path";

   // Atributos comuns
   public static final String ATTR_PATH      = "path";
   public static final String ATTR_RECURSIVE = "recursive";
   public static final String ATTR_LIMIT     = "limit";
   public static final String ATTR_MIN_SIZE  = "min_size";
   public static final String ATTR_MAX_SIZE  = "max_size";

   // ScanDirectoryTool
   public static final String SCAN_TOOL_NAME = "escanear_pasta";

   // SearchFilesTool
   public static final String SEARCH_TOOL_NAME            = "buscar_arquivos";
   public static final String SEARCH_ATTR_NAME            = "name";
   public static final String SEARCH_ATTR_CATEGORY        = "category";
   public static final String SEARCH_ATTR_EXTENSIONS      = "extensions";
   public static final String SEARCH_ATTR_MODIFIED_AFTER  = "modified_after";
   public static final String SEARCH_ATTR_MODIFIED_BEFORE = "modified_before";
   public static final String SEARCH_ATTR_MAX_RESULTS     = "max_results";

   // FindDuplicatesTool
   public static final String DUPLICATES_TOOL_NAME = "encontrar_duplicados";

   // ListLargestFilesTool
   public static final String LARGEST_TOOL_NAME = "listar_maiores";

   // ListOldestFilesTool
   public static final String OLDEST_TOOL_NAME = "listar_mais_antigos";

   // SuggestOrganizationTool
   public static final String SUGGEST_ORG_TOOL_NAME = "sugerir_organizacao";

   // ApplyOrganizationTool
   public static final String APPLY_ORG_TOOL_NAME = "aplicar_organizacao";
   public static final String ATTR_PLAN_ID        = "plan_id";

   // UndoOrganizationTool
   public static final String UNDO_ORG_TOOL_NAME = "desfazer_organizacao";
   public static final String ATTR_BATCH_ID      = "batch_id";

   // =========================================================================
   // Recursos (Resources)
   // =========================================================================
   // AboutResource
   public static final String RESOURCE_ABOUT_NAME = "about";
   public static final String RESOURCE_ABOUT_URI  = "app://" +
                                                    RESOURCE_ABOUT_NAME;
   // RootInfoResource
   public static final String RESOURCE_ROOT_INFO_NAME = "root-info";
   public static final String RESOURCE_ROOT_INFO_URI  = "app://" +
                                                        RESOURCE_ROOT_INFO_NAME;
   // StatisticsResource
   public static final String RESOURCE_STATS_NAME = "estatisticas";
   public static final String RESOURCE_STATS_URI  = "app://" +
                                                    RESOURCE_STATS_NAME;
   // HistoryResource
   public static final String RESOURCE_HISTORY_NAME = "historico";
   public static final String RESOURCE_HISTORY_URI  = "app://" +
                                                      RESOURCE_HISTORY_NAME;
   // ClassificationRulesResource
   public static final String RESOURCE_RULES_NAME = "regras-classificacao";
   public static final String RESOURCE_RULES_URI  = "app://" +
                                                    RESOURCE_RULES_NAME;

   // =========================================================================
   // Prompts
   // =========================================================================
   public static final String PROMPT_ORGANIZE      = "organizar_pasta";
   public static final String PROMPT_FREE_SPACE    = "liberar_espaco";
   public static final String PROMPT_FIND_FILE     = "encontrar_arquivo";
   public static final String PROMPT_ATTR_PASTA    = "pasta";
   public static final String PROMPT_ATTR_DESCRICAO = "descricao";

   private McpConsts() {}
}
