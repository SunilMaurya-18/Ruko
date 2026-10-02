package in.ruko.pipeline;

import in.ruko.extract.patterns.SharedPatterns;
import in.ruko.extract.patterns.SharedPatterns.PiiRule;
import in.ruko.pipeline.MappedText.Edit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Replaces phone, Aadhaar-like, account, PAN, and OTP numbers with placeholders, in the rule order of
 * {@code shared/patterns/pii.v0.json} (phone and Aadhaar before account). The PWA applies the same file.
 */
@Component
public class PiiMasker {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\[(?:phone|acct|aadhaar|pan|otp)]", Pattern.CASE_INSENSITIVE);

    private final List<PiiRule> rules;

    public PiiMasker(SharedPatterns patterns) {
        this.rules = patterns.piiRules();
    }

    public MappedText mask(MappedText text) {
        MappedText masked = text.replaceAll(PLACEHOLDER, match -> match.group().toUpperCase(Locale.ROOT));
        for (PiiRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(masked.value());
            List<Edit> edits = new ArrayList<>();
            while (matcher.find()) {
                int start = rule.keepPrefixGroup() ? matcher.end(1) : matcher.start();
                edits.add(new Edit(start, matcher.end(), rule.placeholder()));
            }
            masked = masked.apply(edits);
        }
        return masked;
    }

    public String mask(String text) {
        return mask(MappedText.of(text)).value();
    }
}
