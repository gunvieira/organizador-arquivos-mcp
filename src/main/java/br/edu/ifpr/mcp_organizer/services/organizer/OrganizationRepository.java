package br.edu.ifpr.mcp_organizer.services.organizer;

import java.util.List;
import java.util.Optional;

import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationBatch;
import br.edu.ifpr.mcp_organizer.services.organizer.model.OrganizationPlan;

/**
 * Repositório (padrão Repository) para persistência de planos de organização
 * ({@link OrganizationPlan}) e histórico/journal de lotes executados
 * ({@link OrganizationBatch}).
 */
public interface OrganizationRepository {

   /**
    * Salva ou atualiza um plano de organização.
    */
   void savePlan(OrganizationPlan plan);

   /**
    * Busca um plano de organização pelo {@code plan_id}.
    */
   Optional<OrganizationPlan> findPlanById(String planId);

   /**
    * Lista todos os planos salvos.
    */
   List<OrganizationPlan> findAllPlans();

   /**
    * Salva ou atualiza um lote de execução (journal) no histórico.
    */
   void saveBatch(OrganizationBatch batch);

   /**
    * Busca um lote específico pelo {@code batch_id}.
    */
   Optional<OrganizationBatch> findBatchById(String batchId);

   /**
    * Busca o último lote aplicado que ainda pode ser desfeito
    * (status {@code CONCLUIDO} ou {@code PARCIAL} com movimentos concluídos).
    */
   Optional<OrganizationBatch> findLastUndoableBatch();

   /**
    * Retorna todo o histórico de lotes em ordem cronológica de criação.
    */
   List<OrganizationBatch> findAllBatches();
}
