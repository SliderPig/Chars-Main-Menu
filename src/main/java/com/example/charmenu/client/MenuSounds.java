package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

// The menu's own small sounds. They all live under Music, so that slider controls every one of them.
public final class MenuSounds {
    private static final ResourceLocation AMBIENCE = new ResourceLocation(CharMenu.MODID, "menu.ambience");
    private static final ResourceLocation MUSIC = new ResourceLocation(CharMenu.MODID, "menu.music");
    private static final ResourceLocation DOOR = new ResourceLocation(CharMenu.MODID, "menu.door");
    private static final ResourceLocation WHOOSH = new ResourceLocation(CharMenu.MODID, "menu.whoosh");
    private static final ResourceLocation TAP = new ResourceLocation(CharMenu.MODID, "menu.tap");

    private static Loop ambience, music;

    private MenuSounds() {}

    private static final class Loop extends AbstractTickableSoundInstance {
        private final float top;
        private boolean leaving;

        Loop(ResourceLocation id, float top) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.MUSIC, RandomSource.create());
            this.top = top;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void leave() {
            leaving = true;
        }

        void stopNow() {
            stop();
        }

        @Override
        public void tick() {
            if (leaving) {
                volume -= 0.05f * top;
                if (volume <= 0.01f) stop();
            } else if (volume < top) {
                volume = Math.min(top, volume + 0.012f * top);
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private static void play(ResourceLocation id, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, SoundSource.MUSIC, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    public static void door(float volume) {
        play(DOOR, volume, 0.92f + (float) Math.random() * 0.16f);
    }

    public static void whoosh() {
        play(WHOOSH, 0.8f, 1f);
    }

    public static void tap() {
        play(TAP, 0.55f, 1f);
    }

    // Called each frame on the menu: keeps the looping sounds in step with the settings.
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (MenuConfig.ambience) {
            if (ambience == null || ambience.isStopped()) {
                ambience = new Loop(AMBIENCE, 0.75f);
                mc.getSoundManager().play(ambience);
            }
        } else if (ambience != null) {
            ambience.leave();
            ambience = null;
        }
        if (MenuConfig.music) {
            if (music == null || music.isStopped()) {
                music = new Loop(MUSIC, 0.8f);
                mc.getSoundManager().play(music);
                mc.getMusicManager().stopPlaying();
            }
        } else if (music != null) {
            music.leave();
            music = null;
        }
    }

    public static void stopAll() {
        if (ambience != null) ambience.stopNow();
        if (music != null) music.stopNow();
        ambience = null;
        music = null;
    }
}
