package io.github.mortuusars.exposure.event;

import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.camera.CameraHelper;
import io.github.mortuusars.exposure.camera.viewfinder.ViewfinderClient;
import io.github.mortuusars.exposure.command.ExposureCommands;
import io.github.mortuusars.exposure.command.ShaderCommand;
import io.github.mortuusars.exposure.command.argument.ShaderLocationArgument;
import io.github.mortuusars.exposure.item.CameraItem;
import io.github.mortuusars.exposure.network.Packets;
import io.github.mortuusars.exposure.util.CameraInHand;
import io.github.mortuusars.exposure.util.ScheduledTasks;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class CommonEvents {
    public static class ModBus {
        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                Packets.register();
                Exposure.Advancements.register();
                Exposure.Stats.register();
            });
        }
    }

    public static class ForgeBus {
        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            ArgumentTypeInfos.registerByClass(ShaderLocationArgument.class, SingletonArgumentInfo.contextFree(ShaderLocationArgument::new));

            ExposureCommands.register(event.getDispatcher());
            ShaderCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void playerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END)
                return;

            Player player = event.player;
            InteractionHand activeHand = CameraInHand.getActiveHand(player);

            if (activeHand == null) {
                if (player.getLevel().isClientSide && ViewfinderClient.isOpen()) {
                    ViewfinderClient.close(player);
//                    player.playSound(Exposure.SoundEvents.VIEWFINDER_CLOSE.get(), 0.35f, player.getLevel().getRandom().nextFloat() * 0.2f + 0.9f);
                }
//                CameraHelper.deactivateAll(player, false);
                return;
            }

            ItemStack itemInHand = player.getItemInHand(activeHand);

            if (activeHand == InteractionHand.OFF_HAND && player.getMainHandItem().getItem() instanceof CameraItem) {
                CameraHelper.deactivateAll(player, false);
            }
            else {
                // Refresh active camera
                ((CameraItem) itemInHand.getItem()).setActive(player, itemInHand, true);

                if (player.getLevel().isClientSide && !ViewfinderClient.isOpen())
                    ViewfinderClient.open(player);
            }
        }

        // IDK why but LevelTickEvent is fired 3 times on the server per 1 on the client.
        // So the solution is to use specific events. This seems to work properly.
        @SubscribeEvent
        public static void serverTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END)
                ScheduledTasks.tick(event);
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END)
                ScheduledTasks.tick(event);
        }

        @SubscribeEvent
        public static void entityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
            Player player = event.getEntity();

            // Interacting with entity when trying to shoot is annoying
            if (CameraInHand.isActive(player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                CameraInHand camera = CameraInHand.ofPlayer(player);
                camera.getStack().use(player.level, player, camera.getHand());
            }
        }

        @SubscribeEvent
        public static void onItemToss(ItemTossEvent event) {
            ItemStack itemStack = event.getEntity().getItem();
            if (itemStack.getItem() instanceof CameraItem cameraItem)
                cameraItem.setActive(event.getPlayer(), itemStack, false);
        }
    }
}
