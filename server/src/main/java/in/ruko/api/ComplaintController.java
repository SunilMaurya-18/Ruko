package in.ruko.api;

import in.ruko.api.dto.ComplaintRequest;
import in.ruko.content.ComplaintDrafter;
import in.ruko.infra.config.FeatureFlags;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Track B complaint template, on unless {@code ruko.features.complaint=false} (then the route is a 404). */
@RestController
@RequestMapping("/api/v1/complaint")
public class ComplaintController {

    private final ComplaintDrafter drafter;
    private final FeatureFlags flags;

    public ComplaintController(ComplaintDrafter drafter, FeatureFlags flags) {
        this.drafter = drafter;
        this.flags = flags;
    }

    @PostMapping(path = "/draft", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ComplaintDrafter.Draft draft(@Valid @RequestBody ComplaintRequest request) {
        if (!flags.complaint()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return drafter.draft(request);
    }
}
