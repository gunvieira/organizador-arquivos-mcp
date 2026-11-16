package br.edu.ifpr.mcp_organizer.main;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.json.JsonMapper;

public class McpOrganizerClient {
   private static final Logger LOG =
                            LoggerFactory.getLogger(McpOrganizerClient.class);
   // nome e caminho para o servidor MCP
   private static final String SERVER_PATH =
                                         "../../server/mcp_organizer_server.jar";
   // objeto cliente para comunicação com o servidor
   private final McpSyncClient client;

   private void loggingMessageNotification(
                                      LoggingMessageNotification notification) {
      LOG.info("[NOTIFICAÇÃO MCP] Origem: {} | Nível: {} | Mensagem: {}",
               notification.logger(),
               notification.level(),
               notification.data());
   }

   private McpSyncClient createClient(String root, boolean isProperty) {
      File serverJar = new File(SERVER_PATH);
      if (!serverJar.isFile()) {
         // Tenta também a partir da raiz do projeto caso executado fora de target/dist-client
         File rootServerJar = new File("server/mcp_organizer_server.jar");
         if (rootServerJar.isFile())
            serverJar = rootServerJar;
         else
            throw new IllegalStateException("JAR do servidor não encontrado: " +
                                            serverJar.getAbsolutePath());
      }
      String jarPath = serverJar.getAbsolutePath();
      LOG.info("Servidor MCP: {}",jarPath);
      ServerParameters.Builder serverParams = ServerParameters.builder("java");
      if (isProperty)
         serverParams = serverParams.args("-D%s=%s".formatted(
                                                             AppConsts.AUTH_DIR_PROP,
                                                              root),
                                          "-jar",jarPath);
      else
         serverParams = serverParams.args("-jar",jarPath)
                                    .addEnvVar(AppConsts.AUTH_DIR_VAR,root);

      ServerParameters params = serverParams.build();

      JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
                                                              new JsonMapper());
      StdioClientTransport transport  = new StdioClientTransport(params,
                                                                 jsonMapper);
      transport.setStdErrorHandler(line -> LOG.info("[SERVER STDERR] {}",line));
      return McpClient.sync(transport)
                      .loggingConsumer(this::loggingMessageNotification)
                      .build();
   }

   private void close() {
      LOG.info("Fechando cliente MCP...");
      client.closeGracefully();
      LOG.info("Cliente MCP fechado.");
   }

   private void execute() {
      client.initialize();
      client.setLoggingLevel(LoggingLevel.INFO);

      Scanner scanner = new Scanner(System.in);
      showMenu(scanner);
      scanner.close();
   }

   private ListToolsResult loadTools() {
      ListToolsResult result = client.listTools();

      LOG.info("Servidor disponibilizou {} ferramenta(s).",
               result.tools().size());

      return result;
   }

   private ListPromptsResult loadPrompts() {
      ListPromptsResult result = client.listPrompts();

      LOG.info("Servidor disponibilizou {} prompt(s).",result.prompts().size());

      return result;
   }

   private void showMenu(Scanner scanner) {
      while (true) {
         System.out.println("\n");
         System.out.println("========================================");
         System.out.println(" CLIENTE MCP - ORGANIZADOR DE ARQUIVOS");
         System.out.println("========================================");
         System.out.println("1 - Ferramentas (Tools)");
         System.out.println("2 - Recursos (Resources)");
         System.out.println("3 - Prompts");
         System.out.println("\n0 - Sair");
         System.out.print("\nOpção: ");

         if (!scanner.hasNextLine())
            return;

         String option = scanner.nextLine().trim().toLowerCase();
         if (option.isBlank())
            continue;

         switch (option) {
            case "1" -> executeToolMenu(scanner);
            case "2" -> showResourceMenu(scanner);
            case "3" -> showPromptMenu(scanner);
            case "0",
                 "sair" -> { return; }
            default -> System.out.println("==> Opção inválida <==");
         }
      }
   }

   private void executeToolMenu(Scanner scanner) {
      ListToolsResult tools = loadTools();

      if (tools.tools().isEmpty()) {
         System.out.println("Nenhuma ferramenta disponibilizada.");
         return;
      }
      while (true) {
         System.out.println();
         System.out.println("\n=== Ferramentas disponíveis ===");
         for (int i = 0; i < tools.tools().size(); i++) {
            Tool tool = tools.tools().get(i);

            System.out.printf(" %d - %s%n",i + 1,tool.name());
            if (tool.description() != null && !tool.description().isBlank())
               System.out.printf(" %s%n", tool.description());
         }
         System.out.println("\n0 - Voltar");
         System.out.print("Selecione uma ferramenta: ");

         if (!scanner.hasNextLine())
            return;

         String command = scanner.nextLine().trim();

         if ("0".equals(command) || "voltar".equalsIgnoreCase(command))
            return;

         Tool selectedTool = selectTool(tools.tools(),command);
         if (selectedTool == null) {
            LOG.warn("Ferramenta não encontrada: {}",command);
            continue;
         }
         executeTool(selectedTool,scanner);
      }
   }

   private Tool selectTool(List<Tool> tools, String command) {
      try {
         int index = Integer.parseInt(command) - 1;

         if (index >= 0 && index < tools.size())
            return tools.get(index);
      }
      catch (NumberFormatException ignored) {
         // o comando pode ser o nome da ferramenta
      }
      for (Tool tool : tools)
         if (tool.name().equalsIgnoreCase(command))
            return tool;
      return null;
   }

   private void executeTool(Tool tool, Scanner scanner) {
      System.out.println();
      System.out.println("----------------------------------------");
      System.out.println("Ferramenta: " + tool.name());

      if (tool.description() != null)
         System.out.println("Descrição: " + tool.description());

      Map<String, Object> arguments = readArguments(tool,scanner);
      CallToolRequest     request   = CallToolRequest.builder(tool.name())
                                                     .arguments(arguments)
                                                     .build();
      LOG.info("Executando ferramenta '{}'",tool.name());
      try {
         CallToolResult result = client.callTool(request);

         showResult(result);
      }
      catch (Exception ex) {
         LOG.error("Erro executando ferramenta '{}'",tool.name(),ex);
      }
   }

   private Map<String, Object> readArguments(Tool tool, Scanner scanner) {
      Map<String, Object> arguments = new LinkedHashMap<>();
      Map<String, Object> schema    = tool.inputSchema();
      if (schema == null || schema.isEmpty())
         return arguments;

      Object propertiesObject = schema.get("properties");

      if (!(propertiesObject instanceof Map<?, ?> properties))
         return arguments;

      List<?> required = getRequiredProperties(schema);

      if (properties.isEmpty())
         return arguments;

      System.out.println();
      System.out.println("Informe os parâmetros:");
      for (Map.Entry<?, ?> entry : properties.entrySet()) {
         String name = String.valueOf(entry.getKey());

         if (!(entry.getValue() instanceof Map<?, ?> property))
            continue;
         String  type        = getStringValue(property,"type","string");
         String  description = getStringValue(property,"description","");
         boolean isRequired  = required.contains(name);
         Object  value       = readArgument(scanner,name,type,description,
                                            isRequired,property);

         if (value != null)
            arguments.put(name,value);
      }
      return arguments;
   }

   private String getStringValue(Map<? ,?> map, String key,
                                 String defaultValue) {
      Object value = map.get(key);

      return value != null ? String.valueOf(value) : defaultValue;
   }

   private List<?> getRequiredProperties(Map<String, Object> schema) {
      Object required = schema.get("required");
      if (required instanceof List<?> list)
         return list;
      return List.of();
   }

   private Object readArgument(Scanner scanner, String name, String type,
                               String description, boolean required,
                               Map<?, ?> schema) {
      while (true) {
         System.out.println();
         System.out.println("'" + name + "' [" + type + "] " +
                            (required ? "(obrigatório)" : "(opcional)"));
         System.out.println("Descrição: " + description);
         System.out.print("Valor: ");

         if (!scanner.hasNextLine())
            return null;

         String value = scanner.nextLine().trim();
         if (value.isBlank()) {
            if (!required) return null;

            System.out.println("Este parâmetro é obrigatório.");
            continue;
         }
         try { return convertValue(value,type,schema); }
         catch (IllegalArgumentException ex) {
            System.out.println("Valor inválido: " + ex.getMessage());
         }
      }
   }

   private Object convertValue(String value, String type, Map<?, ?> schema) {
      return switch (type) {
                case "integer" -> Integer.valueOf(value);
                case "number" -> Double.valueOf(value);
                case "boolean" -> parseBoolean(value);
                case "string" -> value;
                case "array" -> Arrays.stream(value.split(","))
                                      .map(String::trim)
                                      .filter(s -> !s.isEmpty())
                                      .toList();
                default -> parseComplexValue(value,type,schema);
             };
   }

   private Boolean parseBoolean(String value) {
      if ("true".equalsIgnoreCase(value) ||
          "yes".equalsIgnoreCase(value) ||
          "sim".equalsIgnoreCase(value) ||
          "on".equalsIgnoreCase(value)
         )
         return true;

      if ("false".equalsIgnoreCase(value) ||
          "no".equalsIgnoreCase(value) ||
          "não".equalsIgnoreCase(value) ||
          "nao".equalsIgnoreCase(value) ||
          "off".equalsIgnoreCase(value)
         )
         return false;

      throw new IllegalArgumentException("Informe true/false ou sim/não");
   }

   private Object parseComplexValue(String value, String type,
                                    Map<?, ?> schema) {
      if ("null".equals(type))
         return null;

      throw new IllegalArgumentException("Tipo não suportado pelo modo " +
                                         "interativo: " + type);
   }

   private void showResult(CallToolResult result) {
      System.out.println();
      System.out.println("=============== RESULTADO ===============");
      System.out.println("   Erro: " + (result.isError() ? "Sim" : "Não"));
      System.out.println("Content:" );
      result.content().forEach(content -> {
         if (content instanceof TextContent textContent)
            System.out.println(textContent.text());
         else
            System.out.println(" " + content);
      });

      if (result.structuredContent() != null) {
         System.out.println();
         System.out.println("Structured Content:");
         System.out.println(result.structuredContent());
      }
      System.out.println( "==========================================" );
   }

   private void showPromptResult(GetPromptResult result) {
      System.out.println();
      System.out.println("=============== PROMPT ===============");

      if (result.description() != null)
         System.out.println("Descrição: " + result.description());

      for (PromptMessage message : result.messages()) {
         System.out.println();
         System.out.println("Papel: " + message.role());

         if (message.content() instanceof TextContent content)
            System.out.println(content.text());
         else
            System.out.println(message.content());
      }
      System.out.println("======================================");
   }

   private void showResourceMenu(Scanner scanner) {
      List<Resource> resources = client.listResources().resources();

      if (resources.isEmpty()) {
         System.out.println("Nenhum recurso disponível.");
         return;
      }
      while (true) {
         System.out.println();
         System.out.println("\n=== Recursos disponíveis ===");
         for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            System.out.printf("%d - %s [%s]%n",
                              i + 1,resource.name(),resource.uri());
         }
         System.out.println("\n0 - Voltar");
         System.out.print("Selecione um recurso: ");
         if (!scanner.hasNextLine())
            return;

         String option = scanner.nextLine().trim();
         if (option.isBlank())
            continue;

         if ("0".equals(option) || "voltar".equalsIgnoreCase(option))
            return;

         int index;
         try { index = Integer.parseInt(option) - 1; }
         catch (NumberFormatException e) {
            System.out.println("Informe um número válido.");
            continue;
         }
         if (index < 0 || index >= resources.size()) {
            System.out.println("Opção inválida.");
            continue;
         }
         readResource(resources.get(index));
      }
   }

   private void showPromptMenu(Scanner scanner) {
      ListPromptsResult result  = loadPrompts();
      List<Prompt>      prompts = result.prompts();

      if (prompts.isEmpty()) {
         System.out.println("Nenhum prompt disponibilizado.");
         return;
      }
      while (true) {
         System.out.println("\n=== Prompts disponíveis ===");

         for (int i = 0; i < prompts.size(); i++) {
            Prompt prompt = prompts.get(i);

            System.out.printf("%d - %s%n",i + 1,prompt.name());

            if (prompt.description() != null && !prompt.description().isBlank())
               System.out.println(" " + prompt.description());

            System.out.printf(" Argumentos: %d%n",prompt.arguments() == null
                                                  ? 0
                                                  : prompt.arguments().size());
         }
         System.out.println("\n0 - Voltar");
         System.out.print("Selecione um prompt: ");

         if (!scanner.hasNextLine())
            return;

         String option = scanner.nextLine().trim();

         if ("0".equals(option) || "voltar".equalsIgnoreCase(option))
            return;

         Prompt selectedPrompt = selectPrompt(prompts,option);

         if (selectedPrompt == null) {
            System.out.println("Prompt não encontrado.");
            continue;
         }
         executePrompt(selectedPrompt,scanner);
      }
   }

   private Prompt selectPrompt(List<Prompt> prompts, String command) {
      try {
         int index = Integer.parseInt(command) - 1;

         if (index >= 0 && index < prompts.size())
            return prompts.get(index);
      }
      catch (NumberFormatException ignored) {
         // O comando pode ser o nome do prompt.
      }

      for (Prompt prompt : prompts)
         if (prompt.name().equalsIgnoreCase(command))
            return prompt;

      return null;
   }

   private void executePrompt(Prompt prompt, Scanner scanner) {
      System.out.println();
      System.out.println("----------------------------------------");
      System.out.println("Prompt: " + prompt.name());

      if (prompt.description() != null)
         System.out.println("Descrição: " + prompt.description());

      Map<String,Object> arguments = readPromptArguments(prompt,scanner);

      GetPromptRequest request = GetPromptRequest.builder(prompt.name())
                                                 .arguments(arguments)
                                                 .build();

      LOG.info("Obtendo prompt '{}'",prompt.name());

      try {
         GetPromptResult result = client.getPrompt(request);

         showPromptResult(result);
      }
      catch (Exception ex) {
         LOG.error("Erro obtendo prompt '{}'",prompt.name(),ex);
      }
   }

   private Map<String,Object> readPromptArguments(Prompt prompt,
                                                  Scanner scanner) {
      Map<String,Object> arguments = new LinkedHashMap<>();

      if (prompt.arguments() == null || prompt.arguments().isEmpty())
         return arguments;

      System.out.println("\nInforme os argumentos:");
      for (PromptArgument argument : prompt.arguments()) {
         boolean required = Boolean.TRUE.equals(argument.required());

         while (true) {
            System.out.println();
            System.out.println("'" + argument.name() + "'" +
                               (required ? " (obrigatório)" : " (opcional)"));

            if (argument.description() != null)
               System.out.println("Descrição: " + argument.description());

            System.out.print("Valor: ");
            if (!scanner.hasNextLine())
               return arguments;

            String value = scanner.nextLine().trim();
            if (value.isBlank()) {
               if (!required)
                  break;

               System.out.println("Este argumento é obrigatório.");
               continue;
            }
            arguments.put(argument.name(),value);
            break;
         }
      }
      return arguments;
   }

   private void readResource(Resource resource) {
      ReadResourceRequest request = ReadResourceRequest.builder(resource.uri())
                                                       .build();

      ReadResourceResult  result  = client.readResource(request);

      System.out.println("\n=== " + resource.name() + " ===");
      for (ResourceContents content : result.contents()) {
         if (content instanceof TextResourceContents textContent)
            System.out.println(textContent.text());
         else
            System.out.println("Conteúdo binário não suportado neste menu.");
      }
   }

   public McpOrganizerClient(String root, boolean isProperty) {
      this.client = createClient(root,isProperty);
   }

   public static void main(String... args) {
      String  mcpFilesRootArg  = args.length != 0 ? args[0] : null;
      String  mcpFilesRootProp = System.getProperty(AppConsts.AUTH_DIR_PROP);
      String  mcpFilesRootVar  = System.getenv(AppConsts.AUTH_DIR_VAR);
      String  mcpFilesRoot     = "";
      boolean isProperty       = mcpFilesRootProp != null;

      if (mcpFilesRootArg != null) mcpFilesRoot = mcpFilesRootArg;
      else if (mcpFilesRootProp != null) mcpFilesRoot = mcpFilesRootProp;
      else if (mcpFilesRootVar != null) mcpFilesRoot = mcpFilesRootVar;
      else
         LOG.error("O caminho para o diretório autorizado não foi fornecido");

      McpOrganizerClient clientStdio = new McpOrganizerClient(mcpFilesRoot,
                                                              isProperty);

      try { clientStdio.execute(); }
      finally { clientStdio.close(); }
   }
}
