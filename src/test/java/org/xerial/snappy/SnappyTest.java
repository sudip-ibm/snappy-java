/*--------------------------------------------------------------------------
 *  Copyright 2011 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
//--------------------------------------
// snappy-java Project
//
// SnappyTest.java
// Since: 2011/03/30
//
// $URL$
// $Author$
//--------------------------------------
package org.xerial.snappy;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.Assume;
import org.junit.Assert;
import org.junit.Test;
import org.xerial.util.log.Logger;

public class SnappyTest
{
    private static Logger _logger = Logger.getLogger(SnappyTest.class);

    @Test
    public void getVersion()
            throws Exception
    {
        String version = Snappy.getNativeLibraryVersion();
        _logger.debug("version: " + version);
    }

    @Test
    public void directBufferCheck()
            throws Exception
    {

        try {
            ByteBuffer src = ByteBuffer.allocate(1024);
            src.put("hello world".getBytes());
            src.flip();
            ByteBuffer dest = ByteBuffer.allocate(1024);
            int maxCompressedLen = Snappy.compress(src, dest);
        }
        catch (SnappyError e) {
            Assert.assertTrue(e.errorCode == SnappyErrorCode.NOT_A_DIRECT_BUFFER);
            return;
        }

        fail("shouldn't reach here");
    }

    @Test
    public void directBuffer()
            throws Exception
    {

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 20; ++i) {
            s.append("Hello world!");
        }
        String origStr = s.toString();
        byte[] orig = origStr.getBytes();
        int BUFFER_SIZE = orig.length;
        ByteBuffer src = ByteBuffer.allocateDirect(orig.length);
        src.put(orig);
        src.flip();
        _logger.debug("input size: " + src.remaining());
        int maxCompressedLen = Snappy.maxCompressedLength(src.remaining());
        _logger.debug("max compressed length:" + maxCompressedLen);

        ByteBuffer compressed = ByteBuffer.allocateDirect(maxCompressedLen);
        int compressedSize = Snappy.compress(src, compressed);
        _logger.debug("compressed length: " + compressedSize);

        assertTrue(Snappy.isValidCompressedBuffer(compressed));

        assertEquals(0, src.position());
        assertEquals(orig.length, src.remaining());
        assertEquals(orig.length, src.limit());

        assertEquals(0, compressed.position());
        assertEquals(compressedSize, compressed.limit());
        assertEquals(compressedSize, compressed.remaining());

        int uncompressedLen = Snappy.uncompressedLength(compressed);
        _logger.debug("uncompressed length: " + uncompressedLen);
        ByteBuffer extract = ByteBuffer.allocateDirect(uncompressedLen);
        int uncompressedLen2 = Snappy.uncompress(compressed, extract);
        assertEquals(uncompressedLen, uncompressedLen2);
        assertEquals(uncompressedLen, extract.remaining());

        byte[] b = new byte[uncompressedLen];
        extract.get(b);
        String decompressed = new String(b);
        _logger.debug(decompressed);

        assertEquals(origStr, decompressed);
    }

    @Test
    public void bufferOffset()
            throws Exception
    {

        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] orig = m.getBytes();
        final int offset = 100;
        ByteBuffer input = ByteBuffer.allocateDirect(orig.length + offset);
        input.position(offset);
        input.put(orig);
        input.flip();
        input.position(offset);

        // compress
        int maxCompressedLength = Snappy.maxCompressedLength(input.remaining());
        final int offset2 = 40;
        ByteBuffer compressed = ByteBuffer.allocateDirect(maxCompressedLength + offset2);
        compressed.position(offset2);
        Snappy.compress(input, compressed);
        assertTrue(Snappy.isValidCompressedBuffer(compressed));

        // uncompress
        final int offset3 = 80;
        int uncompressedLength = Snappy.uncompressedLength(compressed);
        ByteBuffer uncompressed = ByteBuffer.allocateDirect(uncompressedLength + offset3);
        uncompressed.position(offset3);
        Snappy.uncompress(compressed, uncompressed);
        assertEquals(offset3, uncompressed.position());
        assertEquals(offset3 + uncompressedLength, uncompressed.limit());
        assertEquals(uncompressedLength, uncompressed.remaining());

        // extract string
        byte[] recovered = new byte[uncompressedLength];
        uncompressed.get(recovered);
        String m2 = new String(recovered);

        assertEquals(m, m2);
    }

    @Test
    public void byteArrayCompress()
            throws Exception
    {

        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] input = m.getBytes();
        byte[] output = new byte[Snappy.maxCompressedLength(input.length)];
        int compressedSize = Snappy.compress(input, 0, input.length, output, 0);
        byte[] uncompressed = new byte[input.length];

        assertTrue(Snappy.isValidCompressedBuffer(output, 0, compressedSize));
        int uncompressedSize = Snappy.uncompress(output, 0, compressedSize, uncompressed, 0);
        String m2 = new String(uncompressed);
        assertEquals(m, m2);
    }

    @Test
    public void rangeCheck()
            throws Exception
    {
        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] input = m.getBytes();
        byte[] output = new byte[Snappy.maxCompressedLength(input.length)];
        int compressedSize = Snappy.compress(input, 0, input.length, output, 0);

        assertTrue(Snappy.isValidCompressedBuffer(output, 0, compressedSize));
        // Intentionally set an invalid range
        assertFalse(Snappy.isValidCompressedBuffer(output, 0, compressedSize + 1));
        assertFalse(Snappy.isValidCompressedBuffer(output, 1, compressedSize));

        // Test the ByteBuffer API
        ByteBuffer bin = ByteBuffer.allocateDirect(input.length);
        bin.put(input);
        bin.flip();
        ByteBuffer bout = ByteBuffer.allocateDirect(Snappy.maxCompressedLength(bin.remaining()));
        int compressedSize2 = Snappy.compress(bin, bout);
        assertEquals(compressedSize, compressedSize2);

        assertTrue(Snappy.isValidCompressedBuffer(bout));
        // Intentionally set an invalid range
        bout.limit(bout.limit() + 1);
        assertFalse(Snappy.isValidCompressedBuffer(bout));
        bout.limit(bout.limit() - 1);
        bout.position(1);
        assertFalse(Snappy.isValidCompressedBuffer(bout));
    }

    @Test
    public void highLevelAPI()
            throws Exception
    {

        String m = "Hello! 01234 ACGDSFSDFJ World. FDSDF02394234 fdsfda03924";
        byte[] input = m.getBytes();
        byte[] output = Snappy.compress(input);

        byte[] uncompressed = Snappy.uncompress(output);
        String m2 = new String(uncompressed);
        assertEquals(m, m2);
    }

    @Test
    public void lowLevelAPI()
            throws Exception
    {

        String m = "Hello! 01234 ACGDSFSDFJ World. FDSDF02394234 fdsfda03924";
        byte[] input = m.getBytes();
        byte[] output = Snappy.rawCompress(input, input.length);

        byte[] uncompressed = Snappy.uncompress(output);
        String m2 = new String(uncompressed);
        assertEquals(m, m2);
    }

    @Test
    public void simpleUsage()
            throws Exception
    {

        String input = "Hello snappy-java! Snappy-java is a JNI-based wrapper"
                + " for using Snappy from Google (written in C++), a fast compresser/decompresser.";
        byte[] compressed = Snappy.compress(input.getBytes("UTF-8"));
        byte[] uncompressed = Snappy.uncompress(compressed);
        String result = new String(uncompressed, "UTF-8");
        _logger.debug(result);
    }

    @Test
    public void floatArray()
            throws Exception
    {
        float[] data = new float[] {1.0f, -0.3f, 1.3f, 234.4f, 34};
        byte[] compressed = Snappy.compress(data);
        float[] result = Snappy.uncompressFloatArray(compressed);
        assertArrayEquals(data, result, 0.0f);
    }

    @Test
    public void doubleArray()
            throws Exception
    {
        double[] data = new double[] {1.0, -0.3, 1.3, 234.4, 34};
        byte[] compressed = Snappy.compress(data);
        double[] result = Snappy.uncompressDoubleArray(compressed);
        assertArrayEquals(data, result, 0.0f);
    }

    @Test
    public void longArray()
            throws Exception
    {
        long[] data = new long[] {2, 3, 15, 4234, 43251531412342342L, 23423422342L};
        byte[] compressed = Snappy.compress(data);
        long[] result = Snappy.uncompressLongArray(compressed);
        assertArrayEquals(data, result);
    }

    @Test
    public void shortArray()
            throws Exception
    {
        short[] data = new short[] {432, -32267, 1, 3, 34, 43, 34, Short.MAX_VALUE, -1};
        byte[] compressed = Snappy.compress(data);
        short[] result = Snappy.uncompressShortArray(compressed);
        assertArrayEquals(data, result);
    }

    @Test
    public void intArray()
            throws Exception
    {
        int[] data = new int[] {432, -32267, 1, 3, 34, 43, 34, Short.MAX_VALUE, -1, Integer.MAX_VALUE, 3424, 43};
        byte[] compressed = Snappy.compress(data);
        int[] result = Snappy.uncompressIntArray(compressed);
        assertArrayEquals(data, result);
    }

    @Test
    public void charArray()
            throws Exception
    {
        char[] data = new char[] {'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd', '!'};
        byte[] compressed = Snappy.compress(data);
        char[] result = Snappy.uncompressCharArray(compressed);
        assertArrayEquals(data, result);
    }

    @Test
    public void string()
            throws Exception
    {
        String s = "Hello Snappy! Snappy! Snappy!";
        byte[] compressed = Snappy.compress(s);
        String uncompressedString = Snappy.uncompressString(compressed);
        assertEquals(s, uncompressedString);
    }

    @Test
    public void isValidCompressedData()
            throws Exception
    {

        byte[] b = new byte[] {(byte) 91, (byte) 34, (byte) 80, (byte) 73, (byte) 34, (byte) 93};

        assertFalse(Snappy.isValidCompressedBuffer(b));

        try {
            byte[] uncompressed = Snappy.uncompress(b);
            fail("cannot reach here since the input is invalid data");
        }
        catch (IOException e) {
            _logger.debug(e);
        }
    }


    /*
    Tests happy cases for SnappyInputStream.read method
    - {0}
     */
    @Test
    public void isValidChunkLengthForSnappyInputStreamIn()
            throws Exception {
        byte[] data = {0};
        SnappyInputStream in = new SnappyInputStream(new ByteArrayInputStream(data));
        byte[] out = new byte[50];
        in.read(out);
    }

    /*
    Tests sad cases for SnappyInputStream.read method
    - Expects a java.lang.NegativeArraySizeException catched into a SnappyError
    - {-126, 'S', 'N', 'A', 'P', 'P', 'Y', 0, 0, 0, 0, 0, 0, 0, 0, 0,(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff}
     */
    @Test(expected = SnappyError.class)
    public void isInvalidChunkLengthForSnappyInputStreamInNegative()
            throws Exception {
        byte[] data = {-126, 'S', 'N', 'A', 'P', 'P', 'Y', 0, 0, 0, 0, 0, 0, 0, 0, 0,(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        SnappyInputStream in = new SnappyInputStream(new ByteArrayInputStream(data));
        byte[] out = new byte[50];
        in.read(out);
    }

    /*
    Tests sad cases for SnappyInputStream.read method
    - Expects a java.lang.OutOfMemoryError
    - {-126, 'S', 'N', 'A', 'P', 'P', 'Y', 0, 0, 0, 0, 0, 0, 0, 0, 0,(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff}
     */
    @Test(expected = SnappyError.class)
    public void isInvalidChunkLengthForSnappyInputStreamOutOfMemory()
            throws Exception {
        byte[] data = {-126, 'S', 'N', 'A', 'P', 'P', 'Y', 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff};
        SnappyInputStream in = new SnappyInputStream(new ByteArrayInputStream(data));
        byte[] out = new byte[50];
        try {
            in.read(out);
        } catch (Exception ignored) {
            // Exception here will be catched
            // But OutOfMemoryError will not be caught, and will still be thrown
        }
    }

    /*
    Tests happy cases for BitShuffle.shuffle method
    - double: 0, 10
    - float: 0, 10
    - int: 0, 10
    - long: 0, 10
    - short: 0, 10
     */
    @Test
    public void isValidArrayInputLength()
            throws Exception {
        byte[] a = Snappy.compress(new char[0]);
        byte[] b = Snappy.compress(new double[0]);
        byte[] c = Snappy.compress(new float[0]);
        byte[] d = Snappy.compress(new int[0]);
        byte[] e = Snappy.compress(new long[0]);
        byte[] f = Snappy.compress(new short[0]);
        byte[] g = Snappy.compress(new char[10]);
        byte[] h = Snappy.compress(new double[10]);
        byte[] i = Snappy.compress(new float[10]);
        byte[] j = Snappy.compress(new int[10]);
        byte[] k = Snappy.compress(new long[10]);
        byte[] l = Snappy.compress(new short[10]);
    }

    /*
    Tests sad cases for Snappy.compress
    - Allocate a buffer whose byte size will be a bit larger than Integer.MAX_VALUE
    - char
    - double
    - float
    - int
    - long
    - short
     */
    @Test(expected = SnappyError.class)
    public void isTooLargeDoubleArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new double[Integer.MAX_VALUE / 8 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeCharArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new char[Integer.MAX_VALUE / 2 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeFloatArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new float[Integer.MAX_VALUE / 4 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeIntArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new int[Integer.MAX_VALUE / 4 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeLongArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new long[Integer.MAX_VALUE / 8 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeShortArrayInputLength() throws Exception {
        assumingCIIsFalse();
        Snappy.compress(new short[Integer.MAX_VALUE / 2 + 1]);
    }

    /*
    Tests happy cases for Snappy.compress
    - char: 0, 10
    */
    @Test
    public void isValidArrayInputLengthForBitShuffleShuffle()
            throws Exception
    {
        byte[] b = BitShuffle.shuffle(new double[0]);
        byte[] c = BitShuffle.shuffle(new float[0]);
        byte[] d = BitShuffle.shuffle(new int[0]);
        byte[] e = BitShuffle.shuffle(new long[0]);
        byte[] f = BitShuffle.shuffle(new short[0]);
        byte[] n = BitShuffle.shuffle(new double[10]);
        byte[] o = BitShuffle.shuffle(new float[10]);
        byte[] p = BitShuffle.shuffle(new int[10]);
        byte[] q = BitShuffle.shuffle(new long[10]);
        byte[] r = BitShuffle.shuffle(new short[10]);
    }

    /*
    Tests sad cases for BitShuffle.shuffle method
    - Allocate a buffer whose byte size will be a bit larger than Integer.MAX_VALUE
    - double: 8
    - float: 4
    - int: 4
    - long: 8
    - short: 2
     */
    @Test(expected = SnappyError.class)
    public void isTooLargeDoubleArrayInputLengthForBitShuffleShuffle() throws Exception {
        assumingCIIsFalse();
        BitShuffle.shuffle(new double[Integer.MAX_VALUE / 8 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeFloatArrayInputLengthForBitShuffleShuffle() throws Exception {
        assumingCIIsFalse();
        BitShuffle.shuffle(new float[Integer.MAX_VALUE / 4 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeIntArrayInputLengthForBitShuffleShuffle() throws Exception {
        assumingCIIsFalse();
        BitShuffle.shuffle(new float[Integer.MAX_VALUE / 4 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeLongArrayInputLengthForBitShuffleShuffle() throws Exception {
        assumingCIIsFalse();
        BitShuffle.shuffle(new long[Integer.MAX_VALUE / 8 + 1]);
    }

    @Test(expected = SnappyError.class)
    public void isTooLargeShortArrayInputLengthForBitShuffleShuffle() throws Exception {
        assumingCIIsFalse();
        BitShuffle.shuffle(new short[Integer.MAX_VALUE / 2 + 1]);
    }

    private void assumingCIIsFalse() {
        if (System.getenv("CI") == null)
            return;
        Assume.assumeFalse("Skipped on CI", System.getenv("CI").equals("true"));
    }
}
