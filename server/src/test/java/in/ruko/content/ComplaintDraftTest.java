package in.ruko.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.ruko.api.dto.ComplaintRequest;
import in.ruko.api.dto.ComplaintRequest.Channel;
import in.ruko.api.dto.ComplaintRequest.PayeeIdType;
import in.ruko.api.dto.ComplaintRequest.Platform;
import in.ruko.guardrail.GuardrailLinter;
import in.ruko.pipeline.Lang;
import in.ruko.support.Pipeline;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plan Phase 5: every draft is at most 200 words and passes L1, L3, and L5, and nothing typed by the user can come
 * back in it. Also keeps {@code shared/fixtures/complaint-drafts.v0.json} current, which the PWA's on-phone template
 * must reproduce (regenerate with {@code -Druko.updateGolden=true}).
 */
class ComplaintDraftTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
    private static final ComplaintDrafter DRAFTER = new ComplaintDrafter(Pipeline.I18N, Pipeline.LINTER,
            Clock.fixed(TODAY.atStartOfDay(ComplaintDrafter.INDIA).toInstant(), ComplaintDrafter.INDIA));
    private static final Path FIXTURE = Path.of("..", "shared", "fixtures", "complaint-drafts.v0.json");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addModule(new JavaTimeModule())
            .build();

    private static ComplaintRequest request(Lang lang, Channel channel, Platform platform, PayeeIdType payee) {
        return new ComplaintRequest(lang, LocalDate.of(2026, 9, 1), 4999L, channel, platform, payee);
    }

    @Test
    void everyCombinationIsShortAndLinterClean() {
        int drafts = 0;
        for (Lang lang : Lang.values()) {
            for (Channel channel : Channel.values()) {
                for (Platform platform : Platform.values()) {
                    for (PayeeIdType payee : PayeeIdType.values()) {
                        ComplaintDrafter.Draft draft = DRAFTER.draft(request(lang, channel, platform, payee));
                        assertThat(draft.words()).isLessThanOrEqualTo(ComplaintDrafter.MAX_WORDS)
                                .isEqualTo(GuardrailLinter.wordCount(draft.text()));
                        assertThat(Pipeline.LINTER.lintPlainText(lang, "complaint", draft.text()).violations()).isEmpty();
                        assertThat(draft.text()).doesNotContain("{").doesNotContain("}");
                        drafts++;
                    }
                }
            }
        }
        assertThat(drafts).isEqualTo(2 * 6 * 10 * 7);
    }

    @Test
    void theRequestCannotCarryFreeText() {
        for (RecordComponent component : ComplaintRequest.class.getRecordComponents()) {
            Class<?> type = component.getType();
            assertThat(type == LocalDate.class || type == Long.class || type.isEnum())
                    .as(component.getName() + " must be a date, a number, or a fixed choice").isTrue();
        }
    }

    @Test
    void theDraftHoldsOnlyCatalogueTextTheDateAndTheAmount() {
        ComplaintDrafter.Draft draft = DRAFTER.draft(new ComplaintRequest(Lang.EN, LocalDate.of(2025, 1, 31), 125000L,
                Channel.BANK_TRANSFER, Platform.TELEGRAM, PayeeIdType.BANK_ACCOUNT));
        String template = Pipeline.I18N.text(Lang.EN, "complaint.draft");
        String rest = draft.text()
                .replace("31-01-2025", "{date}")
                .replace("1,25,000", "{amount}")
                .replace(Pipeline.I18N.text(Lang.EN, "complaint.channel.bank_transfer"), "{channel}")
                .replace(Pipeline.I18N.text(Lang.EN, "complaint.platform.telegram"), "{platform}")
                .replace(Pipeline.I18N.text(Lang.EN, "complaint.payee.bank_account"), "{payee}");
        assertThat(rest).isEqualTo(template);
    }

    @Test
    void datesMustBeRealAndNotInTheFuture() {
        assertThat(DRAFTER.draft(new ComplaintRequest(Lang.HI, TODAY, 1L, Channel.UPI, Platform.SMS, PayeeIdType.UPI_ID)))
                .isNotNull();
        assertThatThrownBy(() -> DRAFTER.draft(new ComplaintRequest(Lang.HI, TODAY.plusDays(1), 1L, Channel.UPI,
                Platform.SMS, PayeeIdType.UPI_ID))).isInstanceOf(ComplaintDrafter.InvalidDateException.class);
        assertThatThrownBy(() -> DRAFTER.draft(new ComplaintRequest(Lang.HI, LocalDate.of(1999, 12, 31), 1L,
                Channel.UPI, Platform.SMS, PayeeIdType.UPI_ID))).isInstanceOf(ComplaintDrafter.InvalidDateException.class);
    }

    @Test
    void amountsUseIndianGrouping() {
        assertThat(ComplaintDrafter.rupees(1)).isEqualTo("1");
        assertThat(ComplaintDrafter.rupees(999)).isEqualTo("999");
        assertThat(ComplaintDrafter.rupees(1000)).isEqualTo("1,000");
        assertThat(ComplaintDrafter.rupees(125000)).isEqualTo("1,25,000");
        assertThat(ComplaintDrafter.rupees(12345678)).isEqualTo("1,23,45,678");
        assertThat(ComplaintDrafter.rupees(ComplaintRequest.MAX_AMOUNT)).isEqualTo("1,00,00,00,000");
    }

    @Test
    void sharedFixtureIsTheServerDraft() throws IOException {
        JsonNode fixture = MAPPER.readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8));
        List<String> lines = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (JsonNode entry : fixture) {
            ComplaintRequest request = MAPPER.treeToValue(entry.path("facts"), ComplaintRequest.class);
            String text = DRAFTER.draft(request).text();
            expected.add(text);
            ObjectNode updated = MAPPER.createObjectNode();
            updated.set("facts", entry.path("facts"));
            updated.put("text", text);
            lines.add("  " + MAPPER.writeValueAsString(updated));
        }
        if (Boolean.getBoolean("ruko.updateGolden")) {
            Files.writeString(FIXTURE, "[\n" + String.join(",\n", lines) + "\n]\n", StandardCharsets.UTF_8);
            fixture = MAPPER.readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8));
        }
        List<String> actual = new ArrayList<>();
        fixture.forEach(entry -> actual.add(entry.path("text").asText()));
        assertThat(actual).as("%s is stale: run with -Druko.updateGolden=true", FIXTURE).isEqualTo(expected);
    }
}
