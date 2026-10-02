package in.ruko.api;

import in.ruko.api.dto.TtsRequest;
import in.ruko.infra.AsrGateFilter;
import in.ruko.infra.config.FeatureFlags;
import in.ruko.pipeline.Lang;
import in.ruko.voice.AudioRejectedException;
import in.ruko.voice.VoiceService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {

    public static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

    private final VoiceService voice;
    private final FeatureFlags flags;

    public VoiceController(VoiceService voice, FeatureFlags flags) {
        this.voice = voice;
        this.flags = flags;
    }

    /** Audio for one catalogue script, or 503 {@code {fallback: "browser_tts"}}. */
    @PostMapping(path = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TtsRequest request) {
        byte[] audio = voice.speak(request.scriptKey(), request.lang(), request.counts().toScriptCounts());
        return ResponseEntity.ok().contentType(AUDIO_WAV).body(audio);
    }

    /**
     * Off unless {@code ruko.features.asr}. {@link AsrGateFilter} enforces the flag and consent before the upload is
     * read; they are checked again here in case the route is ever reached another way. The audio is not kept.
     */
    @PostMapping(path = "/asr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Transcript asr(@RequestHeader(name = AsrGateFilter.CONSENT_HEADER, required = false) String consent,
                          @RequestPart("audio") MultipartFile audio,
                          @RequestParam(defaultValue = "hi") Lang lang) throws IOException {
        if (!flags.asr()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (!AsrGateFilter.CONSENT.equals(consent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (audio.isEmpty()) {
            throw new AudioRejectedException();
        }
        return new Transcript(voice.transcribe(audio.getBytes(), lang));
    }

    public record Transcript(String transcript) {
        @Override
        public String toString() {
            return "Transcript[chars=" + (transcript == null ? 0 : transcript.length()) + "]";
        }
    }
}
