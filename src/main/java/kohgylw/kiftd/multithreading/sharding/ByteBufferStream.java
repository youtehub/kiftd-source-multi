package kohgylw.kiftd.multithreading.sharding;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

class ByteBufferStream extends ByteArrayOutputStream {
    public ByteBufferStream() {
    }

    public InputStream inputStream() {
        return new ByteArrayInputStream(this.buf, 0, this.count);
    }
}
