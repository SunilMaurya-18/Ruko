package in.ruko.guardrail;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L2: Ruko never names a share. A word written in capitals that is a known symbol fails, as does any word of six or
 * more letters that is a known symbol in any case ("Reliance"), and any "NSE:" or "BSE:" prefix.
 */
final class TickerRule implements LintRule {

    private static final Set<String> ALLOWED = Set.of(
            "SEBI", "NSDL", "CDSL", "UPI", "IFSC", "NSE", "BSE", "RBI", "VIP", "QR", "IPO", "F&O", "KYC", "OTP",
            "PAN", "GST", "APK", "SIP", "PMS", "ID", "AI", "SMS", "ATM", "OK");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9&][A-Za-z0-9&-]*[A-Za-z0-9&]|[A-Za-z0-9]");
    private static final Pattern EXCHANGE_PREFIX = Pattern.compile("(?<![\\p{L}\\p{N}])(?:nse|bse)\\s?:\\s?[A-Za-z0-9]",
            Pattern.CASE_INSENSITIVE);
    private static final int ANY_CASE_MIN_LENGTH = 6;

    private final TickerSnapshot tickers;

    TickerRule(TickerSnapshot tickers) {
        this.tickers = tickers;
    }

    @Override
    public LintCode code() {
        return LintCode.L2;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            String own = LintText.unquoted(text.value(), ctx.input());
            if (EXCHANGE_PREFIX.matcher(own).find() || namesTicker(own)) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }

    private boolean namesTicker(String text) {
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase(Locale.ROOT);
            if (ALLOWED.contains(upper) || !tickers.contains(upper)) {
                continue;
            }
            boolean capitals = token.equals(upper) && token.chars().anyMatch(Character::isLetter);
            if (capitals || upper.length() >= ANY_CASE_MIN_LENGTH) {
                return true;
            }
        }
        return false;
    }
}
