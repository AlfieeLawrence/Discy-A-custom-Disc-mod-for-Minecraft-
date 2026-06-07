package net.discy.core.client.resource;

import net.minecraft.client.Minecraft;
import net.discy.core.library.SongInfo;
import net.discy.core.library.SongLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds the client dynamic sound pack when the song library changes. */
public final class DynamicSoundPackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSoundPackManager.class);
    private static volatile DynamicSoundPack pack;

    private DynamicSoundPackManager() {}

    public static DynamicSoundPack getPack() {
        return pack;
    }

    public static void rebuild() {
        List<SongInfo> songs = new ArrayList<>(SongLibrary.get().getAllSongs());
        pack = new DynamicSoundPack(songs);
        LOGGER.debug("Dynamic sound pack rebuilt ({} song(s))", songs.size());
    }

    /** Rebuild pack data and reload client resources so SoundManager picks up new events. */
    public static void rebuildAndReload() {
        rebuild();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(mc::reloadResourcePacks);
        }
    }
}
