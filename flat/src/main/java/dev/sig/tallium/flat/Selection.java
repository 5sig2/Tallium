package dev.sig.tallium.flat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;


public final class Selection {
    public static final String GAME = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
        .getMetadata().getVersion().getFriendlyString();
    public static final String GROUP = read("META-INF/tallium/versions.properties").getProperty(GAME);
    public static final Properties TABLE = read("META-INF/tallium/groups/" + GROUP + ".properties");
    static {
        int required=Integer.parseInt(TABLE.getProperty("java"));
        if(Runtime.version().feature()<required)
            throw new IllegalStateException("Tallium on Minecraft "+GAME+" requires Java "+required+" or newer.");
    }
    public static Properties read(String resource) {
        try (InputStream input = Selection.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing Tallium compatibility table: " + resource);
            var value = new Properties(); value.load(input); return value;
        } catch (IOException error) { throw new IllegalStateException("Cannot read Tallium compatibility table", error); }
    }
    private Selection() {}
}
