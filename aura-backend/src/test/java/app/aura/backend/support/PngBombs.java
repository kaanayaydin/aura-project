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

    /** VP8X canvas — ImageIO eklentisiz WebP; magic peek. */
    public static byte[] webpVp8x(int width, int height) {
        byte[] body = new byte[30];
        body[0] = 'R';
        body[1] = 'I';
        body[2] = 'F';
        body[3] = 'F';
        writeU32le(body, 4, 22);
        body[8] = 'W';
        body[9] = 'E';
        body[10] = 'B';
        body[11] = 'P';
        body[12] = 'V';
        body[13] = 'P';
        body[14] = '8';
        body[15] = 'X';
        writeU32le(body, 16, 10);
        writeU24le(body, 24, width - 1);
        writeU24le(body, 27, height - 1);
        return body;
    }

    /** BITMAPINFOHEADER width@18 height@22. */
    public static byte[] bmpDeclared(int width, int height) {
        byte[] body = new byte[54];
        body[0] = 'B';
        body[1] = 'M';
        writeU32le(body, 2, 54);
        writeU32le(body, 10, 54);
        writeU32le(body, 14, 40);
        writeI32le(body, 18, width);
        writeI32le(body, 22, height);
        body[26] = 1;
        body[28] = 24;
        return body;
    }

    public static byte[] unparseableGarbage() {
        byte[] body = new byte[32];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (0x41 + (i % 26));
        }
        return body;
    }

    private static void writeU24le(byte[] body, int offset, int value) {
        body[offset] = (byte) (value & 0xff);
        body[offset + 1] = (byte) ((value >> 8) & 0xff);
        body[offset + 2] = (byte) ((value >> 16) & 0xff);
    }

    private static void writeU32le(byte[] body, int offset, int value) {
        body[offset] = (byte) (value & 0xff);
        body[offset + 1] = (byte) ((value >> 8) & 0xff);
        body[offset + 2] = (byte) ((value >> 16) & 0xff);
        body[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static void writeI32le(byte[] body, int offset, int value) {
        writeU32le(body, offset, value);
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
