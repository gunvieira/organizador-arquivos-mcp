package br.edu.ifpr.mcp_organizer.services.search;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import br.edu.ifpr.mcp_organizer.services.classification.ClassificationRules;

/**
 * Critérios de busca de arquivos construídos pelo padrão Builder.
 */
public final class FileCriteria {

   public static final int DEFAULT_MAX_RESULTS = 20;
   public static final int MAX_RESULTS_LIMIT   = 100;

   private final String nameGlob;
   private final String category;
   private final Set<String> extensions;
   private final Long minSize;
   private final Long maxSize;
   private final LocalDate modifiedAfter;
   private final LocalDate modifiedBefore;
   private final boolean recursive;
   private final int maxResults;

   private FileCriteria(Builder builder) {
      this.nameGlob       = builder.nameGlob;
      this.category       = builder.category;
      this.extensions     = Collections.unmodifiableSet(builder.extensions);
      this.minSize        = builder.minSize;
      this.maxSize        = builder.maxSize;
      this.modifiedAfter  = builder.modifiedAfter;
      this.modifiedBefore = builder.modifiedBefore;
      this.recursive      = builder.recursive;
      this.maxResults     = builder.maxResults;
   }

   public static Builder builder() {
      return new Builder();
   }

   public String getNameGlob() {
      return nameGlob;
   }

   public String getCategory() {
      return category;
   }

   public Set<String> getExtensions() {
      return extensions;
   }

   public Long getMinSize() {
      return minSize;
   }

   public Long getMaxSize() {
      return maxSize;
   }

   public LocalDate getModifiedAfter() {
      return modifiedAfter;
   }

   public LocalDate getModifiedBefore() {
      return modifiedBefore;
   }

   public boolean isRecursive() {
      return recursive;
   }

   public int getMaxResults() {
      return maxResults;
   }

   /**
    * Builder fluente para {@link FileCriteria}.
    */
   public static final class Builder {
      private String nameGlob;
      private String category;
      private final Set<String> extensions = new LinkedHashSet<>();
      private Long minSize;
      private Long maxSize;
      private LocalDate modifiedAfter;
      private LocalDate modifiedBefore;
      private boolean recursive = true;
      private int maxResults = DEFAULT_MAX_RESULTS;

      private Builder() {}

      public Builder nameGlob(String nameGlob) {
         this.nameGlob = (nameGlob == null || nameGlob.isBlank())
                         ? null
                         : nameGlob.trim();
         return this;
      }

      public Builder category(String category) {
         this.category = (category == null || category.isBlank())
                         ? null
                         : category.trim();
         return this;
      }

      public Builder extensions(List<String> exts) {
         this.extensions.clear();
         if (exts != null) {
            for (String ext : exts) {
               String norm = ClassificationRules.normalizeExt(ext);
               if (!norm.isEmpty())
                  this.extensions.add(norm);
            }
         }
         return this;
      }

      public Builder minSize(Long minSize) {
         this.minSize = minSize;
         return this;
      }

      public Builder maxSize(Long maxSize) {
         this.maxSize = maxSize;
         return this;
      }

      public Builder modifiedAfter(LocalDate modifiedAfter) {
         this.modifiedAfter = modifiedAfter;
         return this;
      }

      public Builder modifiedBefore(LocalDate modifiedBefore) {
         this.modifiedBefore = modifiedBefore;
         return this;
      }

      public Builder recursive(boolean recursive) {
         this.recursive = recursive;
         return this;
      }

      public Builder maxResults(int maxResults) {
         this.maxResults = maxResults;
         return this;
      }

      public FileCriteria build() {
         return new FileCriteria(this);
      }
   }
}
