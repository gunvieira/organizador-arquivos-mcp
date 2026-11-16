package br.edu.ifpr.mcp_organizer.services.duplicates;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Estratégia (padrão Strategy) para cálculo de hash parcial e completo de
 * arquivos, utilizada no pipeline de detecção de duplicados.
 */
public interface HashStrategy {

   /**
    * Calcula o hash parcial (primeiros {@code maxBytes} bytes) do arquivo.
    *
    * @param file     caminho do arquivo
    * @param maxBytes quantidade máxima de bytes iniciais a ler
    * @return representação hexadecimal do hash parcial
    * @throws IOException se ocorrer erro de leitura
    */
   String computePartialHash(Path file, int maxBytes) throws IOException;

   /**
    * Calcula o hash completo de todo o conteúdo do arquivo.
    *
    * @param file caminho do arquivo
    * @return representação hexadecimal do hash completo
    * @throws IOException se ocorrer erro de leitura
    */
   String computeFullHash(Path file) throws IOException;
}
