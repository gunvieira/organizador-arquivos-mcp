package br.edu.ifpr.mcp_organizer.services.duplicates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Implementação de {@link HashStrategy} utilizando o algoritmo SHA-256.
 */
public class Sha256HashStrategy implements HashStrategy {

   private static final String ALGORITHM = "SHA-256";
   private static final int BUFFER_SIZE  = 8_192;

   @Override
   public String computePartialHash(Path file, int maxBytes) throws IOException {
      if (file == null)
         throw new IllegalArgumentException("O caminho do arquivo não pode ser nulo.");
      if (maxBytes <= 0)
         throw new IllegalArgumentException("O limite de bytes deve ser positivo.");

      MessageDigest digest = newDigest();
      byte[] buffer = new byte[Math.min(BUFFER_SIZE, maxBytes)];
      int remaining = maxBytes;

      try (InputStream is = Files.newInputStream(file)) {
         while (remaining > 0) {
            int toRead = Math.min(buffer.length, remaining);
            int read = is.read(buffer, 0, toRead);
            if (read == -1)
               break;
            digest.update(buffer, 0, read);
            remaining -= read;
         }
      }
      return HexFormat.of().formatHex(digest.digest());
   }

   @Override
   public String computeFullHash(Path file) throws IOException {
      if (file == null)
         throw new IllegalArgumentException("O caminho do arquivo não pode ser nulo.");

      MessageDigest digest = newDigest();
      byte[] buffer = new byte[BUFFER_SIZE];

      try (InputStream is = Files.newInputStream(file)) {
         int read;
         while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
         }
      }
      return HexFormat.of().formatHex(digest.digest());
   }

   private static MessageDigest newDigest() {
      try {
         return MessageDigest.getInstance(ALGORITHM);
      }
      catch (NoSuchAlgorithmException ex) {
         throw new IllegalStateException(
            "Algoritmo SHA-256 não disponível na JVM.", ex);
      }
   }
}
