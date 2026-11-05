package com.example.charmenu;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The menu only exists on a PC. On a server this class does nothing at all.
@Mod(CharMenu.MODID)
public final class CharMenu {
    public static final String MODID = "charmenu";
    public static final Logger LOG = LoggerFactory.getLogger("charmenu");

    public CharMenu() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> com.example.charmenu.client.ClientHooks::start);
    }
}
