package in.ruko.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConfigPropsTest {

    @Autowired AnalyzeProps analyze;
    @Autowired LlmProps llm;
    @Autowired BhashiniProps bhashini;
    @Autowired LinksProps links;
    @Autowired RateLimitProps rateLimit;
    @Autowired FeatureFlags features;

    @Test
    void bootsWithDefaultsWhenNoEnvironmentIsSet() {
        assertThat(analyze.maxChars()).isEqualTo(4000);
        assertThat(analyze.minChars()).isEqualTo(20);
        assertThat(llm.enabled()).isFalse();
        assertThat(llm.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(llm.configured()).isFalse();
        assertThat(bhashini.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(bhashini.configured()).isFalse();
        assertThat(links.allow()).containsExactly("siportal.sebi.gov.in", "scores.sebi.gov.in", "cybercrime.gov.in");
        assertThat(rateLimit.perIpPerMin()).isEqualTo(30);
        assertThat(features.asr()).isFalse();
        assertThat(features.complaint()).isTrue();
        assertThat(features.s10DomainAge()).isFalse();
        assertThat(features.snapshot()).isTrue();
    }

    @Test
    void secretsNeverAppearInToString() {
        var llmProps = new LlmProps(true, Duration.ofSeconds(3), "https://llm.example", "sk-secret-key", "m");
        var bhashiniProps = new BhashiniProps(true, Duration.ofSeconds(3), "user-123", "bh-secret-key");

        assertThat(llmProps.toString()).doesNotContain("sk-secret-key").doesNotContain("llm.example");
        assertThat(bhashiniProps.toString()).doesNotContain("bh-secret-key").doesNotContain("user-123");
    }
}
