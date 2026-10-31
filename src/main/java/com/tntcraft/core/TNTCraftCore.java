package com.tntcraft.core;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(TNTCraftCore.MODID)
public class TNTCraftCore {
    public static final String MODID = "tntcraftcore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TNTCraftCore(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Cmds.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Data.load();
        LOGGER.info("TNTCraft Core hazir: ev, ekonomi ve satis komutlari yuklendi.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Data.save();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var p = event.getEntity();
        Data.player(p.getUUID(), p.getName().getString());
        Data.save();
    }
}
