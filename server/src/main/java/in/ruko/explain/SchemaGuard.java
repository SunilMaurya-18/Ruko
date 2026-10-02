package in.ruko.explain;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import in.ruko.rules.LlmTag;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Validates a model reply against {@code schemas/llm-assist.v1.json} ({@code additionalProperties: false}
 * everywhere). Anything that is not exactly that shape, including a duplicate card id, is rejected whole.
 */
@Component
public class SchemaGuard {

    public static final String SCHEMA = "schemas/llm-assist.v1.json";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final JsonSchema schema;

    public SchemaGuard() {
        try (InputStream in = SchemaGuard.class.getClassLoader().getResourceAsStream(SCHEMA)) {
            if (in == null) {
                throw new IllegalStateException(SCHEMA + ": missing from classpath");
            }
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException(SCHEMA + ": not valid JSON", e);
        }
    }

    public Optional<Assist> parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return Optional.empty();
        }
        JsonNode tree;
        try {
            tree = MAPPER.readTree(reply);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (tree == null || !schema.validate(tree).isEmpty()) {
            return Optional.empty();
        }
        List<LlmTag> tags = new ArrayList<>();
        tree.path("tags").forEach(tag -> tags.add(new LlmTag(tag.path("id").asText(), tag.path("span").asText())));
        Map<String, String> cards = new LinkedHashMap<>();
        for (JsonNode card : tree.path("cards")) {
            if (cards.put(card.path("signal_id").asText(), card.path("text").asText().strip()) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(new Assist(tags, cards));
    }
}
