package io.github.toxicity188.bettermodelysm;

import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.ModelImporter;
import kr.toxicity.model.api.data.raw.ModelLoadResult;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

public final class YsmImporter implements ModelImporter {
    // Shared across importer instances so reloads cannot race publication of the same file.
    private static final Object[] LOCKS = IntStream.range(0, 64).mapToObj(_ -> new Object()).toArray();
    private final Path directory;
    private final Logger logger;

    public YsmImporter(Path directory, Logger logger) {
        this.directory = directory.toAbsolutePath().normalize();
        this.logger = logger;
    }

    @Override
    @NotNull
    public ModelLoadResult load(@NotNull ModelAsset asset) {
        var output = outputPath(asset);
        synchronized (LOCKS[Math.floorMod(output.hashCode(), LOCKS.length)]) {
            try {
                if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Converted model is not a regular file: " + output);
                    }
                    return loadBbmodel(asset, output);
                }
                Files.createDirectories(directory);
                var temporary = Files.createTempFile(directory, ".ysm-", ".tmp");
                try {
                    try (var input = asset.supplier().get()) {
                        var parsed = YsmConverter.parse(input);
                        parsed.warnings().forEach(warning -> logger.warning(asset.rawName() + ": " + warning));
                        var converted = BlockbenchConverter.convertWithWarnings(parsed.resources());
                        converted.warnings().forEach(warning -> logger.warning(asset.rawName() + ": " + warning));
                        converted.write(temporary);
                    }
                    var result = loadBbmodel(asset, temporary);
                    // Same-directory move without REPLACE_EXISTING preserves any existing user file.
                    Files.move(temporary, output);
                    logger.info("Converted " + asset.rawName() + " to " + output);
                    return result;
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException | RuntimeException exception) {
                logger.log(Level.SEVERE, "Unable to load YSM " + asset.rawName() + " using " + output, exception);
                throw new IllegalStateException("Unable to load YSM: " + asset.rawName(), exception);
            }
        }
    }

    Path outputPath(ModelAsset asset) {
        var safeName = asset.name().replaceAll("[^a-zA-Z0-9_-]", "_");
        safeName = safeName.substring(0, Math.min(48, safeName.length()));
        try {
            var identity = (asset.rawName() + '\0' + asset.name()).getBytes(StandardCharsets.UTF_8);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity));
            return directory.resolve(safeName + "-" + hash + ".bbmodel");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("Java requires SHA-256", exception);
        }
    }

    private static ModelLoadResult loadBbmodel(ModelAsset source, Path file) throws IOException {
        return ModelImporter.BBMODEL.load(ModelAsset.of(source.name(), Files.size(file), () -> Files.newInputStream(file)));
    }
}
