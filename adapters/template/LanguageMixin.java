package dev.sig.tallium.mixin;
import dev.sig.tallium.adapter.TalliumClient;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientLanguage.class)
public abstract class LanguageMixin {
    @Inject(method="getOrDefault",at=@At("HEAD"),cancellable=true)
    private void tallium$actionName(String key,String fallback,CallbackInfoReturnable<String> ci){
        if(key.startsWith("key.tallium.action.")){String id=key.substring("key.tallium.action.".length());TalliumClient.config.resetActions.stream().filter(a->a.id.equals(id)).findFirst().ifPresent(a->ci.setReturnValue(a.name));}
    }
}
