package fi.dy.masa.tweakeroo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import fi.dy.masa.tweakeroo.Tweakeroo;
import fi.dy.masa.tweakeroo.config.Configs;
import fi.dy.masa.tweakeroo.config.FeatureToggle;
import fi.dy.masa.tweakeroo.tweaks.RenderTweaks;
import fi.dy.masa.tweakeroo.util.IDecorationEntity;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher
{
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(Entity entityIn, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir)
    {
        boolean isPlayer = (entityIn instanceof PlayerEntity);
        if (entityIn instanceof AbstractDecorationEntity) {
            if (!RenderTweaks.isPositionValidForRendering(((IDecorationEntity) entityIn).getAttatched()))
                cir.setReturnValue(false);
        }
        if (!isPlayer && Configs.Generic.SELECTIVE_BLOCKS_HIDE_ENTITIES.getBooleanValue()) {
            if (!RenderTweaks.isPositionValidForRendering(entityIn.getBlockPos()))
                cir.setReturnValue(false);
        }

        if (Configs.Disable.DISABLE_ENTITY_RENDERING.getBooleanValue() && !isPlayer)
        {
            cir.setReturnValue(false);
        }
        
        if (Configs.Disable.DISABLE_OTHER_PLAYER_RENDERING.getBooleanValue() && isPlayer && !((PlayerEntity)entityIn).isMainPlayer())
        {
            cir.setReturnValue(false);
        }

        if (FeatureToggle.TWEAK_RENDER_ALL_ENTITIES.getBooleanValue())
        {
            cir.setReturnValue(true);
        }

        if (entityIn instanceof FallingBlockEntity && Configs.Disable.DISABLE_FALLING_BLOCK_RENDER.getBooleanValue())
        {
            cir.setReturnValue(false);
        }
        else if (entityIn instanceof ArmorStandEntity && Configs.Disable.DISABLE_ARMOR_STAND_RENDERING.getBooleanValue())
        {
            cir.setReturnValue(false);
        }
        else if (entityIn instanceof ExperienceOrbEntity)
        {
            if (FeatureToggle.TWEAK_RENDER_LIMIT_ENTITIES.getBooleanValue())
            {
                int max = Configs.Generic.RENDER_LIMIT_XP_ORB.getIntegerValue();

                if (max >= 0 && ++Tweakeroo.renderCountXPOrbs > max)
                {
                    cir.setReturnValue(false);
                }
            }
        }
        else if (entityIn instanceof ItemEntity)
        {
            if (FeatureToggle.TWEAK_RENDER_LIMIT_ENTITIES.getBooleanValue())
            {
                int max = Configs.Generic.RENDER_LIMIT_ITEM.getIntegerValue();

                if (max >= 0 && ++Tweakeroo.renderCountItems > max)
                {
                    cir.setReturnValue(false);
                }
            }
        }
        else if (Configs.Disable.DISABLE_DEAD_MOB_RENDERING.getBooleanValue() &&
                 entityIn instanceof LivingEntity && ((LivingEntity) entityIn).getHealth() <= 0f)
        {
            cir.setReturnValue(false);
        }

    }
}
