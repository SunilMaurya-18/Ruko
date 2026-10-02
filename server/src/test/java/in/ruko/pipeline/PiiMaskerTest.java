package in.ruko.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.ruko.extract.patterns.SharedPatterns;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The vectors in shared/fixtures/pii-cases.v0.json are also run against the PWA masker. */
class PiiMaskerTest {

    private static final SharedPatterns PATTERNS = new SharedPatterns();

    private final PiiMasker masker = new PiiMasker(PATTERNS);
    private final TextNormalizer normalizer = new TextNormalizer();

    record Case(String input, String masked) {
        @Override
        public String toString() {
            return input;
        }
    }

    static List<Case> sharedCases() throws IOException {
        try (InputStream in = PiiMaskerTest.class.getClassLoader().getResourceAsStream("fixtures/pii-cases.v0.json")) {
            List<Case> cases = new ArrayList<>();
            for (JsonNode node : new ObjectMapper().readTree(in)) {
                cases.add(new Case(node.path("input").asText(), node.path("masked").asText()));
            }
            return cases;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sharedCases")
    void masksSharedVectorsOnRawText(Case testCase) {
        assertThat(masker.mask(testCase.input())).isEqualTo(testCase.masked());
    }

    @Test
    void masksAfterNormalisationAndRestoresClientPlaceholders() {
        MappedText masked = masker.mask(normalizer.normalize("Already [PHONE]; call ९८७६५४३२१० or 98765\u200B43210"));

        assertThat(masked.value()).isEqualTo("already [PHONE]; call [PHONE] or [PHONE]");
    }

    @Test
    void placeholdersMapBackToTheMaskedOriginalRange() {
        String original = "OTP is 482913 ok";
        MappedText masked = masker.mask(normalizer.normalize(original));

        int otp = masked.value().indexOf("[OTP]");
        assertThat(masked.originalSlice(otp, otp + 5)).isEqualTo("482913");
        assertThat(masked.originalSlice(0, masked.length())).isEqualTo(original);
    }

    @Test
    void aadhaarIsClassifiedBeforeAccount() {
        assertThat(masker.mask("id 234567890123 and a/c 123456789")).isEqualTo("id [AADHAAR] and a/c [ACCT]");
    }

    @Test
    void doesNotMaskRegistrationNumbersOrAmounts() {
        assertThat(masker.mask("INH000012345 fee Rs 1000000000 or ₹ 50000"))
                .isEqualTo("INH000012345 fee Rs 1000000000 or ₹ 50000");
    }
}
