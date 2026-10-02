package in.ruko.voice;

/** The uploaded audio is empty or could not be decoded. Carries nothing from the upload. */
public class AudioRejectedException extends RuntimeException {

    public AudioRejectedException() {
        super("audio could not be decoded", null, false, false);
    }
}
