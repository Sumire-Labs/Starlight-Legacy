package jp.s12kuma01.starlightlegacy;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class StarlightMod {

    public static final Logger LOGGER = LogManager.getLogger("StarlightLegacy");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Starlight-Legacy: Rewrites the light engine to fix lighting performance and lighting errors");
    }
}
