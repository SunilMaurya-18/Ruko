package in.ruko.pipeline;

import in.ruko.infra.config.AnalyzeProps;
import in.ruko.pipeline.InputRejectedException.Reason;
import org.springframework.stereotype.Component;

/** At most {@code max-chars} code points of valid Unicode text: no NUL, no unpaired surrogates, little control noise. */
@Component
public class InputGuard {

    static final double MAX_CONTROL_RATIO = 0.05;

    private final int maxChars;

    public InputGuard(AnalyzeProps props) {
        this.maxChars = props.maxChars();
    }

    public void check(String text) {
        if (text == null || text.isBlank()) {
            throw new InputRejectedException(Reason.EMPTY);
        }
        int controls = 0;
        int codePoints = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            codePoints++;
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++;
                continue;
            }
            if (Character.isSurrogate(c) || c == '\uFFFD') {
                throw new InputRejectedException(Reason.INVALID_ENCODING);
            }
            if (c == '\u0000') {
                throw new InputRejectedException(Reason.BINARY);
            }
            if (Character.getType(c) == Character.CONTROL && c != '\n' && c != '\r' && c != '\t') {
                controls++;
            }
        }
        if (codePoints > maxChars) {
            throw new InputRejectedException(Reason.TOO_LONG);
        }
        if (controls > codePoints * MAX_CONTROL_RATIO) {
            throw new InputRejectedException(Reason.BINARY);
        }
    }
}
