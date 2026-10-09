package dev.sig.tallium.flat;

import net.fabricmc.api.ClientModInitializer;


public final class Bootstrap implements ClientModInitializer {
    @Override public void onInitializeClient() {
        try {
            ((ClientModInitializer) Class.forName("dev.sig.tallium.adapter.TalliumClient", true,
                Bootstrap.class.getClassLoader()).getConstructor().newInstance()).onInitializeClient();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Tallium startup failed", error); }
    }
}
