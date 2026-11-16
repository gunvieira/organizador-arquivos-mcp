package br.edu.ifpr.mcp_organizer.services.classification;

import java.nio.file.Path;

/**
 * Estratégia (padrão Strategy) para classificar um arquivo em uma categoria.
 */
public interface FileClassifier {

   /**
    * Classifica um arquivo representado pelo seu caminho.
    *
    * @param file caminho do arquivo
    * @return nome da categoria atribuída
    */
   String classify(Path file);

   /**
    * Classifica um arquivo a partir do seu nome.
    *
    * @param fileName nome do arquivo
    * @return nome da categoria atribuída
    */
   String classifyByName(String fileName);
}
