package in.ruko.content;

import in.ruko.api.dto.ComplaintRequest;
import in.ruko.guardrail.GuardrailLinter;
import in.ruko.pipeline.Lang;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fills the {@code complaint.draft} template from structured facts only. Fails closed: a draft over 200 words or one
 * that does not pass linter checks L1, L3, and L5 is never returned. The PWA builds the same text on the phone
 * ({@code src/recovery/complaint.js}) and uses this route only when online.
 */
@Component
public class ComplaintDrafter {

    public static final int MAX_WORDS = 200;
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    public record Draft(Lang language, String text, int words) {
    }

    /** The date is after today in India, or before {@link #EARLIEST}. */
    public static final class InvalidDateException extends RuntimeException {
        InvalidDateException() {
            super("complaint date out of range", null, false, false);
        }
    }

    private final I18nBundle i18n;
    private final GuardrailLinter linter;
    private final Clock clock;

    @Autowired
    public ComplaintDrafter(I18nBundle i18n, GuardrailLinter linter) {
        this(i18n, linter, Clock.system(INDIA));
    }

    ComplaintDrafter(I18nBundle i18n, GuardrailLinter linter, Clock clock) {
        this.i18n = i18n;
        this.linter = linter;
        this.clock = clock;
    }

    public Draft draft(ComplaintRequest request) {
        if (request.date().isAfter(LocalDate.now(clock)) || request.date().isBefore(EARLIEST)) {
            throw new InvalidDateException();
        }
        Lang lang = request.lang();
        String text = i18n.text(lang, "complaint.draft", Map.of(
                "date", DATE.format(request.date()),
                "amount", rupees(request.amount()),
                "channel", i18n.text(lang, "complaint.channel." + request.channel().wire()),
                "platform", i18n.text(lang, "complaint.platform." + request.platform().wire()),
                "payee", i18n.text(lang, "complaint.payee." + request.payeeIdType().wire())));
        int words = GuardrailLinter.wordCount(text);
        if (words > MAX_WORDS || !linter.lintPlainText(lang, "complaint.draft", text).ok()) {
            throw new IllegalStateException("complaint draft failed its checks");
        }
        return new Draft(lang, text, words);
    }

    /** Indian digit grouping: 1,25,000. */
    static String rupees(long amount) {
        String digits = Long.toString(amount);
        if (digits.length() <= 3) {
            return digits;
        }
        String head = digits.substring(0, digits.length() - 3);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < head.length(); i++) {
            if (i > 0 && (head.length() - i) % 2 == 0) {
                out.append(',');
            }
            out.append(head.charAt(i));
        }
        return out.append(',').append(digits, digits.length() - 3, digits.length()).toString();
    }
}
