package dev.sig.tallium.adapter;
public final class CapabilityScreen extends FormScreen {
    public CapabilityScreen(TalliumScreen parent){super(parent,"Tallium · Detector Capabilities");}
    protected void build(){for(var c:TalliumClient.CAPABILITIES.all()){String name=Character.toUpperCase(c.family().charAt(0))+c.family().substring(1);action(name+": Local "+UiText.value(c.local())+" / Others "+UiText.value(c.remote()),()->minecraft.setScreen(new TextScreen(this,name,c.evidence()+". "+c.gap())));}}
}
