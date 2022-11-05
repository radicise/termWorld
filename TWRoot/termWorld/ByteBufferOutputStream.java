package TWRoot.termWorld;
import java.io.OutputStream;
import java.nio.ByteBuffer;
public class ByteBufferOutputStream extends OutputStream {
	public ByteBufferOutputStream(ByteBuffer buf) throws Exception {
		if (buf != Server.buf) {
			throw new Exception();
		}
	}
	public void close() {
	}
	public void write(byte[] bs) {
		Server.buf.put(bs, 0, bs.length);
	}
	public void write(byte[] bs, int offset, int length) {
		Server.buf.put(bs, offset, length);
	}
	public void write(int da) {
		Server.buf.put((byte) da);
	}
	public void flush(int cha) {
	}
}
