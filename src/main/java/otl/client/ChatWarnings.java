package otl.client;

import java.util.Locale;

/** Only anchored server failures are copied; quoted player chat is never treated as a warning. */
final class ChatWarnings {
    static boolean party(String formatted){
        String text=ChatRules.plain(formatted).trim().toLowerCase(Locale.ROOT);
        return text.startsWith("you are not in a party")||text.startsWith("you aren't in a party")||text.startsWith("you must be in a party");
    }
    static boolean privateMessage(String formatted){
        String text=ChatRules.plain(formatted).trim().toLowerCase(Locale.ROOT);
        return text.matches("^(?:player .{1,32} (?:not found|is not online|is offline)|no player .{1,32}(?:found|online)|cannot find player .{1,32}|you (?:cannot|can't) (?:message|msg) yourself|that player is (?:not online|offline)).*$");
    }
}
