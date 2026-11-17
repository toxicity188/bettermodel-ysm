package io.github.toxicity188.bettermodelysm;

import com.google.gson.JsonParser;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelConfig;
import kr.toxicity.model.api.BetterModelPlatform;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.ModelImporter;
import kr.toxicity.model.api.manager.ModelManager;
import kr.toxicity.model.api.platform.PlatformAdapter;
import kr.toxicity.model.api.platform.PlatformItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class YsmImporterTest {
    @TempDir Path directory;
    private YsmImporter importer;
    private Logger logger;
    private byte[] encrypted;

    @BeforeEach
    void setup() throws Exception {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        importer = new YsmImporter(directory.resolve("converted"), logger);
        encrypted = EncryptedFixture.create();
        var importers = new ConcurrentHashMap<String, ModelImporter>();
        var manager = proxy(ModelManager.class, (instance, method, args) -> switch (method.getName()) {
            case "addImporter" -> { importers.put((String) args[0], (ModelImporter) args[1]); yield null; }
            case "importer" -> importers.get(args[0]);
            default -> null;
        });
        manager.addImporter("ysm", importer);
        var config = proxy(BetterModelConfig.class, (instance, method, args) ->
            switch (method.getName()) { case "enableStrictLoading" -> false; case "lerpFrameTime" -> 50; default -> null; });
        var air = proxy(PlatformItemStack.class, (instance, method, args) -> method.getName().equals("isAir") ? true : null);
        var adapter = proxy(PlatformAdapter.class, (instance, method, args) -> method.getName().equals("air") ? air : null);
        BetterModel.register(proxy(BetterModelPlatform.class, (instance, method, args) -> switch (method.getName()) {
            case "config" -> config;
            case "adapter" -> adapter;
            case "manager" -> manager;
            default -> null;
        }));
    }

    @Test
    void decryptsFileAndLoadsThroughBetterModelRegistryAndDefaultImporter() throws Exception {
        var source = Files.write(directory.resolve("fixture.ysm"), encrypted);
        var asset = ModelAsset.of(source);
        var result = asset.toResult();
        var output = importer.outputPath(asset);
        assertEquals(asset.name(), result.blueprint().name());
        assertFalse(result.blueprint().elements().isEmpty());
        assertFalse(result.blueprint().textures().isEmpty());
        assertTrue(Files.isRegularFile(output));
        var direct = ModelImporter.BBMODEL.load(ModelAsset.of(asset.name(), () -> Files.newInputStream(output)));
        assertEquals(result.blueprint().name(), direct.blueprint().name());
        assertEquals(result.errors(), direct.errors());
    }

    @Test
    void keepsUserEditsAndDoesNotOpenChangedSourceAcrossReloads() throws Exception {
        var asset = ModelAsset.of("fixture", "ysm", encrypted);
        importer.load(asset);
        var output = importer.outputPath(asset);
        var json = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        json.addProperty("user_note", "keep my edits");
        json.getAsJsonObject("resolution").addProperty("width", 64);
        var edited = json.toString();
        Files.writeString(output, edited);
        var changed = ModelAsset.of("fixture", "ysm", () -> { throw new IOException("must not open source"); });
        var reloaded = new YsmImporter(directory.resolve("converted"), logger);
        assertEquals(64, reloaded.load(changed).blueprint().resolution().width());
        assertEquals(edited, Files.readString(output));
    }

    @Test
    void reconvertsOnlyAfterUserMovesOutputAway() throws Exception {
        var asset = ModelAsset.of("fixture", "ysm", encrypted);
        importer.load(asset);
        var output = importer.outputPath(asset);
        var backup = Files.move(output, output.resolveSibling("backup.bbmodel"));
        var before = Files.readAllBytes(backup);
        importer.load(asset);
        assertTrue(Files.exists(output));
        assertArrayEquals(before, Files.readAllBytes(backup));
    }

    @Test
    void parallelLoadsAndImporterInstancesConvertSameModelOnce() throws Exception {
        var opens = new AtomicInteger();
        var asset = ModelAsset.of("fixture", "ysm", () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(encrypted);
        });
        var other = new YsmImporter(directory.resolve("converted"), logger);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 24; i++) {
                var loader = i % 2 == 0 ? importer : other;
                results.add(executor.submit(() -> assertEquals("fixture", loader.load(asset).blueprint().name())));
            }
            for (var result : results) result.get();
        }
        assertEquals(1, opens.get());
        try (var files = Files.list(directory.resolve("converted"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void separatesSameNamesAndConfinesUntrustedNames() throws Exception {
        var first = Files.createDirectories(directory.resolve("a")).resolve("model.ysm");
        var second = Files.createDirectories(directory.resolve("b")).resolve("model.ysm");
        Files.write(first, encrypted);
        Files.write(second, encrypted);
        var a = ModelAsset.of(first);
        var b = ModelAsset.of(second);
        assertNotEquals(importer.outputPath(a), importer.outputPath(b));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> importer.load(a));
            var right = executor.submit(() -> importer.load(b));
            assertEquals("model", left.get().blueprint().name());
            assertEquals("model", right.get().blueprint().name());
        }
        var hostile = ModelAsset.of(directory.resolve("../unsafe/한글 model.ysm").toFile());
        assertEquals(directory.resolve("converted"), importer.outputPath(hostile).getParent());
        assertTrue(importer.outputPath(hostile).getFileName().toString().matches("[a-zA-Z0-9_-]+\\.bbmodel"));
    }

    @Test
    void closesSourceAndLogsCauseWithoutLeavingPartialOutput() throws Exception {
        var closed = new AtomicBoolean();
        var asset = ModelAsset.of("broken", "ysm", () -> new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        });
        var records = new ArrayList<LogRecord>();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var exception = assertThrows(IllegalStateException.class, () -> importer.load(asset));
        assertTrue(closed.get());
        assertNotNull(exception.getCause());
        assertTrue(records.stream().anyMatch(record -> record.getThrown() == exception.getCause()));
        assertFalse(Files.exists(importer.outputPath(asset)));
        try (var files = Files.list(directory.resolve("converted"))) { assertEquals(0, files.count()); }
    }

    @Test
    void preservesInvalidEditedOutputInsteadOfReplacingIt() throws Exception {
        var asset = ModelAsset.of("fixture", "ysm", encrypted);
        importer.load(asset);
        var output = importer.outputPath(asset);
        Files.writeString(output, "{unfinished user edit");
        assertThrows(IllegalStateException.class, () -> importer.load(asset));
        assertEquals("{unfinished user edit", Files.readString(output));
    }

    @Test
    void closesSuccessfulSource() {
        var closed = new AtomicBoolean();
        importer.load(ModelAsset.of("fixture", "ysm", () -> new ByteArrayInputStream(encrypted) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        }));
        assertTrue(closed.get());
    }

    @Test
    void loadsSuppliedEncryptedModelOrOriginalFixture() throws Exception {
        var path = System.getProperty("ysmTestModel");
        var source = path != null ? Path.of(path) : Files.write(directory.resolve("fixture.ysm"), encrypted);
        var asset = ModelAsset.of(source);
        var result = importer.load(asset);
        assertEquals(asset.name(), result.blueprint().name());
        assertFalse(result.blueprint().elements().isEmpty());
        assertTrue(Files.size(importer.outputPath(asset)) > 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}




