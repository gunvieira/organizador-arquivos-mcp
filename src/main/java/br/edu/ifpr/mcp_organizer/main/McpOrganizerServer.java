package br.edu.ifpr.mcp_organizer.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpPromptDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpResourceDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import tools.jackson.databind.json.JsonMapper;

/**
 * Aplicação Servidor MCP com transporte síncrono STDIO.
 */
public class McpOrganizerServer {
   private static final Logger LOG =
                            LoggerFactory.getLogger(McpOrganizerServer.class);

   private final McpSyncServer server;

   private void registerTools(
                          SyncSpecification<SingleSessionSyncSpecification> mcp,
                          ServerComposition composition)
                throws Exception {
      for (McpToolDefinition tool : composition.getTools())
         tool.register(mcp);
   }

   private void registerResources(
                          SyncSpecification<SingleSessionSyncSpecification> mcp,
                          ServerComposition composition)
                throws Exception {
      for (McpResourceDefinition resource : composition.getResources())
         resource.register(mcp);
   }

   private void registerPrompts(
                          SyncSpecification<SingleSessionSyncSpecification> mcp,
                          ServerComposition composition)
                throws Exception {
      for (McpPromptDefinition prompt : composition.getPrompts())
         prompt.register(mcp);
   }

   /**
    * Configura o transporte STDIO, o mapeador JSON (Jackson 3) e as capacidades
    * anunciadas pelo servidor MCP (tools, resources, prompts e logging).
    */
   private SyncSpecification<SingleSessionSyncSpecification> configMcpServer() {
      JacksonMcpJsonMapper         jsonMapper        =
                                     new JacksonMcpJsonMapper(new JsonMapper());
      StdioServerTransportProvider transportProvider =
                                   new StdioServerTransportProvider(jsonMapper);
      ServerCapabilities caps = ServerCapabilities.builder()
                                                  .tools(true)
                                                  .resources(false,false)
                                                  .prompts(true)
                                                  .logging()
                                                  .build();
      Implementation     impl = Implementation.builder(McpConsts.APP_NAME,
                                                       McpConsts.APP_VERSION)
                                              .build();

      return McpServer.sync(transportProvider)
                      .serverInfo(impl)
                      .capabilities(caps);
   }

   public McpOrganizerServer() throws Exception {
      this(new ServerComposition());
   }

   public McpOrganizerServer(ServerComposition composition) throws Exception {
      if (composition == null)
         throw new IllegalArgumentException(
            "A composição do servidor não pode ser nula.");

      SyncSpecification<SingleSessionSyncSpecification> srv = configMcpServer();

      try {
         // Registra todos os componentes fornecidos pela composição de dependências
         registerTools(srv, composition);
         registerResources(srv, composition);
         registerPrompts(srv, composition);
         this.server = srv.build();

         LOG.info("MCP Java Server iniciado via STDIO com raiz '{}'...",
                  composition.getPathResolver().getRoot());
      }
      catch (Exception ex) {
         LOG.error("Erro na configuração de uma ou mais ferramentas", ex);
         throw ex;
      }
   }

   public McpSyncServer getServer() {
      return server;
   }

   public static void main(String[] args) {
      try {
         new McpOrganizerServer();
      }
      catch (Exception ex) {
         LOG.error("Falha fatal ao iniciar o servidor MCP: {}",
                   ex.getMessage(), ex);
         System.exit(1);
      }
   }
}
