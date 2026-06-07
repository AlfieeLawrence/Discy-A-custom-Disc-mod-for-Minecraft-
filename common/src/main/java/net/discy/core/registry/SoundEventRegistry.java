package net.discy.core.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.discy.Discy;
import net.discy.core.util.DiscyIdentifier;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SoundEventRegistry {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Discy.MOD_ID, Registries.SOUND_EVENT);
    public static final Registrar<SoundEvent> SOUND_EVENT_REGISTRAR = SOUND_EVENTS.getRegistrar();

    /** Silent placeholder for custom/burned discs — real audio is streamed client-side. */
    public static final RegistrySupplier<SoundEvent> CUSTOM_DISC_SOUND = create("custom_disc_sound");
    public static final RegistrySupplier<SoundEvent> SILENT = create("silent");

    private static final Map<String, RegistrySupplier<SoundEvent>> PERMANENT = new HashMap<>();
    private static final Map<ResourceLocation, Path> dynamicSounds = new HashMap<>();

    private static RegistrySupplier<SoundEvent> create(String name) {
        final ResourceLocation id = new DiscyIdentifier(name);
        return SOUND_EVENT_REGISTRAR.register(id, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static RegistrySupplier<SoundEvent> registerPermanent(String soundName) {
        return PERMANENT.computeIfAbsent(soundName, SoundEventRegistry::create);
    }

    public static void registerDynamicSound(ResourceLocation soundId, Path oggFile) {
        dynamicSounds.put(soundId, oggFile);
    }

    public static Path getDynamicSoundFile(ResourceLocation soundId) {
        return dynamicSounds.get(soundId);
    }

    public static void init() {
        SOUND_EVENTS.register();
    }
}
