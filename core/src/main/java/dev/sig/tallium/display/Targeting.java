package dev.sig.tallium.display;


public final class Targeting {
    private Targeting() {}
    public static double score(double cosine,double distance,double radius,double degrees,boolean exact) {
        if(!Double.isFinite(cosine)||!Double.isFinite(distance)||distance<0||distance>radius||cosine<=0)return Double.POSITIVE_INFINITY;
        double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))));
        if(!exact&&angle>degrees)return Double.POSITIVE_INFINITY;
        return (exact?0:1)+angle/180+distance/Math.max(1,radius)*.0001;
    }
}
