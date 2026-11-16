package br.edu.ifpr.mcp_organizer.services.organizer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.edu.ifpr.mcp_organizer.services.organizer.ConflictResolver.Resolution;
import br.edu.ifpr.mcp_organizer.services.organizer.model.BatchStatus;
import br.edu.ifpr.mcp_organizer.services.organizer.model.ExecutedMoveRecord;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationBatch;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;
import br.edu.ifpr.mcp_organizer.services.organizer.model.PlannedMove;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

/**
 * Executor de planos de organização ({@code aplicar_organizacao}):
 * <ul>
 *   <li>Recebe apenas o {@code plan_id} (nunca caminhos livres);</li>
 *   <li>Impede que o mesmo plano seja aplicado mais de uma vez;</li>
 *   <li>Grava o journal como {@code PENDENTE} antes de iniciar as movimentações;</li>
 *   <li>Revalida cada movimento na sandbox, resolve conflitos de nome ({@code nome (1).ext})
 *       e atualiza o journal a cada movimento concluído;</li>
 *   <li>Se um movimento falhar, interrompe imediatamente, marca o lote como {@code PARCIAL}
 *       e retorna {@code partialFailure = true} informando o que já foi movido (que pode ser desfeito).</li>
 * </ul>
 */
public class OrganizationExecutor {

   private final SandboxPathResolver pathResolver;
   private final OrganizationRepository repository;
   private final ConflictResolver conflictResolver;

   public OrganizationExecutor(SandboxPathResolver pathResolver,
                               OrganizationRepository repository,
                               ConflictResolver conflictResolver) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");
      if (repository == null)
         throw new IllegalArgumentException(
            "O repositório de organização não pode ser nulo.");
      if (conflictResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de conflitos não pode ser nulo.");
      this.pathResolver     = pathResolver;
      this.repository       = repository;
      this.conflictResolver = conflictResolver;
   }

   /**
    * Resultado da execução de um plano.
    *
    * @param batch          lote registrado no journal
    * @param movedCount     quantidade de arquivos efetivamente movidos
    * @param renamedCount   quantidade de arquivos renomeados por conflito no destino
    * @param partialFailure true se a execução parou no meio devido a uma falha
    * @param report         relatório textual detalhado
    */
   public record ExecutionOutcome(
      OrganizationBatch batch,
      int movedCount,
      int renamedCount,
      boolean partialFailure,
      String report
   ) {}

   /**
    * Aplica um plano previamente gerado pelo seu {@code planId}.
    */
   public ExecutionOutcome executePlan(String planId) {
      if (planId == null || planId.isBlank())
         throw new IllegalArgumentException(
            "O parâmetro 'plan_id' é obrigatório.");

      OrganizationPlan plan = repository.findPlanById(planId.trim())
         .orElseThrow(() -> new IllegalArgumentException(
            "Plano de organização não encontrado para plan_id='" + planId +
            "'. Gere um plano primeiro com 'sugerir_organizacao'."));

      if (plan.applied())
         throw new IllegalStateException(
            "O plano '" + plan.planId() +
            "' já foi aplicado anteriormente. Cada plano só pode ser aplicado uma única vez.");

      String batchId = UUID.randomUUID().toString();
      String startedAt = Instant.now().toString();
      List<ExecutedMoveRecord> moveRecords = new ArrayList<>();

      // Marca o plano como aplicado e grava o journal inicial com status PENDENTE antes de mover
      repository.savePlan(plan.markApplied());
      OrganizationBatch pendingBatch = new OrganizationBatch(
         batchId,
         plan.planId(),
         startedAt,
         null,
         BatchStatus.PENDENTE,
         null,
         List.copyOf(moveRecords)
      );
      repository.saveBatch(pendingBatch);

      int movedCount   = 0;
      int renamedCount = 0;
      String failureMsg = null;

      for (PlannedMove planned : plan.moves()) {
         try {
            // Revalida origem e destino dentro da sandbox antes de mover
            Path sourcePath = pathResolver.resolve(planned.sourceRelativePath());
            if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS) ||
                !Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
               throw new IllegalStateException(
                  "Arquivo de origem não existe ou não é um arquivo regular: " +
                  planned.sourceRelativePath());
            }

            Path desiredTarget = pathResolver.resolve(planned.targetRelativePath());
            Resolution resolution = conflictResolver.resolveTarget(desiredTarget);
            Path actualTarget = resolution.resolvedPath();
            String actualTargetRel = pathResolver.relativize(actualTarget);

            MoveFileCommand cmd = new MoveFileCommand(sourcePath, actualTarget);
            cmd.execute();

            movedCount++;
            if (resolution.renamed())
               renamedCount++;

            moveRecords.add(new ExecutedMoveRecord(
               planned.sourceRelativePath(),
               planned.targetRelativePath(),
               actualTargetRel,
               resolution.renamed(),
               true
            ));

            // Atualiza o journal a cada movimento concluído
            repository.saveBatch(new OrganizationBatch(
               batchId,
               plan.planId(),
               startedAt,
               null,
               BatchStatus.PENDENTE,
               null,
               List.copyOf(moveRecords)
            ));
         }
         catch (Exception ex) {
            failureMsg = "Falha ao mover '" + planned.sourceRelativePath() +
                         "' -> '" + planned.targetRelativePath() + "': " +
                         ex.getMessage();
            break;
         }
      }

      boolean isPartial = (failureMsg != null);
      BatchStatus finalStatus = isPartial
                                ? BatchStatus.PARCIAL
                                : BatchStatus.CONCLUIDO;

      OrganizationBatch finalBatch = new OrganizationBatch(
         batchId,
         plan.planId(),
         startedAt,
         Instant.now().toString(),
         finalStatus,
         failureMsg,
         List.copyOf(moveRecords)
      );
      repository.saveBatch(finalBatch);

      String report = buildReport(
         finalBatch, plan.moves().size(), movedCount, renamedCount, failureMsg);

      return new ExecutionOutcome(
         finalBatch, movedCount, renamedCount, isPartial, report);
   }

   private static String buildReport(OrganizationBatch batch,
                                     int totalPlanned,
                                     int movedCount,
                                     int renamedCount,
                                     String failureMsg) {
      StringBuilder sb = new StringBuilder();
      if (failureMsg != null) {
         sb.append("ERRO: Execução interrompida (status PARCIAL).\n")
           .append("Motivo da parada: ").append(failureMsg).append('\n');
      }
      else {
         sb.append("Organização aplicada com sucesso (status CONCLUIDO).\n");
      }

      sb.append("batch_id: ").append(batch.batchId()).append('\n')
        .append("plan_id: ").append(batch.planId()).append('\n')
        .append("Movimentos planejados: ").append(totalPlanned).append('\n')
        .append("Arquivos movidos com sucesso: ").append(movedCount).append('\n')
        .append("Arquivos renomeados por conflito: ").append(renamedCount).append('\n');

      if (!batch.moves().isEmpty()) {
         sb.append("\nMovimentos realizados:\n");
         for (ExecutedMoveRecord rec : batch.moves()) {
            sb.append("- ")
              .append(rec.sourceRelativePath())
              .append(" -> ")
              .append(rec.actualTargetRelativePath());
            if (rec.renamedOnConflict())
               sb.append(" (renomeado por conflito)");
            sb.append('\n');
         }
      }

      if (failureMsg != null && movedCount > 0) {
         sb.append("\nOs ")
           .append(movedCount)
           .append(" arquivo(s) já movidos neste lote podem ser revertidos com 'desfazer_organizacao'.\n");
      }

      return sb.toString();
   }
}
