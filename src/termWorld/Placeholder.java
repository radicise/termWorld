package termWorld;
import java.io.DataOutputStream;
public class Placeholder extends Entity {
	Placeholder(int x, int y, long data, short health) {
		super();
		type = 6;
	}
	void serialize(DataOutputStream dataOut) {
	}
}
