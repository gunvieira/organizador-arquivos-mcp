package br.edu.ifpr.mcp_organizer.services.organizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_organizer.mcp.tools.ApplyOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.SuggestOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.tools.UndoOrganizationTool;
import br.edu.ifpr.mcp_organizer.mcp.utils.McpConsts;
import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;
import br.edu.ifpr.mcp_organizer.services.classification.ExtensionClassifier;
import br.edu.ifpr.mcp_organizer.services.organizer.OrganizationExecutor.ExecutionOutcome;
import br.edu.ifpr.mcp_organizer.services.organizer.model.BatchStatus;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

class OrganizationServicesAndToolsTest {

   @TempDir
   Path tempDir;

   private SandboxPathResolver resolver;
   private OrganizationRepository repository;
   private ConflictResolver conflictResolver;
   private OrganizationPlanner planner;
   private OrganizationExecutor executor;
   private UndoService undoService;

   @BeforeEach
   void setUp() {
      resolver               = new SandboxPathResolver(tempDir);
      ClassificationRules r  = ClassificationRules.loadDefault();
      ExtensionClassifier c  = new ExtensionClassifier(r);
      FileScanner scanner    = new FileScanner(resolver, c, r);
      repository             = new JsonFileOrganizationRepository(resolver);
      conflictResolver       = new ConflictResolver();
      planner                = new OrganizationPlanner(resolver, scanner, repository);
      executor               = new OrganizationExecutor(resolver, repository, conflictResolver);
      undoService            = new UndoService(resolver, repository);
   }

   @Test
   @DisplayName("sugerir_organizacao deve gerar plano sem mover arquivos e ignorar arquivos já organizados")
   void plannerShouldGeneratePlanWithoutMovingAndIgnoreAlreadyOrganized() throws IOException {
      Path looseImg = Files.writeString(tempDir.resolve("foto.png"), "img");
      Path alreadyOrganizedDir = Files.createDirectories(tempDir.resolve("Documentos"));
      Path alreadyOrganizedPdf = Files.writeString(alreadyOrganizedDir.resolve("contrato.pdf"), "pdf");

      OrganizationPlan plan = planner.createPlan(null, true);

      assertNotNull(plan.planId());
      assertFalse(plan.applied());
      assertEquals(1, plan.moves().size());
      assertEquals("foto.png", plan.moves().get(0).sourceRelativePath());
      assertEquals("Imagens/foto.png", plan.moves().get(0).targetRelativePath());

      // Confirma que NENHUM arquivo foi movido durante a geração do plano
      assertTrue(Files.exists(looseImg));
      assertFalse(Files.exists(tempDir.resolve("Imagens/foto.png")));
      assertTrue(Files.exists(alreadyOrganizedPdf));
   }

   @Test
   @DisplayName("ConflictResolver deve renomear com sufixo (1), (2) sem nunca sobrescrever")
   void conflictResolverShouldAppendNumericSuffix() throws IOException {
      Path dir = Files.createDirectories(tempDir.resolve("Imagens"));
      Path original = Files.writeString(dir.resolve("foto.jpg"), "original");
      Files.writeString(dir.resolve("foto (1).jpg"), "um");

      ConflictResolver.Resolution res = conflictResolver.resolveTarget(original);
      assertTrue(res.renamed());
      assertEquals(dir.resolve("foto (2).jpg"), res.resolvedPath());

      // Arquivo sem extensão
      Path noExt = Files.writeString(dir.resolve("README"), "readme");
      ConflictResolver.Resolution resNoExt = conflictResolver.resolveTarget(noExt);
      assertTrue(resNoExt.renamed());
      assertEquals(dir.resolve("README (1)"), resNoExt.resolvedPath());
   }

   @Test
   @DisplayName("Fluxo completo: sugerir -> aplicar (com conflito) -> impedir 2ª aplicação -> desfazer")
   void shouldExecuteFullPlanApplyWithConflictAndUndo() throws IOException {
      // Destino Imagens/foto.jpg já existe no disco antes de aplicar
      Path imgDir = Files.createDirectories(tempDir.resolve("Imagens"));
      Files.writeString(imgDir.resolve("foto.jpg"), "existente");

      // Dois arquivos na raiz para organizar
      Files.writeString(tempDir.resolve("foto.jpg"), "nova_foto");
      Files.writeString(tempDir.resolve("relatorio.pdf"), "pdf_conteudo");

      OrganizationPlan plan = planner.createPlan(null, false);
      assertEquals(2, plan.moves().size());

      ExecutionOutcome outcome = executor.executePlan(plan.planId());
      assertFalse(outcome.partialFailure());
      assertEquals(BatchStatus.CONCLUIDO, outcome.batch().status());
      assertEquals(2, outcome.movedCount());
      assertEquals(1, outcome.renamedCount());

      // Verifica que foto.jpg foi movida como Imagens/foto (1).jpg sem sobrescrever Imagens/foto.jpg
      assertEquals("existente", Files.readString(tempDir.resolve("Imagens/foto.jpg")));
      assertEquals("nova_foto", Files.readString(tempDir.resolve("Imagens/foto (1).jpg")));
      assertEquals("pdf_conteudo", Files.readString(tempDir.resolve("Documentos/relatorio.pdf")));

      // Aplicar o mesmo plano uma segunda vez deve falhar
      assertThrows(IllegalStateException.class,
                   () -> executor.executePlan(plan.planId()));

      // Desfazer deve reverter em ordem inversa para os locais originais (sobrevivendo a nova instância do repo)
      OrganizationRepository reloadedRepo = new JsonFileOrganizationRepository(resolver);
      UndoService reloadedUndo = new UndoService(resolver, reloadedRepo);

      String undoReport = reloadedUndo.undo(null);
      assertTrue(undoReport.contains("Arquivos revertidos com sucesso: 2"));
      assertEquals("nova_foto", Files.readString(tempDir.resolve("foto.jpg")));
      assertEquals("pdf_conteudo", Files.readString(tempDir.resolve("relatorio.pdf")));
      assertFalse(Files.exists(tempDir.resolve("Imagens/foto (1).jpg")));

      // Tentar desfazer novamente sem histórico pendente deve falhar
      assertThrows(IllegalStateException.class, () -> reloadedUndo.undo(null));
   }

