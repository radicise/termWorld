package TWRoot.TWCommon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class ManCLI {
    private static final byte[] COMFIN = new byte[]{(byte) 'f', (byte) 'i', (byte) 'n'};
    private static final byte[] COMKIL = new byte[]{(byte) 'k', (byte) 'i', (byte) 'l'};
    private static final byte[] COMREP = new byte[]{(byte) 'r', (byte) 'e', (byte) 'p'};
    private static final char[] conv = new char[]{'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
    private static byte[] parsePassword(String raw) {
        byte[] pass = new byte[32];
        int l = raw.length();
        if (raw.charAt(0) == '0' && raw.charAt(1) == 'x') {
            for (int i = 2; i < l; i += 2) {
                pass[(i/2)-1] = (byte) (Integer.parseInt(raw.substring(i, i+2), 16));
            }
        } else {
            for (int i = 0; i < l; i ++) {
                pass[32-l+i] = (byte) raw.codePointAt(i);
            }
        }
        return pass;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("ManCLI --help");
            return;
        }
        if (args[0].equals("--help")) {
            System.out.println("\"ManCLI [hostaddr]:[hostport] -[ah] [password]\" where [hostaddr] is INetAddress, [hostport] is short, [password] is (String | 0x[--]{32})");
            return;
        }
        if (args[0].equals("--dump")) {
            if (args.length < 2) {
                System.out.println("MUST PROVIDE DUMP TARGET");
                return;
            }
            FileInputStream fis = new FileInputStream(new File(args[1]));
            byte[] data = fis.readAllBytes();
            fis.close();
            int inc = 32;
            for (int i = 0; i < data.length; i += inc) {
                int j = i;
                while (j < data.length && j < i + inc) {
                    System.out.print(((Character) conv[(data[j] & 0xf0) >> 4]).toString() + conv[data[j] & 0xf] + " ");
                    j += 1;
                }
                System.out.println();
            }
            System.out.println();
            return;
        }
        if (args.length < 3) {
            System.out.println("Failed due to insufficient args");
            return;
        }
        if (args[1].charAt(1) != 'h') {
            throw new Exception("AUTH NOT IMPLEMENTED");
        }
        String[] a_comps = args[0].split(":");
        // System.out.println(Arrays.toString(parsePassword(args[2])));
        byte[] password = parsePassword(args[2]);
        Socket socket = new Socket(a_comps[0], Integer.parseInt(a_comps[1]));
        DataInputStream dIn = new DataInputStream(socket.getInputStream());
        DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
        dOut.write(0x01);
        byte[] serpubkbyt = new byte[dIn.readInt()];
        dIn.read(serpubkbyt);
        PublicKey serpubk = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(serpubkbyt));
        byte[] epass = Sec.RSAEncrypt(serpubk, password);
        dOut.writeInt(epass.length);
        dOut.write(epass);
        if (dIn.readInt() == 0x55) {
            System.out.println("FAILED TO AUTHORIZE MAN CON");
            socket.close();
            return;
        }
        System.out.println("AUTHORIZED CONNECTION");
        Sec.init();
        KeyPair kp = Sec.kg.genKeyPair();
        byte[] pkeb = kp.getPublic().getEncoded();
        dOut.writeInt(pkeb.length);
        dOut.write(pkeb);
        byte[] symkeybyt = new byte[dIn.readInt()];
        dIn.read(symkeybyt);
        // System.out.println(Arrays.toString(symkeybyt));
        Sec cry = new Sec(Sec.RSADecrypt(kp.getPrivate(), symkeybyt));
        System.out.println("KEY EXCHANGE SUCCESS");
        while (true) {
            byte[] com = System.in.readNBytes(3);
            System.in.readNBytes(1);
            if (Arrays.equals(com, COMFIN)) {
                dOut.writeInt((int) cry.crypt(0x00));
                socket.close();
                return;
            }
            if (Arrays.equals(com, COMKIL)) {
                dOut.writeInt((int) cry.crypt(0x04));
                dOut.writeInt((int) cry.crypt(0x00));
                socket.close();
                return;
            }
            if (Arrays.equals(com, COMREP)) {
                dOut.writeInt((int) cry.crypt(0x10));
                String dbg = new String(dIn.readNBytes(dIn.readInt()), StandardCharsets.UTF_16BE);
                System.out.println(dbg);
            }
        }
    }
}
