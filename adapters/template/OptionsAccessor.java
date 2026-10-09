package dev.sig.tallium.mixin;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("keyMappings") void tallium$setKeys(KeyMapping[] keys);
}