   @Test
   @DisplayName("Falha no meio do lote deve parar, gravar status PARCIAL, retornar isError=true e permitir desfazer o que foi movido")
   void partialFailureShouldStopRecordJournalAndAllowUndoOfCompletedMoves() throws IOException {
      Path f1 = Files.writeString(tempDir.resolve("a_primeiro.pdf"), "111");
      Path f2 = Files.writeString(tempDir.resolve("b_segundo.png"), "222");
      Path f3 = Files.writeString(tempDir.resolve("c_terceiro.zip"), "333");

      OrganizationPlan plan = planner.createPlan(null, false);
      assertEquals(3, plan.moves().size());

      // Simula que o 2º arquivo desapareceu antes de aplicar o plano
      Files.delete(f2);

      ApplyOrganizationTool applyTool = new ApplyOrganizationTool(executor);
      CallToolResult result = applyTool.run(null,
         CallToolRequest.builder(McpConsts.APPLY_ORG_TOOL_NAME)
                        .arguments(Map.of("plan_id", plan.planId()))
                        .build());

      // Deve retornar isError=true informando o lote PARCIAL e que 1 arquivo foi movido
      assertTrue(result.isError());
      String text = ((TextContent) result.content().get(0)).text();
      assertTrue(text.contains("PARCIAL"));
      assertTrue(text.contains("Arquivos movidos com sucesso: 1"));

      // O 1º foi movido; o 3º não foi tocado porque parou no erro do 2º
      assertFalse(Files.exists(f1));
      assertTrue(Files.exists(tempDir.resolve("Documentos/a_primeiro.pdf")));
      assertTrue(Files.exists(f3));

      // O que foi movido no lote PARCIAL pode ser desfeito
      UndoOrganizationTool undoTool = new UndoOrganizationTool(undoService);
      CallToolResult undoRes = undoTool.run(null,
         CallToolRequest.builder(McpConsts.UNDO_ORG_TOOL_NAME)
                        .arguments(Map.of())
                        .build());
      assertFalse(undoRes.isError());
      assertTrue(Files.exists(f1));
   }

   @Test
   @DisplayName("Desfazer não deve sobrescrever quando o local original estiver ocupado")
   void undoShouldNotOverwriteOccupiedOriginalLocation() throws IOException {
      Files.writeString(tempDir.resolve("nota.txt"), "conteudo_movido");
      OrganizationPlan plan = planner.createPlan(null, false);
      executor.executePlan(plan.planId());

      // Cria um novo arquivo no local original antes de desfazer
      Files.writeString(tempDir.resolve("nota.txt"), "novo_no_local_original");

      String undoReport = undoService.undo(null);
      assertTrue(undoReport.contains("Conflitos/arquivos pulados: 1"));
      assertTrue(undoReport.contains("local original ocupado"));

      // Ambos os arquivos permanecem preservados sem sobrescrita
      assertEquals("novo_no_local_original", Files.readString(tempDir.resolve("nota.txt")));
      assertEquals("conteudo_movido", Files.readString(tempDir.resolve("Documentos/nota.txt")));
   }

   @Test
   @DisplayName("Tools de organização devem retornar erro claro para plan_id inexistente ou sem histórico")
   void organizationToolsShouldReturnErrorOnInvalidInputs() {
      SuggestOrganizationTool suggestTool = new SuggestOrganizationTool(planner);
      ApplyOrganizationTool applyTool     = new ApplyOrganizationTool(executor);
      UndoOrganizationTool undoTool       = new UndoOrganizationTool(undoService);

      CallToolResult suggestOk = suggestTool.run(null,
         CallToolRequest.builder(McpConsts.SUGGEST_ORG_TOOL_NAME)
                        .arguments(Map.of())
                        .build());
      assertFalse(suggestOk.isError());

      CallToolResult applyInvalid = applyTool.run(null,
         CallToolRequest.builder(McpConsts.APPLY_ORG_TOOL_NAME)
                        .arguments(Map.of("plan_id", "id-inexistente"))
                        .build());
      assertTrue(applyInvalid.isError());

      CallToolResult undoEmpty = undoTool.run(null,
         CallToolRequest.builder(McpConsts.UNDO_ORG_TOOL_NAME)
                        .arguments(Map.of())
                        .build());
      assertTrue(undoEmpty.isError());
   }
}
