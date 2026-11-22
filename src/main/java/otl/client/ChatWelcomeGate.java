package otl.client;

import java.util.regex.Pattern;

/** Fail closed: a connection/world has no channel commands until its own welcome arrives. */
final class ChatWelcomeGate {
    private static final Pattern WELCOME=Pattern.compile("^Welcome\\s+to\\s+Overcast\\s+Community\\b.*$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PLAYER_LINE=Pattern.compile("^(?:<[A-Za-z0-9_]{1,16}>\\s*|[A-Za-z0-9_]{1,16}\\s*[:>\\u00bb]\\s*).*$",Pattern.DOTALL);
    private boolean enabled=true,confirmed,title,description;
    boolean enabled(){return enabled;}
    void reset(){enabled=true;confirmed=false;worldChanged();}
    /**
     * A new world is the teleport into a match: full chat is back even before the map's own
     * welcome arrives, which on a translated client never says "Created by" or "Welcome to".
     */
    void worldChanged(){enabled=true;title=false;description=false;}
    boolean accept(String formatted){
        String text=ChatRules.plain(formatted).trim();
        if(!ChatRules.ALL.equals(ChatRules.classify(formatted).key)||PLAYER_LINE.matcher(text).matches())return false;
        boolean before=enabled;
        for(String line:text.split("[\\r\\n]+")){
            String s=line.trim().toLowerCase(java.util.Locale.ROOT);
            if(s.equals("overcast community"))title=true;
            if(s.matches("^view our /servers or click the beacon[.!]?$"))description=true;
        }
        if(title&&description){enabled=false;confirmed=false;title=description=false;return before;}
        // The map's welcome heading is a strikethrough rule, not a word, so it reads the same on
        // every client: "Created by" and "Welcome to Overcast Community" are translated.
        if(isWelcome(formatted)||TeamBar.welcomeHeading(formatted)!=null||text.matches("(?is)^Created by\\s+.+$")){
            boolean first=!confirmed;enabled=true;confirmed=true;title=description=false;return !before||first;
        }
        return false;
    }
    static boolean isWelcome(String formatted){
        String text=ChatRules.plain(formatted).trim();
        if(!ChatRules.ALL.equals(ChatRules.classify(formatted).key)||PLAYER_LINE.matcher(text).matches())return false;
        for(String line:text.split("[\\r\\n]+"))if(WELCOME.matcher(line.trim()).matches())return true;
        return false;
    }
}
