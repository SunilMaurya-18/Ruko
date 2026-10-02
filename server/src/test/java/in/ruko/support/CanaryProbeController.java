package in.ruko.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint shaped like the future analyze request, so error paths can be exercised before Phase 1. */
@RestController
public class CanaryProbeController {

    public static final String PATH = "/api/v1/_probe";

    public record ProbeRequest(
            @NotBlank @Size(max = 4000) String text,
            @NotBlank @Pattern(regexp = "hi|en") String lang) {
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> accept(@Valid @RequestBody ProbeRequest request) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping(PATH + "/boom")
    public ResponseEntity<Void> boom() {
        throw new IllegalStateException("failure while handling " + Canary.VALUE);
    }
}
