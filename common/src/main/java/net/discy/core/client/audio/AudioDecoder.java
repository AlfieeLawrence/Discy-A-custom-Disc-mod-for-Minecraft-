package net.discy.core.client.audio;

import java.io.Closeable;
import java.nio.ShortBuffer;

public interface AudioDecoder extends Closeable {

    int decodeMonoFrames(ShortBuffer target);

    int sampleRate();

    int alFormat();

    @Override
    void close();
}
