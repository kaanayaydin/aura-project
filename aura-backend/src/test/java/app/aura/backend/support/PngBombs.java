package app.aura.backend.support;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** IHDR çözünürlüğü şişik, dosya küçük — decompression bomb fixture. */
public final class PngBombs {

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private PngBombs() {}

    public static byte[] declaredSize(int width, int height) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(ihdr);
            data.writeInt(width);
            data.writeInt(height);
            data.writeByte(8);
            data.writeByte(2);
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
            writeChunk(out, new byte[] {'I', 'H', 'D', 'R'}, ihdr.toByteArray());
            writeChunk(out, new byte[] {'I', 'D', 'A', 'T'}, zlib(new byte[] {0, 0, 0, 0}));
            writeChunk(out, new byte[] {'I', 'E', 'N', 'D'}, new byte[0]);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, byte[] type, byte[] payload)
            throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(payload.length);
        data.write(type);
        data.write(payload);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(payload);
        data.writeInt((int) crc.getValue());
    }

    private static byte[] zlib(byte[] raw) {
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] buf = new byte[64];
        int n = deflater.deflate(buf);
        deflater.end();
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }
}
