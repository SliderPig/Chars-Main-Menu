package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.sounds.SoundSource;

// Hooks the menu into the game. While a world is loaded the only thing left running is one quick check per tick.
public final class ClientHooks {
    private static boolean inWorld;
    // other mods that take over the main menu: if one of these is here, this mod stays out of its way
    private static final String[] MENU_MODS = {"fancymenu", "custommainmenu", "mainmenu", "custommenu"};
    private static boolean stepAside;

    private ClientHooks() {}

    public static void start() {
        MenuConfig.load();
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientHooks::shaders);
        modBus.addListener(ClientHooks::reloads);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::opening);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientHooks::screenInit);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::tick);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::renderTick);
        MinecraftForge.EVENT_BUS.addListener(ClientHooks::sound);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MenuSettingsScreen(parent)));
        for (String id : MENU_MODS) {
            if (ModList.get().isLoaded(id)) {
                stepAside = true;
                CharMenu.LOG.info("{} is installed and looks after the main menu, so Char's Main Menu stays out of the way", id);
            }
        }
    }

    private static void shaders(RegisterShadersEvent event) {
        Shaders.register(event);
    }

    private static void reloads(RegisterClientReloadListenersEvent event) {
        // new resource packs mean new block textures: the scene gets rebuilt next time it's needed
        event.registerReloadListener((ResourceManagerReloadListener) manager -> SceneEngine.freeAll());
    }

    private static void opening(ScreenEvent.Opening event) {
        if (stepAside || !MenuConfig.enabled || SceneEngine.failed()) return;
        // only exactly vanilla's own title screen: if another mod swapped in its own, leave that alone
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == TitleScreen.class) {
            event.setNewScreen(new CharTitleScreen(true));
        }
    }

    private static void screenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CharTitleScreen title) {
            try {
                title.adopt();
            } catch (Throwable t) {
                SceneEngine.fail(t);
            }
        }
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean now = Minecraft.getInstance().level != null;
        if (now != inWorld) {
            inWorld = now;
            if (now) {
                // into a world: give back everything the menu was holding on to
                SceneEngine.freeAll();
                MenuSounds.stopAll();
                Director.get().resetToFront();
            }
        }
    }

    private static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) MenuHooks.newFrame();
    }

    // With the menu's own music on, the game's menu music waits outside.
    private static void sound(PlaySoundEvent event) {
        if (!MenuConfig.music || event.getSound() == null) return;
        if (event.getSound().getSource() == SoundSource.MUSIC && !CharMenu.MODID.equals(event.getSound().getLocation().getNamespace()) && MenuHooks.active()) {
            event.setSound(null);
        }
    }

    public static boolean listsSeeThrough(AbstractSelectionList<?> list) {
        return MenuHooks.active();
    }
}
