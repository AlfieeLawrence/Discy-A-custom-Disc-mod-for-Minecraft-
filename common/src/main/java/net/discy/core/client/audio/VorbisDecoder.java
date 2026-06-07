package net.discy.core.client.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class VorbisDecoder implements AudioDecoder {

    private final long handle;
    private final int sampleRate;
    private final int channels;
    private final int alFormat;
    private final ByteBuffer ownedFileData;
    private ShortBuffer stereoStaging;

    private boolean closed = false;

    public VorbisDecoder(byte[] oggBytes) throws IOException {
        this.ownedFileData = MemoryUtil.memAlloc(oggBytes.length);
        try {
            this.ownedFileData.put(oggBytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                long h = STBVorbis.stb_vorbis_open_memory(this.ownedFileData, error, null);
                if (h == MemoryUtil.NULL) {
                    throw new IOException("STBVorbis open_memory failed (err " + error.get(0) + ")");
                }
                this.handle = h;
            }
            try (STBVorbisInfo info = STBVorbisInfo.malloc()) {
                STBVorbis.stb_vorbis_get_info(this.handle, info);
                this.sampleRate = info.sample_rate();
                this.channels = info.channels();
            }
            if (channels < 1 || channels > 2) {
                STBVorbis.stb_vorbis_close(this.handle);
                throw new IOException("Unsupported channel count: " + channels);
            }
            this.alFormat = AL10.AL_FORMAT_MONO16;
            if (channels == 2) {
                this.stereoStaging = MemoryUtil.memAllocShort(16384 * 2);
            }
        } catch (Throwable t) {
            MemoryUtil.memFree(this.ownedFileData);
            if (this.stereoStaging != null) {
                MemoryUtil.memFree(this.stereoStaging);
            }
            throw t;
        }
    }

    @Override
    public int decodeMonoFrames(ShortBuffer target) {
        if (closed) return 0;
        if (channels == 1) {
            return STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, 1, target);
        }
        int frames = target.remaining();
        stereoStaging.clear();
        stereoStaging.limit(frames * 2);
        int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, 2, stereoStaging);
        if (decoded <= 0) return 0;
        int writePos = target.position();
        for (int i = 0; i < decoded; i++) {
            int l = stereoStaging.get(i * 2);
            int r = stereoStaging.get(i * 2 + 1);
            target.put(writePos + i, (short) ((l + r) >> 1));
        }
        target.position(writePos + decoded);
        return decoded;
    }

    @Override
    public int sampleRate() { return sampleRate; }

    @Override
    public int alFormat() { return alFormat; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            STBVorbis.stb_vorbis_close(handle);
        } finally {
            MemoryUtil.memFree(ownedFileData);
            if (stereoStaging != null) {
                MemoryUtil.memFree(stereoStaging);
                stereoStaging = null;
            }
        }
    }
}
