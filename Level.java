package termWorld;
import java.util.TreeMap;
public class Level {
	FixedFrame terrain;
	TreeMap<Long, Entity> entities;
	long age;
	Level(FixedFrame terrain, TreeMap<Long, Entity> entities) {
		this.terrain = terrain;
		this.entities = entities;
	}
	void display() throws Exception {
		Long nextEntity = entities.firstKey();
		boolean moreEntities = nextEntity != null;
		Long lastEntity = entities.lastKey();
		int o = 0;
		for (int i = 0; i < terrain.height; i++) {
			for (int n = 0; n < terrain.width; n++) {
				if (moreEntities && (nextEntity == o)) {
					Text.buffered.write(entities.get(nextEntity).face);
					nextEntity = entities.higherKey(nextEntity);
					if (o == lastEntity) {
						moreEntities = false;
					}
				}
				else {
					Text.buffered.write(Text.tiles[terrain.tiles[i * terrain.width + n]]);
				}
				o++;
			}
			Text.buffered.write(Text.delimiter);
		}
	}
}
