package net.discy.core.library;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SongInfo {
    private final String hash;
    private final String displayName;
    private final int lengthSeconds;
    @Nullable private final ResourceLocation soundId;
    private final String source;

    public SongInfo(String hash, String displayName, int lengthSeconds,
                    @Nullable ResourceLocation soundId, String source) {
        this.hash = Objects.requireNonNull(hash, "hash");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.lengthSeconds = lengthSeconds;
        this.soundId = soundId;
        this.source = source == null ? "external" : source;
    }

    public String hash() { return hash; }
    public String displayName() { return displayName; }
    public int lengthSeconds() { return lengthSeconds; }
    @Nullable public ResourceLocation soundId() { return soundId; }
    public String source() { return source; }

    public String shortHash() {
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }

    @Override
    public String toString() {
        return "SongInfo[" + shortHash() + " '" + displayName + "' " + lengthSeconds + "s from " + source + "]";
    }
}
