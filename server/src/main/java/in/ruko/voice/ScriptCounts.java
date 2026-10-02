package in.ruko.voice;

/** The result counts a band script reads out. Bounded so the TTS cache key space stays small. */
public record ScriptCounts(int redFlags, int couldntVerify, int reassuring) {

    public static final int MAX = 99;

    public ScriptCounts {
        if (outOfRange(redFlags) || outOfRange(couldntVerify) || outOfRange(reassuring)) {
            throw new IllegalArgumentException("count out of range");
        }
    }

    private static boolean outOfRange(int count) {
        return count < 0 || count > MAX;
    }
}
