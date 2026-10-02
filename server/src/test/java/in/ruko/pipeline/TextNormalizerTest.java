package in.ruko.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "dotted app name           | install a.n.y.d.e.s.k now     | install anydesk now",
            "dashed and mixed case     | A-n-Y-d-E-s-K                 | anydesk",
            "spaced letters            | g u a r a n t e e d profit    | guaranteed profit",
            "abbreviation with dots    | S.E.B.I. approved             | sebi. approved",
            "two letters stay          | e.g. and i.e.                 | e.g. and i.e.",
            "upi local part untouched  | pay r.k.s@okaxis              | pay r.k.s@okaxis",
            "domain labels untouched   | www.a.b.c.com                 | www.a.b.c.com",
            "case fold                 | Guaranteed RETURNS            | guaranteed returns",
            "fullwidth digits (NFKC)   | ９８７６５                     | 98765",
    })
    void normalises(String name, String input, String expected) {
        assertThat(normalizer.normalize(input).value()).isEqualTo(expected);
    }

    @Test
    void stripsZeroWidthBidiAndControlCharacters() {
        assertThat(normalizer.normalize("Any\u200BDesk\u200C S\u2060E\uFEFFBI\u202E\u0007").value())
                .isEqualTo("anydesk sebi");
    }

    @Test
    void collapsesSpaceRunsAndTabsButKeepsNewlines() {
        assertThat(normalizer.normalize("a  \t b\r\nnext").value()).isEqualTo("a b\nnext");
    }

    @Test
    void convertsDevanagariDigits() {
        assertThat(normalizer.normalize("₹५००० हर महीने १५%").value()).isEqualTo("₹5000 हर महीने 15%");
    }

    @Test
    void keepsDevanagariWordsIntact() {
        assertThat(normalizer.normalize("है तो भी न व").value()).isEqualTo("है तो भी न व");
    }

    @Test
    void mapsEverySpanBackToTheVerbatimOriginal() {
        String original = "Install A.n.y.D.e.s.k\u200B NOW ९८७६";
        MappedText text = normalizer.normalize(original);
        assertThat(text.value()).isEqualTo("install anydesk now 9876");

        int app = text.value().indexOf("anydesk");
        assertThat(text.originalSlice(app, app + "anydesk".length())).isEqualTo("A.n.y.D.e.s.k");
        int now = text.value().indexOf("now");
        assertThat(text.originalSlice(now, now + 3)).isEqualTo("NOW");
        int digits = text.value().indexOf("9876");
        assertThat(text.originalSlice(digits, digits + 4)).isEqualTo("९८७६");
        assertThat(text.originalSlice(0, text.length())).isEqualTo(original.substring(0, original.length()));
    }

    @Test
    void nfkcComposedDevanagariStillMapsBack() {
        String original = "\u095B\u0930\u0942\u0930";
        MappedText text = normalizer.normalize(original);

        assertThat(text.value()).isEqualTo("\u091C\u093C\u0930\u0942\u0930");
        assertThat(text.originalSlice(0, 2)).isEqualTo("\u095B");
    }
}
