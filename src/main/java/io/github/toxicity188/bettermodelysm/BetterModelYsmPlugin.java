package io.github.toxicity188.bettermodelysm;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.manager.ModelManager;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.util.List;
import java.util.ServiceLoader;

public final class BetterModelYsmPlugin extends JavaPlugin {
    private List<ImageReaderSpi> imageReaders = List.of();

    @Override
    public void onLoad() {
        // Paper's context class loader does not necessarily see plugin ImageIO services.
        imageReaders = ServiceLoader.load(ImageReaderSpi.class, getClassLoader()).stream()
            .map(ServiceLoader.Provider::get)
            .filter(reader -> reader.getClass().getName().startsWith("com.twelvemonkeys."))
            .toList();
        imageReaders.forEach(IIORegistry.getDefaultInstance()::registerServiceProvider);
        BetterModel.platform().manager(ModelManager.class)
            .addImporter("ysm", new YsmImporter(getDataFolder().toPath().resolve("converted"), getLogger()));
    }

    @Override
    public void onDisable() {
        imageReaders.forEach(IIORegistry.getDefaultInstance()::deregisterServiceProvider);
        imageReaders = List.of();
    }
}

