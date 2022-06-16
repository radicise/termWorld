package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Random;
import java.util.TreeMap;
class Level {
	static final int extension = 36;
	static final int bytesPerEntity = 19;
	Entity[] ent;
	FixedFrame terrain;
	TreeMap<Long, Integer> entities;
	long age;
	int VID;
	int spawnX;
	int spawnY;
	TreeMap<Long, Integer> dispFaces;
	private int entPlace = 0;
	private int start;
	Level(FixedFrame terrain, TreeMap<Long, Integer> entities, Entity[] ent, long age, int VID, int spawnX, int spawnY) {
		this.terrain = terrain;
		this.entities = entities;
        this.age = age;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
		this.ent = ent;
		this.VID = VID;
		dispFaces = new TreeMap<Long, Integer>();
		entities.forEach((Long L, Integer I) -> {
			dispFaces.put((L >>> 32) ^ (L << 32), I);
		});
	}
	synchronized void serialize(DataOutputStream strm) throws Exception {
		strm.writeInt(VID);
		strm.writeInt(terrain.width);
		strm.writeInt(terrain.height);
		strm.writeInt(ent.length);
		strm.write(terrain.tiles);
		strm.writeLong(age);
		strm.writeInt(spawnX);
		strm.writeInt(spawnY);
		strm.writeInt(entities.size());
		for (Integer I : entities.values()) {
			ent[I].serialize(strm);
		}
	}
	static Level deserialize(DataInputStream strm) throws Exception {
		int VID = strm.readInt();
		int width = strm.readInt();
		int height = strm.readInt();
		int marker = width * height;
		Entity[] ent = new Entity[strm.readInt()];
		byte[] tiles = new byte[marker];
		strm.read(tiles);
		long age = strm.readLong();
		int spawnX = strm.readInt();
		int spawnY = strm.readInt();
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		int numEntities = strm.readInt();
		for (int i = 0; i < numEntities; i++) {
			ent[i] = Entity.deserialize(strm);
			entities.put((((long) ent[i].x) << 32) | ((long) ent[i].y), i);
		}
		return new Level(new FixedFrame(width, height, tiles), entities, ent, age, VID, spawnX, spawnY);
	}
	void display() throws Exception {
		boolean moreEntities = true;
		Long nextEntity = null;
		Long lastEntity = null;
		if (dispFaces.isEmpty()) {
			moreEntities = false;
		}
		else {
			nextEntity = dispFaces.firstKey();
			lastEntity = dispFaces.lastKey();
		}
		long o = 0;
		int p;
		Text.buffered.write(Text.delimiter);
		for (int i = 0; i < terrain.height; i++) {
			o = ((long) i) << 32;
			for (int n = 0; n < terrain.width; n++) {
				if (moreEntities && (nextEntity == o)) {
					p = dispFaces.get(nextEntity);
					if (Text.escapes) {
						Text.buffered.write(Text.colors[Server.level.ent[p].color]);
						Text.buffered.write(Server.level.ent[p].face);
						Text.buffered.write(Text.colorClear);
					}
					else {
						Text.buffered.write(Server.level.ent[p].face);
					}
					nextEntity = dispFaces.higherKey(nextEntity);
					if (o == lastEntity) {
						moreEntities = false;
					}
				}
				else {
					Text.buffered.write(Text.tiles[terrain.tiles[i * terrain.width + n]]);
				}
				o += 1;
			}
			Text.buffered.write(Text.delimiter);
		}
		Text.buffered.write(Text.delimiter);
	}
	synchronized int nextSlot() throws Exception {
		start = entPlace;
		while (ent[entPlace] != null) {
			entPlace++;
			if (entPlace == ent.length) {
				entPlace = 0;
			}
			if (entPlace == start) {
				throw new Exception("Could not allocate Entity slot");
			}
		}
		ent[entPlace] = new Entity(0, 0, 0L, (short) 0);
		return entPlace;
	}
	static Level generate(int width, int height, long seed) {
		Random rand = new Random(seed);
		byte[] terrain = new byte[width * height];
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		int entSize = 1024;
		Entity[] ent = new Entity[entSize];
		int r;
		int ePlace = 0;
		for (int i = 0; i < terrain.length; i++) {
			r = rand.nextInt();
			if (r < -((Integer.MAX_VALUE / 2) + 1)) {
				terrain[i] = 1;
			}
			else {
				switch (r & 0xf) {
					case (0):
					case (1):
						terrain[i] = 3;
						break;
					case (2):
						terrain[i] = 2;
						break;
				}
				switch ((r >>> 4) & 0x3f) {
					case (0):
						ent[ePlace] = new Dog(i % width, i / width, (r >>> 10) & 0x1cf, (short) 10);
						entities.put((((long) (i % width)) << 32) ^ ((long) (i / width)), ePlace);
						ePlace++;
						break;
					case (1):
					case (2):
					case (3):
						ent[ePlace] = new EntityItem(i % width, i / width, new Item(Item.Thing.CLOTHPATCH, (byte) 50), (r >>> 10) & 0x1c0);
						entities.put((((long) (i % width)) << 32) ^ ((long) (i / width)), ePlace);
						ePlace++;
						break;
				}
			}
		}
		return new Level(new FixedFrame(width, height, terrain), entities, ent, 0L, Server.version, 0, 0);
	}
}
