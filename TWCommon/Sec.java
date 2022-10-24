package TWCommon;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import javax.crypto.Cipher;

import java.lang.IllegalArgumentException;

public class Sec {
    private static byte[] key = new byte[32];
    private static int keypos = 0;
    public static KeyPairGenerator kg;
    public static SecureRandom rand;
    private static void regenerate_key() throws Exception {
        keypos = 0;
        key = hash(key);
    }
    public static byte[] crypt(byte[] data) throws Exception {
        byte[] output = new byte[data.length];
        for (int i = 0; i < data.length; i ++) {
            if (keypos >= 32) {
                regenerate_key();
            }
            for (int j = 0; j < 8; j ++) {
                output[i] = (byte) (output[i] | ((data[i] & (1 << j)) ^ (key[keypos] & (1 << j))));
            }
            keypos ++;
        }
        return output;
    }
    public static byte[] hash(byte[] data) throws Exception {
        MessageDigest hasher = MessageDigest.getInstance("SHA-256");
        return hasher.digest(data);
    }
    public static byte[] longToBytes(long big, int count) {
        if (count > 8) {
            throw new IllegalArgumentException("count cannot be more than 8");
        }
        byte[] bytes = new byte[count];
        for (int i = count - 1; i >= 0; i --) {
            bytes[count-i-1] = (byte) ((big & (0xff << (i * 8))) >> (i * 8));
        }
        return bytes;
    }
    public static byte[] intToBytes(int big, int count) {
        if (count > 4) {
            throw new IllegalArgumentException("count cannot be more than 4");
        }
        return longToBytes((long) big, count);
    }
    public static byte[] shortToBytes(short big) {
        return new byte[]{(byte) ((big&0xff00) >> 8), (byte) (big&0xff)};
    }
    public static short bytesToShort(byte[] bytes) {
        if (bytes.length != 2) {
            throw new IllegalArgumentException("must have two bytes to convert to short");
        }
        return (short) (((short) (bytes[0]<<8)) | ((short) (bytes[1])));
    }
    public static int bytesToInt(byte[] bytes) {
        if (bytes.length > 4) {
            throw new IllegalArgumentException("cannot have more than four bytes to convert to int");
        }
        int f = 0;
        for (int i = 0; i < bytes.length; i ++) {
            f |= ((int) (bytes[i]<<(bytes.length-i-1)));
        }
        return f;
    }
    public static long bytesToLong(byte[] bytes) {
        if (bytes.length > 8) {
            throw new IllegalArgumentException("cannot have more than eight bytes to convert to long");
        }
        long f = 0;
        for (int i = 0; i < bytes.length; i ++) {
            f |= ((long) (bytes[i]<<(bytes.length-i-1)));
        }
        return f;
    }
    public static void init() {
        try {
            kg = KeyPairGenerator.getInstance("RSA");
            rand = new SecureRandom(longToBytes(System.nanoTime(), 8));
        } catch (Exception _E) {}
        kg.initialize(1024);
    }
    public static byte[] RSAEncrypt(PublicKey pk, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.ENCRYPT_MODE, pk);
        return c.doFinal(data);
    }
    public static byte[] RSADecrypt(PrivateKey pk, byte[] data) throws Exception {
        Cipher c = Cipher.getInstance("RSA");
        c.init(Cipher.DECRYPT_MODE, pk);
        return c.doFinal(data);
    }
}
