package TWRoot.Plugins;

public class TestPlug extends Plugin {
    public static void main(String[] args) {
        PluginMaster.serverPlugs.add(TestPlug.class);
        System.out.println("I HAVE LIFE");
    }
}
