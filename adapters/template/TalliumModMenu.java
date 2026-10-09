package dev.sig.tallium.adapter;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;


public final class TalliumModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory(){return TalliumScreen::new;}
}
