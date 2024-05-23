package fi.dy.masa.tweakeroo.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import fi.dy.masa.tweakeroo.config.FeatureToggle;

@Mixin(DebugHud.class)
public class MixinDebugHud {
    @ModifyArg(method = "getLeftText()Ljava/util/List;", at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", ordinal = 4), index = 1)
    private String format(String s) {
        if (FeatureToggle.TWEAK_PRECISE_COORDINATES.getBooleanValue()) return "XYZ: %f / %f / %f";
        return s;
    }
}
