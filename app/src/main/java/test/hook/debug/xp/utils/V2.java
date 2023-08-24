package test.hook.debug.xp.utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * @author user
 */
public class V2 implements Closeable {
    private static final int PK = 0x04034B50;
    private RandomAccessFile accessFile;

    public V2(File file) {
        try {
            this.accessFile = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignored) {
        }
    }

    private void skip(int size) throws IOException {
        if (accessFile.skipBytes(size) != size) {
            throw new IOException("Failed to skip");
        }
    }

    private long readInt64() throws IOException {
        byte[] tmp = new byte[8];
        if (accessFile.read(tmp) != 8) {
            throw new IOException("Failed to read 8 byte");
        }
        return ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private int readInt32() throws IOException {
        byte[] tmp = new byte[4];
        if (accessFile.read(tmp) != 4) {
            throw new IOException("Failed to read 4 byte");
        }
        return ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private int readShort() throws IOException {
        byte[] tmp = new byte[2];
        if (accessFile.read(tmp) != 2) {
            throw new IOException("Failed to read 2 byte");
        }
        return ByteBuffer.wrap(tmp).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    public void skipZipEntry() throws IOException {
        while (accessFile.getFilePointer() != accessFile.length()) {
            int pk = readInt32();
            if (pk != PK) {
                accessFile.seek(accessFile.getFilePointer() - 4);
                break;
            }
            skip(0xE);
            int size = readInt32();
            skip(4);
            int nameSize = readShort();
            skip(2 + nameSize + size);
        }
    }

    private byte[] parseSignData(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int digestsSize = buffer.getInt();
        buffer.position(buffer.position() + digestsSize);

        int certsSize = buffer.getInt();
        byte[] certs = new byte[certsSize];
        buffer.get(certs);

        return Arrays.copyOfRange(certs, 4, certs.length);
    }

    public byte[] parseSign() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) readInt64()).order(ByteOrder.LITTLE_ENDIAN);
        accessFile.read(buffer.array());
        while (buffer.remaining() != 0) {
            buffer.getLong();
            int eId = buffer.getInt();
            int valueSize = buffer.getInt();
            byte[] eachBlock = new byte[valueSize];
            buffer.get(eachBlock);
            ByteBuffer eachTmp = ByteBuffer.wrap(eachBlock).order(ByteOrder.LITTLE_ENDIAN);
            if (eId == 0x1000101) {
                eachTmp.getInt();
                int signDataSize = eachTmp.getInt();
                byte[] sign = new byte[signDataSize];
                eachTmp.get(sign);
                return parseSignData(sign);
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (accessFile != null) {
            accessFile.close();
        }
    }
}
