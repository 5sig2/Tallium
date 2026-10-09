package dev.sig.tallium.adapter;
import java.util.*;

public final class UiText {
    private UiText() {}
    private static final Map<String,String> VALUES=Map.ofEntries(
        Map.entry("true","On"),Map.entry("false","Off"),Map.entry("REMAINING","Remaining"),Map.entry("USED","Used"),
        Map.entry("YOURSELF","Yourself"),Map.entry("OTHERS","Other players"),Map.entry("EVERYONE","Everyone"),Map.entry("SELECTED","Selected players"),Map.entry("UNRESOLVED","Choose players"),
        Map.entry("ROW","Row"),Map.entry("GRID","Column / Grid"),Map.entry("CYCLE","Cycle"),Map.entry("TOTAL","Total"),
        Map.entry("NAMETAG","Nametag"),Map.entry("PLAYER_LIST","Player list"),Map.entry("DEFAULT","Default"),Map.entry("RESOURCE_PACK","Resource pack"),
        Map.entry("INHERIT","Inherited"),Map.entry("PLAIN","Plain"),Map.entry("COMPACT","Compact"),Map.entry("CHAT","Chat"),Map.entry("PLAYERS","Players menu"),Map.entry("POPUP","Popup"),
        Map.entry("MAIN_OFFHAND","Inventory and offhand"),Map.entry("HOTBAR","Hotbar"),Map.entry("SCREEN","Screen"),Map.entry("XP","XP bar"),
        Map.entry("SUPPORTED","Supported"),Map.entry("UNKNOWN","Unknown"),Map.entry("UNSUPPORTED","Unsupported"));
    public static String value(Object value){String text=String.valueOf(value);return VALUES.getOrDefault(text,text);}
    public static String label(String text){
        String result=text;for(var entry:VALUES.entrySet())result=result.replaceAll("(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(entry.getKey())+"(?![A-Za-z0-9_])",java.util.regex.Matcher.quoteReplacement(entry.getValue()));return result;
    }
    private static final Map<String,String> HELP=Map.ofEntries(
        Map.entry("Tracking","Record item uses. Pausing keeps existing counts."),
        Map.entry("Measurement","Remaining is your inventory count. Used is the session count; arrows count shots."),
        Map.entry("Nametag","Show this item beside player names."),
        Map.entry("Player Scope","Choose which players to count. Remaining applies to your inventory only."),
        Map.entry("Mode","Row, grid, rotating items, or one combined count."),
        Map.entry("Surface","Choose where to show the group."),
        Map.entry("Columns","Use 1 for a column, or 2–16 for a grid."),
        Map.entry("Icons","Use the default icons or your resource pack."),
        Map.entry("Two Values","Show remaining and used together for your own counters. Unavailable for totals or other player scopes."),
        Map.entry("Display","Show Remaining, Total, Remaining | Total, or alternate between them."),
        Map.entry("Corner Radius","Zero gives square corners."),
        Map.entry("Number Position","Place the number on or beside the item."),
        Map.entry("Background Opacity","Background transparency."),
        Map.entry("Inspect to Render Distance","Inspect loaded players within render distance."),
        Map.entry("Aim Tolerance (degrees)","Select players within this angle from the crosshair. Walls block selection."),
        Map.entry("No Target Sound","Play a sound when no player is found."),
        Map.entry("Popup Duration (seconds)","How long the player popup stays visible."),
        Map.entry("Skip Zero","Skip empty items when cycling."),
        Map.entry("All Empty","Hide an empty cycle or show zero."),
        Map.entry("Hide Zero","Hide entries with a count of zero."),
        Map.entry("Opacity","Text and background transparency. Item icons are unchanged."),
        Map.entry("Inventory Scope","Count your inventory and offhand, or just the hotbar."),
        Map.entry("Inspection Range","Maximum inspection distance in blocks."),
        Map.entry("Summary Rows","Maximum rows in chat summaries."),
        Map.entry("Advanced Filters","Match potion identity, effect, amplifier or exact duration. Empty fields match any value."),
        Map.entry("Potion identity","Registry ID such as minecraft:strong_strength. Leave blank to match any potion."),
        Map.entry("Effect family","Effect registry ID such as minecraft:strength. Leave blank to match any effect."),
        Map.entry("Amplifier (0 = I)","Effect level uses a zero-based value: 0 is I, 1 is II. Leave blank for any level."),
        Map.entry("Duration (ticks)","Exact effect duration in game ticks; 20 ticks is one second. Leave blank for any duration."),
        Map.entry("Apply","Save changes."),
        Map.entry("Cancel","Discard changes."),
        Map.entry("Reset Group Usage","Reset matching usage across players and profiles. Inventory counts are unchanged."),
        Map.entry("Reset This Item's Usage","Reset matching usage across all players and profiles. Inventory counts are unchanged."),
        Map.entry("Open Controls","Change keybindings in Minecraft Controls."),
        Map.entry("Detector Capabilities","View supported items and tracking limits."),
        Map.entry("Recover Configuration","Archive the unreadable configuration and save the staged settings. The archived original is preserved."));
    public static String help(String text){String key=text.contains(": ")?text.substring(0,text.indexOf(": ")):text;return HELP.getOrDefault(key,label(text));}
    public static String defaultValue(String label){return switch(label){case "Opacity","Text Scale","Scale (0.25–4)"->"1.0";case "Icon Size"->"16";case "Spacing","Padding","Columns"->"2";case "Interval (1–60)"->"5";case "Inspection Range"->"32";case "Summary Rows"->"12";case "Potion identity","Effect family","Amplifier (0 = I)","Duration (ticks)"->"";default->null;};}
}
