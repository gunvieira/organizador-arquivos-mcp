package br.edu.ifpr.mcp_organizer.services.organizer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.edu.ifpr.mcp_organizer.services.organizer.model.BatchStatus;
import br.edu.ifpr.mcp_organizer.services.organizer.model.ExecutedMoveRecord;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationBatch;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;

/**
 * Serviço de reversão ({@code desfazer_organizacao}): reverte o último lote
 * aplicado e ainda não desfeito (ou um {@code batch_id} específico) em
 * <b>ordem inversa</b>. Se o local original estiver ocupado, nunca sobrescreve:
 * pula o arquivo, registra e reporta o conflito no resultado.
 */
public class UndoService {

   private final SandboxPathResolver pathResolver;
   private final OrganizationRepository repository;

   public UndoService(SandboxPathResolver pathResolver,
                      OrganizationRepository repository) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");
      if (repository == null)
         throw new IllegalArgumentException(
            "O repositório de organização não pode ser nulo.");
      this.pathResolver = pathResolver;
      this.repository   = repository;
   }

   /**
    * Desfaz o último lote elegível (quando {@code batchId} for nulo/vazio) ou
    * o lote específico indicado por {@code batchId}.
    */
   public String undo(String batchId) {
      final OrganizationBatch batch;
      if (batchId != null && !batchId.isBlank()) {
         batch = repository.findBatchById(batchId.trim())
            .orElseThrow(() -> new IllegalArgumentException(
               "Lote não encontrado no histórico para batch_id='" + batchId + "'."));
         if (batch.status() == BatchStatus.DESFEITO)
            throw new IllegalStateException(
               "O lote '" + batch.batchId() + "' já foi desfeito anteriormente.");
         if (!JsonFileOrganizationRepository.isUndoable(batch))
            throw new IllegalStateException(
               "O lote '" + batch.batchId() +
               "' não possui movimentos concluídos para desfazer.");
      }
      else {
         batch = repository.findLastUndoableBatch()
            .orElseThrow(() -> new IllegalStateException(
               "Não há histórico de organização pendente para desfazer."));
      }

      List<ExecutedMoveRecord> moves = batch.moves();
      List<String> revertedLines     = new ArrayList<>();
      List<String> skippedConflicts  = new ArrayList<>();

      // Reverte em ordem inversa
      for (int i = moves.size() - 1; i >= 0; i--) {
         ExecutedMoveRecord rec = moves.get(i);
         if (!rec.completed())
            continue;

         try {
            Path currentLocation  = pathResolver.resolve(rec.actualTargetRelativePath());
            Path originalLocation = pathResolver.resolve(rec.sourceRelativePath());

            if (Files.exists(originalLocation, LinkOption.NOFOLLOW_LINKS)) {
               skippedConflicts.add(
                  rec.actualTargetRelativePath() + " -> " +
                  rec.sourceRelativePath() +
                  " (local original ocupado; ignorado para não sobrescrever)");
               continue;
            }

            if (!Files.exists(currentLocation, LinkOption.NOFOLLOW_LINKS)) {
               skippedConflicts.add(
                  rec.actualTargetRelativePath() + " -> " +
                  rec.sourceRelativePath() +
                  " (arquivo não encontrado no destino atual)");
               continue;
            }

            MoveFileCommand cmd = new MoveFileCommand(originalLocation, currentLocation);
            cmd.undo();

            revertedLines.add(
               rec.actualTargetRelativePath() + " -> " + rec.sourceRelativePath());
         }
         catch (Exception ex) {
            skippedConflicts.add(
               rec.actualTargetRelativePath() + " -> " +
               rec.sourceRelativePath() + " (erro: " + ex.getMessage() + ")");
         }
      }

      OrganizationBatch updatedBatch = new OrganizationBatch(
         batch.batchId(),
         batch.planId(),
         batch.startedAt(),
         Instant.now().toString(),
         BatchStatus.DESFEITO,
         batch.errorMessage(),
         batch.moves()
      );
      repository.saveBatch(updatedBatch);

      StringBuilder sb = new StringBuilder();
      sb.append("Reversão concluída para o lote: ").append(batch.batchId()).append('\n')
        .append("plan_id original: ").append(batch.planId()).append('\n')
        .append("Arquivos revertidos com sucesso: ").append(revertedLines.size()).append('\n')
        .append("Conflitos/arquivos pulados: ").append(skippedConflicts.size()).append('\n');

      if (!revertedLines.isEmpty()) {
         sb.append("\nMovimentos desfeitos (ordem inversa):\n");
         for (String line : revertedLines)
            sb.append("- ").append(line).append('\n');
      }

      if (!skippedConflicts.isEmpty()) {
         sb.append("\nConflitos não sobrescritos / pendências:\n");
         for (String conflict : skippedConflicts)
            sb.append("- ").append(conflict).append('\n');
      }

      return sb.toString();
   }
}
