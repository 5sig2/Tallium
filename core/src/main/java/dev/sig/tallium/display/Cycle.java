package dev.sig.tallium.display;

import java.util.*;
import java.util.function.Predicate;


public final class Cycle {
    private String current;
    private long elapsed, last;
    private int interval;
    public String select(List<String> members, Predicate<String> eligible, int seconds, long now, boolean paused) {
        if(last==0) last=now;
        if(!paused) elapsed += Math.max(0,now-last);
        last=now;
        List<String> available=members.stream().filter(eligible).toList();
        if(interval!=seconds){interval=seconds;elapsed=0;}
        if(available.isEmpty()){current=null;elapsed=0;return null;}
        if(!available.contains(current)){current=available.getFirst();elapsed=0;}
        else if(elapsed>=seconds*1_000_000_000L){current=available.get((available.indexOf(current)+1)%available.size());elapsed=0;}
        return current;
    }
    public void reset(){current=null;elapsed=0;last=0;}
}
