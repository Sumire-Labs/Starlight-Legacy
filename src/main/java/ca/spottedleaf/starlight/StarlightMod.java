package ca.spottedleaf.starlight;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "starlight", name = "Starlight", version = "1.0.0")
public class StarlightMod {

    public static final Logger LOGGER = LogManager.getLogger("Starlight");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Starlight: Rewrites the light engine to fix lighting performance and lighting errors");
    }
}
