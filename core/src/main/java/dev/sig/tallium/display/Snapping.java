package dev.sig.tallium.display;


public final class Snapping {
    private Snapping() {}
    public static int axis(double value,int size,int extent,boolean enabled,boolean nearEdge,boolean farEdge) {
        int raw=(int)Math.round(value);if(!enabled)return raw;
        int result=raw,distance=7;
        int[] guides={extent/2,nearEdge?size/2+4:Integer.MIN_VALUE,farEdge?extent-size/2-4:Integer.MIN_VALUE};
        for(int guide:guides){long gap=Math.abs((long)raw-guide);if(gap<distance){result=guide;distance=(int)gap;}}
        return result;
    }
}
