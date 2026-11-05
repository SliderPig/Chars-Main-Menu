package com.example.charmenu.client;

import com.example.charmenu.CharMenu;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Settings saved in config/charmenu.properties. Also changeable from the small cat button on the menu, or the Config button in the Mods list.
public final class MenuConfig {
    public static boolean enabled = true;    // false: the plain vanilla menu
    public static boolean animation = true;  // false: no flying, a soft fade instead
    public static boolean highQuality = true;
    public static boolean music = false;
    public static boolean ambience = true;

    private MenuConfig() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("charmenu.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            Properties p = new Properties();
            p.load(r);
            enabled = flag(p, "enabled", enabled);
            animation = flag(p, "animation", animation);
            highQuality = !"low".equalsIgnoreCase(p.getProperty("quality", "high").trim());
            music = flag(p, "music", music);
            ambience = flag(p, "ambience", ambience);
        } catch (Exception e) {
            CharMenu.LOG.warn("Couldn't read {}: {}", f, e.toString());
        }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("animation", Boolean.toString(animation));
        p.setProperty("quality", highQuality ? "high" : "low");
        p.setProperty("music", Boolean.toString(music));
        p.setProperty("ambience", Boolean.toString(ambience));
        try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Char's Main Menu. enabled=false gives the plain vanilla menu. quality = high or low.");
        } catch (Exception e) {
            CharMenu.LOG.warn("Couldn't save menu settings: {}", e.toString());
        }
    }
}
