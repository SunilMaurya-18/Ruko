package in.ruko.pipeline;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.ruko.infra.config.AnalyzeProps;
import in.ruko.pipeline.InputRejectedException.Reason;
import org.junit.jupiter.api.Test;

class InputGuardTest {

    private final InputGuard guard = new InputGuard(new AnalyzeProps(4000, 20, 0.6, 65536));

    @Test
    void acceptsExactlyMaxChars() {
        assertThatCode(() -> guard.check("a".repeat(4000))).doesNotThrowAnyException();
    }

    @Test
    void rejectsOneOverMaxChars() {
        assertRejected("a".repeat(4001), Reason.TOO_LONG);
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        assertThatCode(() -> guard.check("😀".repeat(4000))).doesNotThrowAnyException();
        assertRejected("😀".repeat(4001), Reason.TOO_LONG);
    }

    @Test
    void acceptsDevanagariAndNewlines() {
        assertThatCode(() -> guard.check("गारंटी के साथ\nहर महीने 20%\r\n")).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyOrBlank() {
        assertRejected(null, Reason.EMPTY);
        assertRejected("  \n ", Reason.EMPTY);
    }

    @Test
    void rejectsUnpairedSurrogatesAndReplacementCharacters() {
        assertRejected("bad \uD800 text", Reason.INVALID_ENCODING);
        assertRejected("bad \uDC00 text", Reason.INVALID_ENCODING);
        assertRejected("mojibake \uFFFD here", Reason.INVALID_ENCODING);
    }

    @Test
    void rejectsNul() {
        assertRejected("text\u0000more", Reason.BINARY);
    }

    @Test
    void rejectsHighControlCharacterRatioButToleratesStrays() {
        assertThatCode(() -> guard.check("a normal message with one bell\u0007 in it")).doesNotThrowAnyException();
        assertRejected("\u0001\u0002\u0003\u0004abc", Reason.BINARY);
    }

    private void assertRejected(String text, Reason reason) {
        assertThatThrownBy(() -> guard.check(text))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> org.assertj.core.api.Assertions
                        .assertThat(e.reason()).isEqualTo(reason));
    }
}
