package br.edu.ifpr.mcp_organizer.services.organizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.edu.ifpr.mcp_organizer.services.organizer.model.BatchStatus;
import br.edu.ifpr.mcp_organizer.services.organizer.model.ExecutedMoveRecord;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationBatch;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;
import br.edu.ifpr.mcp_organizer.services.security.SandboxPathResolver;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementação de {@link OrganizationRepository} que persiste planos e
 * histórico em arquivos JSON dentro de {@code <raiz>/.mcp-organizer/}.
 */
public class JsonFileOrganizationRepository implements OrganizationRepository {

   public static final String PLANS_FILE_NAME   = "plans.json";
   public static final String HISTORY_FILE_NAME = "history.json";

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private final Path appDataDir;
   private final Path plansFile;
   private final Path historyFile;

   public JsonFileOrganizationRepository(SandboxPathResolver pathResolver) {
      if (pathResolver == null)
         throw new IllegalArgumentException(
            "O resolvedor de caminhos não pode ser nulo.");
      this.appDataDir  = pathResolver.getAppDataDir();
      this.plansFile   = appDataDir.resolve(PLANS_FILE_NAME);
      this.historyFile = appDataDir.resolve(HISTORY_FILE_NAME);
   }

   @Override
   public synchronized void savePlan(OrganizationPlan plan) {
      if (plan == null || plan.planId() == null || plan.planId().isBlank())
         throw new IllegalArgumentException("Plano inválido para persistência.");

      List<OrganizationPlan> plans = new ArrayList<>(findAllPlans());
      boolean replaced = false;
      for (int i = 0; i < plans.size(); i++) {
         if (plans.get(i).planId().equals(plan.planId())) {
            plans.set(i, plan);
            replaced = true;
            break;
         }
      }
      if (!replaced)
         plans.add(plan);

      writeJsonList(plansFile, plans);
   }

   @Override
   public synchronized Optional<OrganizationPlan> findPlanById(String planId) {
      if (planId == null || planId.isBlank())
         return Optional.empty();
      String trimmed = planId.trim();
      return findAllPlans().stream()
                           .filter(p -> p.planId().equals(trimmed))
                           .findFirst();
   }

   @Override
   public synchronized List<OrganizationPlan> findAllPlans() {
      if (!Files.exists(plansFile))
         return List.of();
      try {
         return MAPPER.readValue(
            plansFile.toFile(),
            new TypeReference<List<OrganizationPlan>>() {});
      }
      catch (Exception ex) {
         throw new IllegalStateException(
            "Erro ao ler arquivo de planos JSON: " + ex.getMessage(), ex);
      }
   }

   @Override
   public synchronized void saveBatch(OrganizationBatch batch) {
      if (batch == null || batch.batchId() == null || batch.batchId().isBlank())
         throw new IllegalArgumentException("Lote inválido para persistência.");

      List<OrganizationBatch> batches = new ArrayList<>(findAllBatches());
      boolean replaced = false;
      for (int i = 0; i < batches.size(); i++) {
         if (batches.get(i).batchId().equals(batch.batchId())) {
            batches.set(i, batch);
            replaced = true;
            break;
         }
      }
      if (!replaced)
         batches.add(batch);

      writeJsonList(historyFile, batches);
   }

   @Override
   public synchronized Optional<OrganizationBatch> findBatchById(String batchId) {
      if (batchId == null || batchId.isBlank())
         return Optional.empty();
      String trimmed = batchId.trim();
      return findAllBatches().stream()
                             .filter(b -> b.batchId().equals(trimmed))
                             .findFirst();
   }

   @Override
   public synchronized Optional<OrganizationBatch> findLastUndoableBatch() {
      List<OrganizationBatch> batches = findAllBatches();
      for (int i = batches.size() - 1; i >= 0; i--) {
         OrganizationBatch b = batches.get(i);
         if (isUndoable(b))
            return Optional.of(b);
      }
      return Optional.empty();
   }

   @Override
   public synchronized List<OrganizationBatch> findAllBatches() {
      if (!Files.exists(historyFile))
         return List.of();
      try {
         return MAPPER.readValue(
            historyFile.toFile(),
            new TypeReference<List<OrganizationBatch>>() {});
      }
      catch (Exception ex) {
         throw new IllegalStateException(
            "Erro ao ler arquivo de histórico JSON: " + ex.getMessage(), ex);
      }
   }

   public static boolean isUndoable(OrganizationBatch batch) {
      if (batch == null || batch.moves() == null)
         return false;
      if (batch.status() != BatchStatus.CONCLUIDO &&
          batch.status() != BatchStatus.PARCIAL)
         return false;
      return batch.moves().stream().anyMatch(ExecutedMoveRecord::completed);
   }

   private void writeJsonList(Path file, Object data) {
      try {
         Files.createDirectories(appDataDir);
         MAPPER.writerWithDefaultPrettyPrinter()
               .writeValue(file.toFile(), data);
      }
      catch (IOException ex) {
         throw new IllegalStateException(
            "Falha ao gravar arquivo de estado JSON em '" + file + "': " +
            ex.getMessage(), ex);
      }
   }
}
