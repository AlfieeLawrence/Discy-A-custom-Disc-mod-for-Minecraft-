package net.discy.core.client.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ShortBuffer;

public final class Mp3Decoder implements AudioDecoder {

    private final Bitstream bitstream;
    private final Decoder decoder;

    private int sampleRate = 44100;
    private boolean eof = false;
    private boolean closed = false;

    private short[] pending = null;
    private int pendingOff = 0;

    public Mp3Decoder(byte[] mp3Bytes) throws IOException {
        this.bitstream = new Bitstream(new ByteArrayInputStream(mp3Bytes));
        this.decoder = new Decoder();
    }

    @Override
    public int decodeMonoFrames(ShortBuffer target) {
        if (closed || eof) return 0;

        int written = 0;

        while (target.hasRemaining()) {
            if (pending != null && pendingOff < pending.length) {
                while (pendingOff < pending.length && target.hasRemaining()) {
                    target.put(pending[pendingOff++]);
                    written++;
                }
                if (pendingOff >= pending.length) {
                    pending = null;
                    pendingOff = 0;
                }
                continue;
            }

            Header header;
            try {
                header = bitstream.readFrame();
            } catch (BitstreamException e) {
                eof = true;
                break;
            }
            if (header == null) {
                eof = true;
                break;
            }

            sampleRate = header.frequency();
            int channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;

            SampleBuffer output;
            try {
                output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            } catch (DecoderException e) {
                try { bitstream.closeFrame(); } catch (Exception ignored) {}
                continue;
            }

            short[] pcm = output.getBuffer();
            int frameLen = output.getBufferLength();

            int monoLen = (channels == 1) ? frameLen : frameLen / 2;
            short[] mono = new short[monoLen];
            if (channels == 1) {
                System.arraycopy(pcm, 0, mono, 0, monoLen);
            } else {
                for (int i = 0; i < monoLen; i++) {
                    mono[i] = (short) ((pcm[i * 2] + pcm[i * 2 + 1]) >> 1);
                }
            }

            try { bitstream.closeFrame(); } catch (Exception ignored) {}

            int canWrite = Math.min(mono.length, target.remaining());
            for (int i = 0; i < canWrite; i++) {
                target.put(mono[i]);
                written++;
            }
            if (canWrite < mono.length) {
                pending = mono;
                pendingOff = canWrite;
            }
        }

        return written;
    }

    @Override
    public int sampleRate() { return sampleRate; }

    @Override
    public int alFormat() { return AL10.AL_FORMAT_MONO16; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { bitstream.close(); } catch (BitstreamException ignored) {}
    }
}
