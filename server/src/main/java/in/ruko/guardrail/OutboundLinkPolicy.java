package in.ruko.guardrail;

import in.ruko.infra.config.LinksProps;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The only places Ruko may point a user: HTTPS on the configured hosts ({@code ruko.links.allow}), and
 * {@code tel:1930} as a telephone action. Links found in a message are never fetched and never allowed here.
 */
@Component
public final class OutboundLinkPolicy {

    public static final String TEL_1930 = "tel:1930";

    private final Set<String> hosts;

    public OutboundLinkPolicy(LinksProps props) {
        this.hosts = props.allow().stream().map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> hosts() {
        return hosts;
    }

    public boolean allowsHost(String host) {
        return host != null && hosts.contains(host.toLowerCase(Locale.ROOT));
    }

    public boolean allows(String link) {
        if (TEL_1930.equals(link)) {
            return true;
        }
        try {
            URI uri = new URI(link);
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && allowsHost(uri.getHost());
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }
}
