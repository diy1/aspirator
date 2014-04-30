package chord.util;

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.BufferedInputStream;
import java.util.zip.GZIPInputStream;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.BufferedOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of a buffered input or output file stream of bytes.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public class ByteBufferedFile {
    // size of maximum unit that will be read/written (e.g. 1 for byte, 2 for short, 4 for int, 8 for long, etc.)
    // It is set to 8 as the maximum unit currently supported is long.
    private static final int MAX_BYTES = 8;
    // optimal number of bytes to read/write in a single operation to a file
    private final int fileBlockSize;
    // size of the buffer == fileBlockSize + MAX_BYTES
    private final int bufferSize;
    private byte[] buffer;
    private FileInputStream iStream;
    private FileOutputStream oStream;
    private int curPos;
    private int maxPos;  // used only if isRead
    public ByteBufferedFile(int fileBlockSize, String fileName, boolean isRead) throws IOException {
        assert (fileBlockSize >= MAX_BYTES);
        this.fileBlockSize = fileBlockSize;
        this.bufferSize = fileBlockSize + MAX_BYTES;
        buffer = new byte[bufferSize];
        if (isRead) {
            if (fileName.endsWith(".gz")) {
                // HACK: unzip the file manually
                String newFileName = fileName.replaceAll("\\.gz$", "");
                String cmd = String.format("gunzip < %s > %s", fileName, newFileName);
                System.out.println("UNZIP: " + cmd);
                Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });
                try {
                    if (p.waitFor() != 0)
                        throw new RuntimeException("Unable to gunzip "+fileName);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                iStream = new FileInputStream(newFileName);
            } else
                iStream = new FileInputStream(fileName);
            refill();
        } else {
            if (fileName.endsWith(".gz"))
                throw new RuntimeException("Not supported");
            else
                oStream = new FileOutputStream(fileName);
        }
    }
    // Must be called only when put[Byte|Int|Long] is called and curPos is at or beyond fileBlockSize.
    // This operation performs the following steps in order:
    // 1. Copies buffer from [0..fileBlockSize) to file
    // 2. Copies buffer from [fileBlockSize..curPos) to [0..N) where N = curPos - fileBlockSize
    // 3. Sets curPos to N
    private void write() throws IOException {
        oStream.write(buffer, 0, fileBlockSize);
        int numMoved = 0;
        for (int i = fileBlockSize; i < curPos; i++)
            buffer[numMoved++] = buffer[i];
        curPos = numMoved;
    }
    // Must be called only once and only after all calls to put[Byte|Int|Long] are done.
    // Writes any data remaining in the buffer to file and closes the file.
    public void flush() throws IOException {
        oStream.write(buffer, 0, curPos);
        oStream.close();
        oStream = null;
    }
    public void putByte(byte v) throws IOException {
        if (curPos >= fileBlockSize)
            write();
        buffer[curPos++] = v;
    }
    public byte getByte() throws IOException {
        if (maxPos == curPos) {
            refill();
            if (maxPos == 0)
                throw new IOException();
        }
        byte v = buffer[curPos++];
        return v;
    }
    public void putInt(int v) throws IOException {
        if (curPos >= fileBlockSize)
            write();
        buffer[curPos++] = (byte) (v >> 24);
        buffer[curPos++] = (byte) (v >> 16);
        buffer[curPos++] = (byte) (v >> 8);
        buffer[curPos++] = (byte) v;
    }
    public int getInt() throws IOException {
        if (maxPos - curPos < 4) {
            refill();
            if (maxPos < 4)
                throw new IOException();
        }
        int v =
            ((buffer[curPos++]) << 24) |
            ((buffer[curPos++] & 0xFF) << 16) |
            ((buffer[curPos++] & 0xFF) << 8) |
            ((buffer[curPos++] & 0xFF));
        return v;
    }
    public void putLong(long v) throws IOException {
        if (curPos >= fileBlockSize)
            write();
        buffer[curPos++] = (byte) (v >> 56);
        buffer[curPos++] = (byte) (v >> 48);
        buffer[curPos++] = (byte) (v >> 40);
        buffer[curPos++] = (byte) (v >> 32);
        buffer[curPos++] = (byte) (v >> 24);
        buffer[curPos++] = (byte) (v >> 16);
        buffer[curPos++] = (byte) (v >> 8);
        buffer[curPos++] = (byte) v;
    }
    public long getLong() throws IOException {
        if (maxPos - curPos < 8) {
            refill();
            if (maxPos < 8)
                throw new IOException();
        }
        long v =
            ((long) (buffer[curPos++]) << 56) |
            ((long) (buffer[curPos++] & 0xFF) << 48) |
            ((long) (buffer[curPos++] & 0xFF) << 40) |
            ((long) (buffer[curPos++] & 0xFF) << 32) |
            ((long) (buffer[curPos++] & 0xFF) << 24) |
            ((long) (buffer[curPos++] & 0xFF) << 16) |
            ((long) (buffer[curPos++] & 0xFF) <<  8) |
            ((long) (buffer[curPos++] & 0xFF));
        return v;
    }
    public void putString(String s) throws IOException {
        byte[] sb = s.getBytes();
        int n = sb.length;
        putInt(n);
        for (int i = 0; i < n; i++)
            putByte(sb[i]);
    }
    public String getString() throws IOException {
        int n = getInt();
        byte[] sb = new byte[n];
        for (int i = 0; i < n; i++)
            sb[i] = getByte();
        return new String(sb);
    }
    public void eatByte() throws IOException {
        if (maxPos == curPos) {
            refill();
            if (maxPos == 0)
                throw new IOException();
        }
        curPos++;
    }
    public void eatInt() throws IOException {
        if (maxPos - curPos < 4) {
            refill();
            if (maxPos < 4)
                throw new IOException();
        }
        curPos += 4;
    }
    public void eat(int n) throws IOException {
        int q = n / 8;
        int r = n % 8;
        for (int i = 0; i < q; i++)
            eatLong();
        for (int i = 0; i < r; i++)
            eatByte();
    }
    public void eatLong() throws IOException {
        if (maxPos - curPos < 8) {
            refill();
            if (maxPos < 8)
                throw new IOException();
        }
        curPos += 8;
    }
    public boolean isDone() throws IOException {
        if (curPos != maxPos)
            return false;
        if (iStream == null)
            return true;
        refill();
        return maxPos == 0;
    }
    private void refill() throws IOException {
        int numMoved = 0;
        for (int i = curPos; i < maxPos; i++)
            buffer[numMoved++] = buffer[i];
        curPos = 0;
        int numRead = iStream.read(buffer, numMoved, fileBlockSize);
        if (numRead == -1) {
            iStream.close();
            iStream = null;
            numRead = 0;
        }
        maxPos = numRead + numMoved;
    }
    public static int assemble(byte b1, byte b2, byte b3, byte b4) {
        return (b1 << 24) | ((b2 & 0xFF) << 16) |
            ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }
}

/*
reading/writing unsigned bytes

A slight complication in Java is that, perhaps contrary to common sense,
the byte data type is always signed. What that means is that if you try
and read/write the byte 128, when converted to a Java byte, this will
actually come out as -128; byte 129 will actually come out as -127 etc.
With some slightly klutzy code we can work round the problem:

to write an unsigned byte, we hold or calculate the value as an int and
then simply cast to a byte;
to read an unsigned byte, we make sure we are reading into an int (or at
least, something bigger than a byte), and then AND with 255 (0xff in hex).
This is what the latter code looks like:

int unsignedByte = bb.get() & 0xff;

This works because whenever you perform bitwise operations in Java,
both operands (the things either side of the &) are converted to
(at least) an int.
Initially, a negative byte will be converted to a negative int
(the sign is preserved or what is technically called sign extended).
Then, ANDing with 255 (the maximum positive number that can fit in
an 8-bit byte) in effect "chops off" the higher up
bits that mark the int as negative.
*/
