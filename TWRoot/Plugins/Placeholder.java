package TWRoot.Plugins;
import java.io.DataOutputStream;
public class Placeholder extends Entity {
	public Placeholder(int x, int y, long data, short health) {
		super();
		type = 6;
	}
	public void serialize(DataOutputStream dataOut) {
	}
}
