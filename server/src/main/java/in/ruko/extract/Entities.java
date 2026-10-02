package in.ruko.extract;

import java.util.List;
import java.util.Map;

/** Phones are reported as a count only: the numbers themselves are masked before extraction. */
public record Entities(
        List<String> upiIds,
        List<String> regNumbers,
        List<String> ifsc,
        List<String> urls,
        int phoneCount,
        List<String> returnClaims,
        List<String> appNames,
        List<String> qrPhrases) {

    public static final List<String> TYPE_IDS = List.of(
            "upi_ids", "reg_numbers", "ifsc", "urls", "return_claims", "app_names", "qr_phrases");

    public static Entities of(Map<String, List<String>> byType, int phoneCount) {
        return new Entities(
                byType.getOrDefault("upi_ids", List.of()),
                byType.getOrDefault("reg_numbers", List.of()),
                byType.getOrDefault("ifsc", List.of()),
                byType.getOrDefault("urls", List.of()),
                phoneCount,
                byType.getOrDefault("return_claims", List.of()),
                byType.getOrDefault("app_names", List.of()),
                byType.getOrDefault("qr_phrases", List.of()));
    }

    public List<String> byType(String typeId) {
        return switch (typeId) {
            case "upi_ids" -> upiIds;
            case "reg_numbers" -> regNumbers;
            case "ifsc" -> ifsc;
            case "urls" -> urls;
            case "return_claims" -> returnClaims;
            case "app_names" -> appNames;
            case "qr_phrases" -> qrPhrases;
            default -> throw new IllegalArgumentException("unknown entity type");
        };
    }
}
