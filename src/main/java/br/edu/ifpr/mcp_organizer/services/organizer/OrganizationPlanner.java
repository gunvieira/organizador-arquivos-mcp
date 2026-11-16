package br.edu.ifpr.mcp_organizer.services.organizer;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;
import br.edu.ifpr.mcp_organizer.services.organizer.model.PlannedMove;
import br.edu.ifpr.mcp_organizer.services.scan.FileInfo;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner;
import br.edu.ifpr.mcp_organizer.services.scan.FileScanner.ScanResult;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

/**
 * Gerador de planos de organização ({@code sugerir_organizacao}): analisa os
 * arquivos da pasta alvo, propõe os movimentos para {@code <Categoria>/<arquivo>}
 * ignorando arquivos que já estejam na pasta da categoria correta, e persiste o
 * plano com um {@code plan_id} (UUID). **Não move nenhum arquivo.**
 */
public class OrganizationPlanner {

   private final SandboxPathResolver pathResolver;
   private final FileScanner scanner;
   private final OrganizationRepository repository;

   public OrganizationPlanner(SandboxPathResolver pathResolver,
                              FileScanner scanner,
                              OrganizationRepository repository) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");
      if (scanner == null)
         throw new IllegalArgumentException(
            "O scanner de arquivos não pode ser nulo.");
      if (repository == null)
         throw new IllegalArgumentException(
            "O repositório de organização não pode ser nulo.");
      this.pathResolver = pathResolver;
      this.scanner      = scanner;
      this.repository   = repository;
   }

   /**
    * Cria e persiste um plano de organização para o diretório informado.
    */
   public OrganizationPlan createPlan(String relativePath, boolean recursive) {
      ScanResult scanResult = scanner.scan(relativePath, recursive);
      Path targetDir = scanResult.targetDirectory();
      String relTargetDir = scanResult.relativeTargetDirectory();

      List<PlannedMove> moves = new ArrayList<>();

      for (FileInfo file : scanResult.files()) {
         String category = file.category();
         Path desiredCategoryDir = targetDir.resolve(category);
         Path desiredTargetFile  = desiredCategoryDir.resolve(file.fileName());

         // Ignora arquivos que já estejam na pasta da categoria correta
         Path currentParent = file.absolutePath().getParent();
         if (file.absolutePath().equals(desiredTargetFile) ||
             (currentParent != null &&
              currentParent.getFileName() != null &&
              currentParent.getFileName().toString().equalsIgnoreCase(category))) {
            continue;
         }

         String targetRel = pathResolver.relativize(desiredTargetFile);
         moves.add(new PlannedMove(
            file.relativePath(),
            targetRel,
            category
         ));
      }

      String planId = UUID.randomUUID().toString();
      OrganizationPlan plan = new OrganizationPlan(
         planId,
         Instant.now().toString(),
         relTargetDir,
         recursive,
         false,
         moves
      );

      repository.savePlan(plan);
      return plan;
   }

   /**
    * Gera o relatório textual para a tool {@code sugerir_organizacao}.
    */
   public String suggestOrganizationReport(String relativePath, boolean recursive) {
      OrganizationPlan plan = createPlan(relativePath, recursive);

      StringBuilder sb = new StringBuilder();
      sb.append("Plano de organização gerado (NENHUM arquivo foi movido).\n")
        .append("plan_id: ").append(plan.planId()).append('\n')
        .append("Diretório alvo: ").append(plan.targetDirectory()).append('\n')
        .append("Busca recursiva: ").append(plan.recursive() ? "sim" : "não").append('\n')
        .append("Total de movimentos propostos: ").append(plan.moves().size()).append('\n');

      if (plan.moves().isEmpty()) {
         sb.append("\nTodos os arquivos já estão organizados ou a pasta não possui arquivos elegíveis.\n");
      }
      else {
         sb.append("\nMovimentos propostos (origem -> destino [categoria]):\n");
         for (PlannedMove move : plan.moves()) {
            sb.append("- ")
              .append(move.sourceRelativePath())
              .append(" -> ")
              .append(move.targetRelativePath())
              .append(" [").append(move.category()).append("]\n");
         }
         sb.append("\nPara aplicar este plano após confirmação do usuário, execute 'aplicar_organizacao' informando plan_id=\"")
           .append(plan.planId()).append("\".\n");
      }

      return sb.toString();
   }
}
