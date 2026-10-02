package in.ruko.guardrail;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** L5: links only to allowlisted hosts or tel:1930, and plain text only (no HTML, Markdown, or script schemes). */
final class LinkRule implements LintRule {

    private static final Pattern MARKUP = Pattern.compile(
            "<[A-Za-z/!?]|&#?[A-Za-z0-9]+;|\\]\\(|\\*\\*|__|`|(?m)^\\s{0,3}#{1,6}\\s|(?i)(?:javascript|vbscript|data):");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)[^\\s\"“”'‘’<>]+");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}@._/-])((?:[a-z0-9-]+\\.)+[a-z]{2,})(?![\\p{L}\\p{N}_-])");
    private static final Pattern TEL = Pattern.compile("(?i)tel:[^\\s]*");

    private final OutboundLinkPolicy links;

    LinkRule(OutboundLinkPolicy links) {
        this.links = links;
    }

    @Override
    public LintCode code() {
        return LintCode.L5;
    }

    @Override
    public void check(Draft draft, LintContext ctx, List<Violation> out) {
        for (Text text : draft.texts()) {
            if (MARKUP.matcher(text.value()).find() || badLink(text.value())) {
                out.add(new Violation(code(), text.field()));
            }
        }
    }

    private boolean badLink(String text) {
        Matcher url = URL.matcher(text);
        while (url.find()) {
            String link = url.group().replaceAll("[.,;:!?)]+$", "");
            String absolute = link.regionMatches(true, 0, "www.", 0, 4) ? "https://" + link : link;
            if (!links.allows(absolute)) {
                return true;
            }
        }
        String withoutUrls = URL.matcher(text).replaceAll(" ");
        Matcher domain = DOMAIN.matcher(withoutUrls);
        while (domain.find()) {
            if (!links.allowsHost(domain.group(1))) {
                return true;
            }
        }
        Matcher tel = TEL.matcher(text);
        while (tel.find()) {
            if (!OutboundLinkPolicy.TEL_1930.equals(tel.group().replaceAll("[.,;:!?)]+$", ""))) {
                return true;
            }
        }
        return false;
    }
}
