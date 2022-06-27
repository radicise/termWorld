package termWorld;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;
public class Level {
	public Entity[] ent;//read below
	public FixedFrame terrain;
	public TreeMap<Long, Integer> entities;//read below
	public static final int roomWidth = 5;
	public static final int roomHeight = 4;
	public long age;
	public int VID;
	public int spawnX;
	public int spawnY;
	public TreeMap<Long, Integer> dispFaces;//TODO make addEntity(Entity) method, returns slot or -1 if the desired space is occupied
	private int entPlace = 0;
	private int start;
	public Level(FixedFrame terrain, TreeMap<Long, Integer> entities, Entity[] ent, long age, int VID, int spawnX, int spawnY) {
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
	public synchronized void serialize(DataOutputStream strm) throws Exception {
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
	public static Level deserialize(DataInputStream strm) throws Exception {
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
			if (ent[i] == null) {
				i--;
				numEntities--;
			}
			else {
				entities.put((((long) ent[i].x) << 32) | ((long) ent[i].y), i);
			}
		}
		return new Level(new FixedFrame(width, height, tiles), entities, ent, age, VID, spawnX, spawnY);
	}
	public void display() throws Exception {
		boolean moreEntities = true;
		Long nextEntity = null;
		Long lastEntity = null;
		if (dispFaces.higherKey((long) (-1)) == null) {
			moreEntities = false;
		}
		else {
			nextEntity = dispFaces.higherKey((long) (-1));
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
	public synchronized int nextSlot() throws Exception {
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
	public static Level generate(int width, int height, long seed) {
		Random rand = new Random(seed);
		byte[] terrain = new byte[width * height];
		TreeMap<Long, Integer> entities = new TreeMap<Long, Integer>();
		int entSize = 1024;
		Entity[] ent = new Entity[entSize];
		int r;
		int s;
		int i;
		int j;
		int h;
		int spawnX = 0;
		int spawnY = 0;
		Arrays.fill(terrain, (byte) 1);
		for (i = 0; i < (terrain.length / 128); i++) {
			r = (width * (spawnY = (rand.nextInt(height - roomHeight - 1) + 1))) + (spawnX = (rand.nextInt((width - roomWidth) - 1) + 1));
			for (s = r; s < (r + (roomHeight * width)); s += width) {
				for (j = 0; j < roomWidth; j++) {
					terrain[s + j] = 0;
				}
			}
			r += (rand.nextInt(roomWidth) + (width * rand.nextInt(roomHeight)));
			h = r;
			s = (10 + rand.nextInt(10)) * ((rand.nextInt(2) * 2) - 1);
			j = s;
			if (((r % width) + s) >= width) {
				j = ((width - (r % width)) - 1);
			}
			if (((r % width) + s) < 0) {
				j = r % width;
			}
			s = (j < 0) ? (-1) : 1;
			j += r;
			for (; h != j; h += s) {
				terrain[h] = 0;
			}
			h = r;
			s = (10 + rand.nextInt(10)) * ((rand.nextInt(2) * 2) - 1);
			j = s;
			if (((r / width) + s) >= height) {
				j = (height - (r / width)) - 1;
			}
			if (((r / width) + s) < 0) {
				j = r / width;
			}
			s = (j < 0) ? (-1) : 1;
			j = (r + (j * width));
			for (; h != j; h += (s * width)) {
				terrain[h] = 0;
			}
		}
		for (r = 0; r < width; r++) {
			terrain[r] = 0;
		}
		for (r = 0; r < terrain.length; r += width) {
			terrain[r] = 0;
		}
		for (r = (terrain.length - width); r < terrain.length; r++) {
			terrain[r] = 0;
		}
		for (r = (width - 1); r < terrain.length; r+= width) {
			terrain[r] = 0;
		}
		int ePlace = 0;
		for (i = 0; i < terrain.length; i++) {
			if (terrain[i] != 1){
				r = rand.nextInt();
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
		return new Level(new FixedFrame(width, height, terrain), entities, ent, 0L, Server.version, spawnX, spawnY);
	}
}
