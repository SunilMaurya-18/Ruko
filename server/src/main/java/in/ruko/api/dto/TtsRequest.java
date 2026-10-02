package in.ruko.api.dto;

import in.ruko.pipeline.Lang;
import in.ruko.voice.ScriptCounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/voice/tts}: a spoken-script key, the language, and the result counts. There is no text field;
 * any field not declared here is rejected.
 */
public record TtsRequest(
        @NotBlank @Size(max = 64) String scriptKey,
        @NotNull Lang lang,
        @NotNull @Valid Counts counts) {

    public record Counts(
            @NotNull @Min(0) @Max(ScriptCounts.MAX) Integer redFlags,
            @NotNull @Min(0) @Max(ScriptCounts.MAX) Integer couldntVerify,
            @NotNull @Min(0) @Max(ScriptCounts.MAX) Integer reassuring) {

        public ScriptCounts toScriptCounts() {
            return new ScriptCounts(redFlags, couldntVerify, reassuring);
        }
    }
}
