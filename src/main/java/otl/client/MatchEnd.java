package otl.client;

/** PGM match chat lines, shared by Bow Accuracy, Auto GG and Team Bar so they cannot drift apart. */
final class MatchEnd {
    private MatchEnd() {}

    /** True for "Game over!", "Tied!", "Your team won!", "<map> wins!" and "<player> has ended the match.". */
    static boolean is(String text){
        return text != null && text.matches("(?i)^(?:Game over!|Tied!|Your team won!|Your team lost!?|[^\\r\\n]{1,80} wins?!|[A-Za-z0-9_]{1,16} has ended the match\\.)$");
    }

    /** True for the lines PGM sends when the next match begins. */
    static boolean started(String text){
        return text != null && (kickoff(text) || text.startsWith("Playing "));
    }

    /** The first tick of play; "Playing <map>" comes earlier, while the countdown still runs. */
    static boolean kickoff(String text){
        return text != null && (text.equalsIgnoreCase("The match has started!") || text.equalsIgnoreCase("Go!"));
    }

    /**
     * True for the sound PGM plays to every participant when a match finishes: the winners hear
     * the wither die, the losers hear one spawn. The chat lines above are translated per player,
     * so on a non-English client they never arrive in these words; the sound is the same in every
     * language. A map that spawns a real wither would also match, which costs one wrong gg.
     */
    static boolean endedBySound(String name){
        if(name == null) return false;
        String sound = name.toLowerCase(java.util.Locale.ROOT);
        return sound.endsWith("wither.death") || sound.endsWith("wither.spawn");
    }
}
