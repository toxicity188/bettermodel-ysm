package io.github.toxicity188.bettermodelysm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.spi.ImageReaderSpi;
import java.io.ByteArrayInputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class PluginJarTest {
    @TempDir Path directory;
    private static final String INTERNAL = "io.github.toxicity188.bettermodelysm.internal.";

    @Test
    void bundlesOnlyConverterAndDeclaresProvidedLibraries() throws Exception {
        try (var jar = new JarFile(System.getProperty("pluginJar"))) {
            var entries = jar.stream().map(java.util.zip.ZipEntry::getName).toList();
            assertFalse(entries.stream().anyMatch(name -> name.startsWith("org/bukkit/")
                || name.startsWith("io/papermc/") || name.startsWith("kr/toxicity/model/")
                || name.startsWith("com/google/gson/") || name.startsWith("com/twelvemonkeys/")
                || name.startsWith("io/github/toxicity188/ysmconverter/")
                || name.startsWith("com/github/luben/zstd/")
                || name.startsWith(INTERNAL.replace('.', '/') + "gson/")
                || name.startsWith(INTERNAL.replace('.', '/') + "twelvemonkeys/")));
            assertNotNull(jar.getEntry("META-INF/THIRD_PARTY_LICENSES.md"));
            assertNotNull(jar.getEntry("META-INF/licenses/YSMParser-MIT.txt"));
            assertNull(jar.getEntry("META-INF/services/javax.imageio.spi.ImageReaderSpi"));
            assertFalse(entries.stream().anyMatch(name -> name.contains("libzstd-jni")));
            try (var input = jar.getInputStream(jar.getJarEntry("plugin.yml"))) {
                var descriptor = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(descriptor.contains("depend:\n- BetterModel"));
                assertTrue(descriptor.contains("libraries:\n"));
                assertTrue(descriptor.contains("com.github.luben:zstd-jni:1.5.7-25"));
                assertTrue(descriptor.contains("com.twelvemonkeys.imageio:imageio-core:3.15.3"));
                assertTrue(descriptor.contains("com.twelvemonkeys.imageio:imageio-jpeg:3.15.3"));
                assertTrue(descriptor.contains("com.twelvemonkeys.imageio:imageio-webp:3.15.3"));
                assertFalse(descriptor.contains("com.google.code.gson:gson"));
                assertTrue(descriptor.contains("api-version: 1.21.4"));
                assertTrue(descriptor.contains("io.github.toxicity188.bettermodelysm.BetterModelYsmPlugin"));
            }
        }
    }

    @Test
    void jarUsesProvidedLibrariesForConversionAndCodecs() throws Exception {
        var url = Path.of(System.getProperty("pluginJar")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{url}, getClass().getClassLoader())) {
            var converter = loader.loadClass(INTERNAL + "ysmconverter.api.YsmConverter");
            assertSame(loader, converter.getClassLoader());
            assertSame(com.google.gson.Gson.class, loader.loadClass("com.google.gson.Gson"));
            var modelPath = System.getProperty("ysmTestModel");
            var encrypted = modelPath == null ? EncryptedFixture.create() : Files.readAllBytes(Path.of(modelPath));
            var resources = converter.getMethod("decrypt", java.io.InputStream.class)
                .invoke(null, new ByteArrayInputStream(encrypted));
            var blockbench = loader.loadClass(INTERNAL + "ysmconverter.blockbench.BlockbenchConverter");
            var converted = blockbench.getMethod("convertWithWarnings", java.util.Map.class).invoke(null, resources);
            var output = directory.resolve("isolated.bbmodel");
            converted.getClass().getMethod("write", Path.class).invoke(converted, output);
            assertTrue(Files.size(output) > 0);
            var providers = ServiceLoader.load(ImageReaderSpi.class, loader).stream().map(ServiceLoader.Provider::get).toList();
            assertTrue(providers.stream().anyMatch(provider -> provider.getClass().getName().contains(".webp.")));
            assertTrue(providers.stream().anyMatch(provider -> provider.getClass().getName().contains(".jpeg.")));
            var zstd = loader.loadClass("com.github.luben.zstd.Zstd");
            var original = new byte[]{1, 2, 3, 4, 5};
            var compressed = (byte[]) zstd.getMethod("compress", byte[].class).invoke(null, (Object) original);
            var decoded = (byte[]) zstd.getMethod("decompress", byte[].class, int.class)
                .invoke(null, compressed, original.length);
            assertArrayEquals(original, decoded);
        }
    }
}


