package br.edu.ifpr.mcp_organizer.services.organizer;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Resolvedor de conflitos de nomes no destino: nunca sobrescreve um arquivo
 * existente. Caso o destino já exista, gera um novo nome acrescentando o
 * sufixo {@code "nome (1).ext"}, {@code "nome (2).ext"}, e assim por diante.
 */
public class ConflictResolver {

   /**
    * Resultado da resolução de conflito de caminho de destino.
    *
    * @param resolvedPath caminho livre que não colide com arquivo existente
    * @param renamed      true se o nome original precisou ser renomeado com sufixo {@code (N)}
    */
   public record Resolution(Path resolvedPath, boolean renamed) {}

   /**
    * Resolve o caminho de destino garantindo que nenhum arquivo existente seja
    * sobrescrito.
    *
    * @param desiredTarget caminho de destino inicialmente planejado
    * @return resolução contendo o caminho final livre e indicador de renomeação
    */
   public Resolution resolveTarget(Path desiredTarget) {
      if (desiredTarget == null)
         throw new IllegalArgumentException(
            "O caminho de destino não pode ser nulo.");

      if (!Files.exists(desiredTarget, LinkOption.NOFOLLOW_LINKS))
         return new Resolution(desiredTarget, false);

      Path parent = desiredTarget.getParent();
      String fileName = desiredTarget.getFileName().toString();
      String baseName;
      String extensionWithDot;

      // Separa o nome base da extensão para inserir o sufixo numérico antes do ponto
      int lastDot = fileName.lastIndexOf('.');
      if (lastDot > 0 && lastDot < fileName.length() - 1) {
         baseName         = fileName.substring(0, lastDot);
         extensionWithDot = fileName.substring(lastDot);
      }
      else {
         baseName         = fileName;
         extensionWithDot = "";
      }

      // Incrementa o sufixo (1), (2), ... até encontrar um caminho livre no destino
      int counter = 1;
      while (true) {
         String candidateName = "%s (%d)%s".formatted(
            baseName, counter, extensionWithDot);
         Path candidate = parent != null
                          ? parent.resolve(candidateName)
                          : Path.of(candidateName);
         if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
            return new Resolution(candidate, true);
         counter++;
      }
   }
}
