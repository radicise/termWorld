package TWRoot.Util;

import java.io.IOException;
import java.io.InputStream;

public class ServiceHelper implements Runnable {
    InputStream in;
    public ServiceHelper(InputStream in) {
        this.in = in;
    }
    public void run() {
        try {
            while (true) {
                byte[] b = in.readNBytes(in.available());
                if (b.length > 0) {
                    System.out.write(b);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}
