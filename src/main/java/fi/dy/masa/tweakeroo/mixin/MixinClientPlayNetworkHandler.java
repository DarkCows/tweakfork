package fi.dy.masa.tweakeroo.mixin;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.network.ClientConnection;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import fi.dy.masa.tweakeroo.config.Configs;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import fi.dy.masa.tweakeroo.config.FeatureToggle;
import fi.dy.masa.tweakeroo.data.DataManager;
import fi.dy.masa.tweakeroo.data.ServerDataSyncer;
import fi.dy.masa.tweakeroo.tweaks.PlacementTweaks;
import fi.dy.masa.tweakeroo.tweaks.RenderTweaks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import fi.dy.masa.tweakeroo.util.MiscUtils;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler extends ClientCommonNetworkHandler
{
    protected MixinClientPlayNetworkHandler(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState)
    {
        super(client, connection, connectionState);
    }

    @Shadow
    private ClientWorld world;

    @Shadow
    private int chunkLoadDistance;

    @Shadow
    public abstract DynamicRegistryManager.Immutable getRegistryManager();

    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenScreenListener(OpenScreenS2CPacket packet, CallbackInfo ci) {
        if (!RenderTweaks.onOpenScreen(packet.getName(),packet.getScreenHandlerType(),packet.getSyncId())) {
            ci.cancel();
        }
    }

    @Inject(method = "onInventory", at = @At("HEAD"), cancellable = true)
    private void onInventoryListener(InventoryS2CPacket packet, CallbackInfo ci) {
        if (!RenderTweaks.onInventory(packet.getSyncId(),packet.getContents())) {
            ci.cancel();
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/screen/ScreenHandler;setStackInSlot(IILnet/minecraft/item/ItemStack;)V"),
            cancellable = true)
    private void onHandleSetSlot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci)
    {
        if (PlacementTweaks.shouldSkipSlotSync(packet.getSlot(), packet.getStack()))
        {
            ci.cancel();
        }
    }

    @Inject(method = "onDeathMessage", at = @At(value = "INVOKE", // onCombatEvent
            target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void onPlayerDeath(DeathMessageS2CPacket packetIn, CallbackInfo ci)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (FeatureToggle.TWEAK_PRINT_DEATH_COORDINATES.getBooleanValue() && mc.player != null)
        {
            MiscUtils.printDeathCoordinates(mc);
        }
    }

    @Inject(
            method = "onCommandTree",
            at = @At("RETURN")
    )
    private void onCommandTree(CallbackInfo ci)
    {
        if (FeatureToggle.TWEAK_SERVER_DATA_SYNC.getBooleanValue())
        {
            // when the player becomes OP, the server sends the command tree to the client
            ServerDataSyncer.getInstance().recheckOpStatus();
        }
    }

    @Inject(method = "onCustomPayload", at = @At("HEAD"))
    private void tweakeroo_onCustomPayload(CustomPayload payload, CallbackInfo ci)
    {
        if (payload.getId().id().equals(DataManager.CARPET_HELLO))
        {
            DataManager.getInstance().setHasCarpetServer(true);
        }
        else if (payload.getId().id().equals(DataManager.SERVUX_ENTITY_DATA))
        {
            DataManager.getInstance().setHasServuxServer(true);
        }
    }
    @Inject(
        method = "onEntityStatus",
        at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getActiveTotemOfUndying(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/item/ItemStack;")
    )
    private void onPlayerUseTotemOfUndying(EntityStatusS2CPacket packet, CallbackInfo ci)
    {
        if (FeatureToggle.TWEAK_HAND_RESTOCK.getBooleanValue())
        {
            for (Hand hand : Hand.values())
            {
                if (this.client.player.getStackInHand(hand).isOf(Items.TOTEM_OF_UNDYING))
                {
                    PlacementTweaks.cacheStackInHand(hand);
                    // the slot update packet goes after this packet, let's set it to empty and restock
                    this.client.player.setStackInHand(hand, ItemStack.EMPTY);
                    PlacementTweaks.onProcessRightClickPost(this.client.player, hand);
                }
            }
        }
    }

    @Inject(method = "onBlockEvent", at = @At("HEAD"), cancellable = true)
    private void overrideBlockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_CLIENT_BLOCK_EVENTS.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void onLightUpdateEvent(LightUpdateS2CPacket packet, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_PACKET_LIGHT_UPDATES.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "readLightData", at = @At("HEAD"), cancellable = true)
    private void onReadLightData(int x, int z, LightData data, CallbackInfo ci) {
        if (Configs.Disable.DISABLE_PACKET_LIGHT_UPDATES.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlayerRespawn", at=@At(value = "NEW",
    target="net/minecraft/client/world/ClientWorld"))
    private void onPlayerRespawnInject(PlayerRespawnS2CPacket packet, CallbackInfo ci) {
        RenderTweaks.resetWorld(getRegistryManager(),chunkLoadDistance);
    }


    @Inject(method = "onGameJoin", at=@At(value = "NEW",
    target="net/minecraft/client/world/ClientWorld"))
    private void onGameJoinInject(GameJoinS2CPacket packet, CallbackInfo ci) {
        RenderTweaks.resetWorld(getRegistryManager(),chunkLoadDistance);
    }

    @Inject(method = "onChunkData", at=@At("RETURN"))
    private void onChunkDataInject(ChunkDataS2CPacket packet, CallbackInfo ci) {
        int cx = packet.getChunkX();
		int cz = packet.getChunkZ();
        RenderTweaks.loadFakeChunk(cx, cz);

        if (!FeatureToggle.TWEAK_SELECTIVE_BLOCKS_RENDERING.getBooleanValue()) {
            return;
        }
		WorldChunk worldChunk = this.world.getChunkManager().getWorldChunk(cx, cz);

		if (worldChunk != null) {
            BlockPos.Mutable pos = new BlockPos.Mutable();
			ChunkSection[] sections = worldChunk.getSectionArray();
            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section != null && !section.isEmpty()) {
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                pos.set(x+worldChunk.getPos().getStartX(),y+ this.world.sectionIndexToCoord(i),z+worldChunk.getPos().getStartZ());

                                if (!RenderTweaks.isPositionValidForRendering(pos)) {
                                    BlockEntity be = worldChunk.getBlockEntity(pos);
                                    BlockState state = section.getBlockState(x, y, z);
                                    worldChunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                    RenderTweaks.setFakeBlockState(this.world, pos, state, be);
                                }
                            }
                        }
                    }

                }
            }
		}
    }

    @Inject(method = "onUnloadChunk",at=@At("RETURN"))
    private void onUnloadChunkInject(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        int i = packet.pos().x;
		int j = packet.pos().z;
        RenderTweaks.unloadFakeChunk(i,j);
    }


    @Inject(method = "onChunkLoadDistance",at=@At("RETURN"))
    private void onChunkLoadDistanceInject(ChunkLoadDistanceS2CPacket packet, CallbackInfo ci) {
        RenderTweaks.getFakeWorld().getChunkManager().updateLoadDistance(packet.getDistance());
    }

    @Inject(method = "onChunkRenderDistanceCenter",at=@At("RETURN"))
    private void onChunkRenderDistanceCenterInject(ChunkRenderDistanceCenterS2CPacket packet, CallbackInfo ci) {
        RenderTweaks.getFakeWorld().getChunkManager().setChunkMapCenter(packet.getChunkX(), packet.getChunkZ());
    }

}
