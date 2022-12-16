package TWRoot.Plugins.Std;

import java.io.File;
import java.io.FileInputStream;

import TWRoot.Plugins.Plugin;
import TWRoot.Plugins.PluginMaster;
import TWRoot.Plugins.SpaceFiller;

/**
 * loads standard entities
 */
@SuppressWarnings("unchecked")
public class Loader extends Plugin {
    /**
     * {}
     */
    public static Class<? extends SpaceFiller>[] contributes = new Class[]{Dog.class, Explosive.class};
    public static Class<? extends SpaceFiller>[] contiles = new Class[]{TileWall.class};
    public static void main(String[] args) throws Exception {
        FileInputStream fIn = new FileInputStream(new File("TWRoot/Plugins/Std/config.txt"));
        String[] lines = new String(fIn.readAllBytes()).split("\n");
        fIn.close();
        for (int i = 0; i < contributes.length; i ++) {
            if (i >= lines.length || lines[i].charAt(0) == '-') {
                contributes[i] = null;
            }
        }
        PluginMaster.commonPlugs.add(Loader.class);
    }
}
