package in.ruko.api.dto;

import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Length and encoding are checked by {@code InputGuard}, which reads the configured limit. */
public record AnalyzeRequest(
        @NotNull String text,
        @NotNull Lang lang,
        @NotNull Source source,
        @DecimalMin("0.0") @DecimalMax("1.0") Double ocrConfidence) {

    @Override
    public String toString() {
        return "AnalyzeRequest[lang=" + lang + ", source=" + source + ", chars=" + (text == null ? 0 : text.length()) + "]";
    }
}
