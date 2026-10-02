package in.ruko.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.ruko.infra.config.BhashiniProps;
import in.ruko.pipeline.Lang;
import in.ruko.support.BhashiniStub;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The ULCA config-then-compute flow against a local stub. */
class BhashiniClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final BhashiniStub stub = BhashiniStub.start();
    private final BhashiniClient client = new BhashiniClient(props(stub.configUrl()));

    @AfterEach
    void stop() {
        stub.close();
    }

    private static BhashiniProps props(String configUrl) {
        return new BhashiniProps(true, TIMEOUT, BhashiniStub.USER, BhashiniStub.KEY, configUrl, "pipeline-1",
                Duration.ofSeconds(10));
    }

    @Test
    void synthesisesTheGivenTextAndCachesTheRoutePerLanguage() throws Exception {
        assertThat(client.synthesize("पैसा भेजने से पहले रुकें।", Lang.HI, TIMEOUT)).isEqualTo(BhashiniStub.WAV);
        client.synthesize("दूसरी लाइन।", Lang.HI, TIMEOUT);
        client.synthesize("Stop before you send any money.", Lang.EN, TIMEOUT);

        assertThat(stub.spoken).containsExactly("पैसा भेजने से पहले रुकें।", "दूसरी लाइन।", "Stop before you send any money.");
        assertThat(stub.configCalls).hasValue(2);
        assertThat(stub.inferenceCalls).hasValue(3);
    }

    @Test
    void transcribesWavAudio() throws Exception {
        stub.transcript = "guaranteed returns join now";

        assertThat(client.transcribe(BhashiniStub.WAV, Lang.EN, TIMEOUT)).isEqualTo("guaranteed returns join now");
        assertThat(stub.heard).hasSize(1);
        assertThat(stub.heard.getFirst()).isEqualTo(BhashiniStub.WAV);
    }

    @Test
    void refusesAnInferenceEndpointOutsideBhashini() {
        stub.mode = BhashiniStub.Mode.UNTRUSTED_CALLBACK;

        assertThatThrownBy(() -> client.synthesize("text", Lang.HI, TIMEOUT))
                .isInstanceOf(IOException.class).hasMessageContaining("trusted");
        assertThat(stub.inferenceCalls).hasValue(0);
    }

    @Test
    void providerErrorsCarryTheStatusOnlyAndDropTheRoute() throws Exception {
        client.synthesize("warm up", Lang.HI, TIMEOUT);
        stub.mode = BhashiniStub.Mode.DOWN;

        assertThatThrownBy(() -> client.synthesize("text", Lang.HI, TIMEOUT))
                .isInstanceOf(IOException.class).hasMessage("Bhashini inference returned status 500");

        stub.mode = BhashiniStub.Mode.OK;
        client.synthesize("again", Lang.HI, TIMEOUT);
        assertThat(stub.configCalls).hasValue(2);
    }

    @Test
    void wrongCredentialsFailAtTheConfigCall() {
        BhashiniClient wrong = new BhashiniClient(new BhashiniProps(true, TIMEOUT, "someone", "wrong-key",
                stub.configUrl(), "pipeline-1", Duration.ofSeconds(10)));

        assertThatThrownBy(() -> wrong.synthesize("text", Lang.HI, TIMEOUT))
                .isInstanceOf(IOException.class).hasMessage("Bhashini config returned status 401");
    }

    @Test
    void plainHttpIsRefusedExceptToLocalhost() {
        BhashiniClient insecure = new BhashiniClient(props("http://bhashini.example/config"));

        assertThatThrownBy(() -> insecure.synthesize("text", Lang.HI, TIMEOUT))
                .isInstanceOf(IOException.class).hasMessage("Bhashini URL must use https");
        assertThat(stub.configCalls).hasValue(0);
    }
}
