package dev.sig.tallium.flat;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;


public final class OptionalEntrypoint implements LanguageAdapter {
    @Override public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        try {
            return type.cast(Class.forName(value, true, OptionalEntrypoint.class.getClassLoader())
                .getConstructor().newInstance());
        } catch (ReflectiveOperationException error) { throw new LanguageAdapterException(error); }
    }
}
