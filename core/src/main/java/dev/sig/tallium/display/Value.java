package dev.sig.tallium.display;

public record Value(State state,long count,String reason) {
    public enum State { KNOWN, ESTIMATED, UNKNOWN, UNSUPPORTED }
    public boolean hasCount(){return state==State.KNOWN||state==State.ESTIMATED;}
    public static Value estimated(long n){return new Value(State.ESTIMATED,n,"");}
    public static Value known(long n){return new Value(State.KNOWN,n,"");}
    public static Value unknown(String reason){return new Value(State.UNKNOWN,0,reason);}
    public static Value unsupported(String reason){return new Value(State.UNSUPPORTED,0,reason);}
    public String text(){return switch(state){case KNOWN,ESTIMATED->Long.toString(count);case UNKNOWN->"Unknown";case UNSUPPORTED->"Unsupported";};}
}
