package net.discy.core.client.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.common.collect.ImmutableSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.discy.Discy;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import net.discy.core.util.DiscyIdentifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-only resource pack that exposes uploaded OGGs as vanilla {@code sounds.json}
 * record entries ({@code discy:custom.<hash>}). This is what makes custom discs use
 * {@link net.minecraft.client.sounds.SoundManager} — same range and mod compatibility
 * as vanilla discs (Sophisticated Storage backpacks, Furniture gramophones, etc.).
 */
public final class DynamicSoundPack implements PackResources {
    public static final String PACK_ID = "discy/dynamic_sounds";
    public static final String SOUND_SUBPATH = "custom";

    private final Map<String, byte[]> generated = new HashMap<>();
    private final Map<String, IoSupplier<InputStream>> fileSources = new HashMap<>();
    private final Set<String> namespaces = ImmutableSet.of(Discy.MOD_ID);

    public DynamicSoundPack(List<SongInfo> songs) {
        registerPackMcMeta();
        registerSoundsJson(songs);
        for (SongInfo song : songs) {
            registerSongOgg(song);
        }
    }

    public static String soundEventKey(String hash) {
        return SOUND_SUBPATH + "." + hash;
    }

    private void registerPackMcMeta() {
        JsonObject packSection = new JsonObject();
        packSection.addProperty("description", "Discy runtime songs");
        packSection.addProperty("pack_format", 15);
        JsonObject root = new JsonObject();
        root.add("pack", packSection);
        generated.put("pack.mcmeta", root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void registerSoundsJson(List<SongInfo> songs) {
        JsonObject root = new JsonObject();
        for (SongInfo song : songs) {
            Path audio = SongLibrary.findAudioPath(song.hash());
            if (audio == null) continue;
            if (!audio.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("category", "record");
            JsonArray sounds = new JsonArray();
            JsonObject one = new JsonObject();
            one.addProperty("name", Discy.MOD_ID + ":" + SOUND_SUBPATH + "/" + song.hash());
            one.addProperty("stream", true);
            sounds.add(one);
            entry.add("sounds", sounds);
            root.add(soundEventKey(song.hash()), entry);
        }
        generated.put("assets/" + Discy.MOD_ID + "/sounds.json",
                root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void registerSongOgg(SongInfo song) {
        Path audio = SongLibrary.findAudioPath(song.hash());
        if (audio == null || !Files.isRegularFile(audio)) return;
        if (!audio.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")) return;

        String packPath = "assets/" + Discy.MOD_ID + "/sounds/" + SOUND_SUBPATH + "/" + song.hash() + ".ogg";
        fileSources.put(packPath, () -> Files.newInputStream(audio));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... pathParts) {
        String key = String.join("/", pathParts);
        byte[] bytes = generated.get(key);
        if (bytes != null) {
            return () -> new ByteArrayInputStream(bytes);
        }
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) return null;
        String key = "assets/" + location.getNamespace() + "/" + location.getPath();
        byte[] bytes = generated.get(key);
        if (bytes != null) {
            return () -> new ByteArrayInputStream(bytes);
        }
        return fileSources.get(key);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(Discy.MOD_ID)) return;
        String prefix = "assets/" + namespace + "/" + path;
        for (Map.Entry<String, byte[]> e : generated.entrySet()) {
            String fullKey = e.getKey();
            if (!fullKey.startsWith(prefix)) continue;
            String relative = fullKey.substring(("assets/" + namespace + "/").length());
            ResourceLocation rl = new DiscyIdentifier(relative);
            byte[] bytes = e.getValue();
            output.accept(rl, () -> new ByteArrayInputStream(bytes));
        }
        for (Map.Entry<String, IoSupplier<InputStream>> e : fileSources.entrySet()) {
            String fullKey = e.getKey();
            if (!fullKey.startsWith(prefix)) continue;
            String relative = fullKey.substring(("assets/" + namespace + "/").length());
            ResourceLocation rl = new DiscyIdentifier(relative);
            output.accept(rl, e.getValue());
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? namespaces : Set.of();
    }

    @Override
    public void close() {
    }

    @Override
    public String packId() {
        return PACK_ID;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer.getMetadataSectionName().equals("pack")) {
            return (T) new PackMetadataSection(Component.literal("Discy songs"), 15);
        }
        return null;
    }
}
