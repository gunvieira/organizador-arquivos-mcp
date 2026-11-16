package br.edu.ifpr.mcp_organizer.main;

import java.nio.file.Path;
import java.util.List;

import br.edu.ifpr.mcp_organizer.mcp.prompts.FindFilePrompt;
import br.edu.ifpr.mcp_organizer.mcp.prompts.FreeSpacePrompt;
import br.edu.ifpr.mcp_organizer.mcp.prompts.OrganizeFolderPrompt;
import br.edu.ifpr.mcp_organizer.mcp.resources.AboutResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.ClassificationRulesResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.HistoryResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.RootInfoResource;
import br.edu.ifpr.mcp_organizer.mcp.resources.StatisticsResource;
import br.edu.ifpr.mcp_organizer.mcp.tools.ApplyOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.AuthPathTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.FindDuplicatesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.ListLargestFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.ListOldestFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.ScanDirectoryTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.SearchFilesTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.SuggestOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.UndoOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpPromptDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpResourceDefinition;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpToolDefinition;
import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ClassifierRegistry;
import br.edu.ifpr.mcp_organizer.services.classification.FileClassifier;
import br.edu.ifpr.mcp_organizer.services.duplicates.DuplicateFinderService;
import br.edu.ifpr.mcp_organizer.services.duplicates.HashStrategy;
import br.edu.ifpr.mcp_organizer.services.duplicates.Sha256HashStrategy;
import br.edu.ifpr.mcp_organizer.services.organizer.ConflictResolver;
import br.edu.ifpr.mcp_organizer.services.organizer.JsonFileOrganizationRepository;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationExecutor;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationPlanner;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationRepository;
import br.edu.ifpr.mcp_organizer.services.organizer.UndoService;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.search.FileSearchService;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

/**
 * Composição manual das dependências do servidor MCP (Injeção de Dependência
 * por construtor). Centraliza a criação dos serviços, ferramentas, recursos e
 * prompts sem acoplamento estático oculto.
 */
public class ServerComposition {

   private final SandboxPathResolver pathResolver;
   private final ClassificationRules classificationRules;
   private final ClassifierRegistry classifierRegistry;
   private final FileClassifier fileClassifier;
   private final FileScanner fileScanner;
   private final FileSearchService fileSearchService;
   private final HashStrategy hashStrategy;
   private final DuplicateFinderService duplicateFinderService;
   private final OrganizationRepository organizationRepository;
   private final ConflictResolver conflictResolver;
   private final OrganizationPlanner organizationPlanner;
   private final OrganizationExecutor organizationExecutor;
   private final UndoService undoService;

   private final List<McpToolDefinition> tools;
   private final List<McpResourceDefinition> resources;
   private final List<McpPromptDefinition> prompts;

   /**
    * Compõe todas as dependências do servidor a partir da configuração do
    * processo ({@code mcp.files.root} ou {@code MCP_FILES_ROOT}).
    */
   public ServerComposition() {
      this(SandboxPathResolver.fromConfiguration());
   }

   /**
    * Compõe todas as dependências do servidor a partir de um diretório raiz.
    *
    * @param root diretório raiz autorizado
    */
   public ServerComposition(Path root) {
      this(new SandboxPathResolver(root));
   }

   /**
    * Compõe todas as dependências do servidor a partir de um
    * {@link SandboxPathResolver} já validado.
    *
    * @param pathResolver resolvedor de caminhos da sandbox
    */
   public ServerComposition(SandboxPathResolver pathResolver) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");

      this.pathResolver           = pathResolver;
      this.classificationRules    = ClassificationRules.loadDefault();
      this.classifierRegistry     = new ClassifierRegistry(classificationRules);
      this.fileClassifier         = classifierRegistry.getDefaultClassifier();
      this.fileScanner            = new FileScanner(pathResolver,
                                                    fileClassifier,
                                                    classificationRules);
      this.fileSearchService      = new FileSearchService(fileScanner,
                                                          classificationRules);
      this.hashStrategy           = new Sha256HashStrategy();
      this.duplicateFinderService = new DuplicateFinderService(fileScanner,
                                                               hashStrategy);
      this.organizationRepository = new JsonFileOrganizationRepository(pathResolver);
      this.conflictResolver       = new ConflictResolver();
      this.organizationPlanner    = new OrganizationPlanner(pathResolver,
                                                            fileScanner,
                                                            organizationRepository);
      this.organizationExecutor   = new OrganizationExecutor(pathResolver,
                                                             organizationRepository,
                                                             conflictResolver);
      this.undoService            = new UndoService(pathResolver,
                                                    organizationRepository);

      Path root = pathResolver.getRoot();

      this.tools = List.of(
         new AuthPathTool(root),
         new ScanDirectoryTool(fileScanner),
         new SearchFilesTool(fileSearchService),
         new FindDuplicatesTool(duplicateFinderService),
         new ListLargestFilesTool(fileSearchService),
         new ListOldestFilesTool(fileSearchService),
         new SuggestOrganizationTool(organizationPlanner),
         new ApplyOrganizationTool(organizationExecutor),
         new UndoOrganizationTool(undoService)
      );

      this.resources = List.of(
         new AboutResource(),
         new RootInfoResource(root),
         new StatisticsResource(fileScanner),
         new HistoryResource(organizationRepository),
         new ClassificationRulesResource(classificationRules)
      );

      this.prompts = List.of(
         new OrganizeFolderPrompt(),
         new FreeSpacePrompt(),
         new FindFilePrompt()
      );
   }

   public SandboxPathResolver getPathResolver() {
      return pathResolver;
   }

   public ClassificationRules getClassificationRules() {
      return classificationRules;
   }

   public ClassifierRegistry getClassifierRegistry() {
      return classifierRegistry;
   }

   public FileClassifier getFileClassifier() {
      return fileClassifier;
   }

   public FileScanner getFileScanner() {
      return fileScanner;
   }

   public FileSearchService getFileSearchService() {
      return fileSearchService;
   }

   public HashStrategy getHashStrategy() {
      return hashStrategy;
   }

   public DuplicateFinderService getDuplicateFinderService() {
      return duplicateFinderService;
   }

   public OrganizationRepository getOrganizationRepository() {
      return organizationRepository;
   }

   public ConflictResolver getConflictResolver() {
      return conflictResolver;
   }

   public OrganizationPlanner getOrganizationPlanner() {
      return organizationPlanner;
   }

   public OrganizationExecutor getOrganizationExecutor() {
      return organizationExecutor;
   }

   public UndoService getUndoService() {
      return undoService;
   }

   public List<McpToolDefinition> getTools() {
      return tools;
   }

   public List<McpResourceDefinition> getResources() {
      return resources;
   }

   public List<McpPromptDefinition> getPrompts() {
      return prompts;
   }
}
