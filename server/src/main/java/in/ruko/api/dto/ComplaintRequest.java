package in.ruko.api.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import in.ruko.pipeline.Lang;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Locale;

/**
 * {@code POST /api/v1/complaint/draft}: structured facts only. Every field is a date, a whole number of rupees, or a
 * fixed choice, so nothing the user wrote in their own words can reach the server or come back in the draft. Any
 * field not declared here is rejected.
 */
public record ComplaintRequest(
        @NotNull Lang lang,
        @NotNull LocalDate date,
        @NotNull @Min(1) @Max(MAX_AMOUNT) Long amount,
        @NotNull Channel channel,
        @NotNull Platform platform,
        @NotNull PayeeIdType payeeIdType) {

    public static final long MAX_AMOUNT = 1_000_000_000L;

    public enum Channel {
        UPI, BANK_TRANSFER, CARD, WALLET, CASH_DEPOSIT, OTHER;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Platform {
        WHATSAPP, TELEGRAM, INSTAGRAM, FACEBOOK, YOUTUBE, SMS, PHONE_CALL, WEBSITE, APP, OTHER;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum PayeeIdType {
        UPI_ID, BANK_ACCOUNT, PHONE_NUMBER, QR_CODE, WEBSITE, APP, OTHER;

        @JsonValue
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
