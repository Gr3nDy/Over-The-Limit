package otl.client;

import java.util.regex.Pattern;

final class ServerNoticeRules {
    enum Mode {
        ALL("All notices"),JOINS_LEAVES("Join/leave only"),EXCEPT_JOINS_LEAVES("Except join/leave");
        final String label;Mode(String label){this.label=label;}
        static Mode parse(String value){try{return valueOf(value);}catch(IllegalArgumentException bad){return ALL;}}
    }
    private static final Pattern JOIN_LEAVE=Pattern.compile("^(?:\\[[^\\]\\r\\n]{1,48}\\]\\s*)*<?\\*?[A-Za-z0-9_]{1,16}>? (?:quietly )?(?:joined|left) the (?:game|server)[.!]?$",Pattern.CASE_INSENSITIVE);
    static boolean accepts(String formatted,Mode mode){
        boolean join=JOIN_LEAVE.matcher(ChatRules.plain(formatted).replaceAll("\\p{Zs}"," ").trim()).matches();
        if(!join&&ChatRules.playerMessage(formatted))return false;
        return mode==Mode.ALL||mode==Mode.JOINS_LEAVES&&join||mode==Mode.EXCEPT_JOINS_LEAVES&&!join;
    }
}
