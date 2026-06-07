package net.discy.core.client.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ShortBuffer;

public final class StreamingAudioSource implements Closeable {

    private static final int NUM_BUFFERS = 4;
    private static final int FRAMES_PER_BUFFER = 16384;

    private final int alSource;
    private final int[] alBuffers = new int[NUM_BUFFERS];
    private final AudioDecoder decoder;
    private final ShortBuffer pcmStaging;

    private boolean eof = false;
    private boolean finished = false;
    private boolean disposed = false;

    public StreamingAudioSource(AudioDecoder decoder, double x, double y, double z) {
        this.decoder = decoder;
        this.alSource = AL10.alGenSources();
        for (int i = 0; i < NUM_BUFFERS; i++) {
            alBuffers[i] = AL10.alGenBuffers();
        }
        this.pcmStaging = MemoryUtil.memAllocShort(FRAMES_PER_BUFFER);

        AL10.alSource3f(alSource, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        AL10.alSource3f(alSource, AL10.AL_VELOCITY, 0f, 0f, 0f);
        AL10.alSourcef(alSource, AL10.AL_ROLLOFF_FACTOR, 0.0f);
        AL10.alSourcef(alSource, AL10.AL_GAIN, 1.0f);
        AL10.alSourcei(alSource, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcei(alSource, AL10.AL_LOOPING, AL10.AL_FALSE);
    }

    public void setPosition(double x, double y, double z) {
        if (disposed) return;
        AL10.alSource3f(alSource, AL10.AL_POSITION, (float) x, (float) y, (float) z);
    }

    public void start() {
        for (int buf : alBuffers) {
            if (!fillAndQueue(buf)) break;
        }
        AL10.alSourcePlay(alSource);
    }

    public void tick(float gain) {
        if (disposed || finished) return;

        AL10.alSourcef(alSource, AL10.AL_GAIN, Math.max(0f, gain));

        int processed = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int bufId = AL10.alSourceUnqueueBuffers(alSource);
            if (!eof) {
                fillAndQueue(bufId);
            }
        }

        int state = AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE);
        if (eof) {
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued == 0 || state == AL10.AL_STOPPED) {
                finished = true;
            }
        } else if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) {
            AL10.alSourcePlay(alSource);
        }
    }

    private boolean fillAndQueue(int bufId) {
        pcmStaging.clear();
        int framesDecoded = decoder.decodeMonoFrames(pcmStaging);
        if (framesDecoded <= 0) {
            eof = true;
            return false;
        }
        pcmStaging.position(0);
        pcmStaging.limit(framesDecoded);
        AL10.alBufferData(bufId, decoder.alFormat(), pcmStaging, decoder.sampleRate());
        AL10.alSourceQueueBuffers(alSource, bufId);
        return true;
    }

    public boolean isFinished() {
        return finished;
    }

    public void stop() {
        if (disposed) return;
        AL10.alSourceStop(alSource);
        finished = true;
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        try {
            AL10.alSourceStop(alSource);
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            for (int i = 0; i < queued; i++) {
                AL10.alSourceUnqueueBuffers(alSource);
            }
            AL10.alDeleteSources(alSource);
            for (int b : alBuffers) AL10.alDeleteBuffers(b);
        } finally {
            MemoryUtil.memFree(pcmStaging);
            decoder.close();
        }
    }
}
