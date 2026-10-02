package in.ruko.rules;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import in.ruko.api.dto.AnalyzeRequest;
import in.ruko.content.I18nBundle;
import in.ruko.pipeline.AnalysisContext;
import in.ruko.pipeline.AnalysisService;
import in.ruko.pipeline.Lang;
import in.ruko.pipeline.Source;
import in.ruko.snapshot.SebiSnapshotIndex;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TRD §9 readiness: compiles the shipped rules again (schema, regexes, catalogue keys) and runs the readiness probes
 * through that fresh copy with no snapshot, so a dated snapshot added at deploy time cannot change the outcome.
 * Records no metrics and writes no logs; probe texts are fixed fixtures, never request content.
 */
@Component
public class RulesReadiness {

    public static final String PROBES = "readiness/readiness.v0.json";
    static final int MIN_PROBES = 3;

    private static final SebiSnapshotIndex NO_SNAPSHOT = SebiSnapshotIndex.parse("{}");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public record Expected(String band, String contentClass, List<String> signals) {
    }

    public record Probe(String id, String lang, String source, String text, Expected expected) {

        AnalyzeRequest request() {
            return new AnalyzeRequest(text, Lang.valueOf(lang.toUpperCase(Locale.ROOT)),
                    Source.valueOf(source.toUpperCase(Locale.ROOT)), null);
        }
    }

    private record ProbesFile(int version, String note, List<Probe> probes) {
    }

    private final I18nBundle i18n;
    private final AnalysisService analysis;
    private final List<Probe> probes;

    @Autowired
    public RulesReadiness(I18nBundle i18n, AnalysisService analysis) {
        this(i18n, analysis, probes());
    }

    RulesReadiness(I18nBundle i18n, AnalysisService analysis, List<Probe> probes) {
        this.i18n = i18n;
        this.analysis = analysis;
        this.probes = List.copyOf(probes);
    }

    public static List<Probe> probes() {
        ProbesFile file;
        try {
            file = MAPPER.readValue(RuleLoader.readResource(PROBES), ProbesFile.class);
        } catch (IOException e) {
            throw new IllegalStateException(PROBES + ": not a valid probes file", e);
        }
        if (file.probes() == null || file.probes().size() < MIN_PROBES) {
            throw new IllegalStateException(PROBES + ": needs at least " + MIN_PROBES + " probes");
        }
        return List.copyOf(file.probes());
    }

    /** Ids of the probes whose band, content class, or signal ids differ; empty when ready. */
    public List<String> failures() {
        RuleSet rules = RuleLoader.load(RuleLoader.readResource(RuleLoader.RESOURCE), i18n);
        SignalEngine engine = new SignalEngine(rules, NO_SNAPSHOT);
        ContentClassifier classifier = new ContentClassifier(rules);
        List<String> failed = new ArrayList<>();
        for (Probe probe : probes) {
            AnalysisContext ctx = analysis.prepare(probe.request());
            RuleText text = RuleText.of(ctx);
            List<SignalHit> hits = engine.evaluate(text);
            Band band = BandCalculator.band(hits, ctx.unreadable());
            ContentClass contentClass = ctx.unreadable() ? ContentClass.UNKNOWN : classifier.classify(text, hits);
            TreeSet<String> ids = hits.stream().map(SignalHit::id).collect(Collectors.toCollection(TreeSet::new));
            if (!band.wire().equals(probe.expected().band())
                    || !contentClass.wire().equals(probe.expected().contentClass())
                    || !ids.equals(new TreeSet<>(probe.expected().signals()))) {
                failed.add(probe.id());
            }
        }
        return failed;
    }
}
