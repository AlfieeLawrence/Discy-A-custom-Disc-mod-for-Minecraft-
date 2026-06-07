package net.discy.core.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.discy.Discy;
import net.discy.core.util.DiscyIdentifier;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads {@code data/discy/permanent_discs.json} and registers vanilla {@link RecordItem} discs.
 *
 * <p>Each disc needs matching assets: {@code sounds/<sound>.ogg}, a {@code sounds.json} entry,
 * {@code textures/item/<texture>.png}, and lang strings.
 */
public final class PermanentDiscRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<RegistrySupplier<Item>> DISCS = new ArrayList<>();
    private static final List<PermanentDiscDefinition> DEFINITIONS = new ArrayList<>();

    private PermanentDiscRegistry() {}

    public record PermanentDiscDefinition(
            String id,
            String sound,
            String texture,
            int lengthSeconds,
            String displayNameKey,
            String descriptionKey
    ) {
        public ResourceLocation itemId() {
            return new DiscyIdentifier(id);
        }

        public ResourceLocation texturePath() {
            return new DiscyIdentifier("textures/item/" + texture + ".png");
        }
    }

    /** Call before {@link ObjectRegistry#init()} and {@link SoundEventRegistry#init()}. */
    public static void loadAndRegister() {
        DISCS.clear();
        DEFINITIONS.clear();

        JsonObject root = readJson();
        if (root == null || !root.has("discs")) {
            return;
        }

        JsonArray discs = root.getAsJsonArray("discs");
        for (JsonElement element : discs) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String id = text(obj, "id");
            if (id.isBlank()) continue;

            String sound = text(obj, "sound", id);
            String texture = text(obj, "texture", id);
            int length = obj.has("length_seconds") ? obj.get("length_seconds").getAsInt() : 180;

            RegistrySupplier<net.minecraft.sounds.SoundEvent> soundEvent =
                    SoundEventRegistry.registerPermanent(sound);
            RegistrySupplier<Item> item = ObjectRegistry.ITEM_REGISTRAR.register(
                    new DiscyIdentifier(id),
                    () -> new RecordItem(1, soundEvent.get(), ObjectRegistry.getSettings().stacksTo(1), length));

            DISCS.add(item);
            DEFINITIONS.add(new PermanentDiscDefinition(
                    id, sound, texture, length,
                    "item.discy." + id,
                    "item.discy." + id + ".desc"));
            LOGGER.info("Discy: registered permanent disc {}", id);
        }
    }

    public static List<RegistrySupplier<Item>> getDiscs() {
        return Collections.unmodifiableList(DISCS);
    }

    public static List<PermanentDiscDefinition> getDefinitions() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    private static JsonObject readJson() {
        try (InputStream is = PermanentDiscRegistry.class.getResourceAsStream("/data/discy/permanent_discs.json")) {
            if (is == null) {
                LOGGER.warn("Discy: permanent_discs.json not found");
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("Discy: could not read permanent_discs.json: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonObject obj, String key) {
        return text(obj, key, "");
    }

    private static String text(JsonObject obj, String key, String fallback) {
        if (!obj.has(key)) return fallback;
        return obj.get(key).getAsString();
    }
}
