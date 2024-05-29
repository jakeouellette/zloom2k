package zedit2;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;

public class Main implements Runnable {
    public static final String VERSION = "0.39loom";
    private static String[] args;
    public static void main(String[] args) {
        //System.setProperty("sun.java2d.opengl", "True");
        System.setProperty("sun.java2d.translaccel", "true");
        System.setProperty("sun.java2d.ddforcevram", "true");
        //System.setProperty("sun.java2d.trace", "log");
        Main.args = args;

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.out.println("Failed to set system L&F");
        }
        //JFrame.setDefaultLookAndFeelDecorated(false);
        SwingUtilities.invokeLater(new Main());
    }

    @Override
    public void run() {
        try {
            var ge = GlobalEditor.getGlobalEditor();
            //Util.removeAltProcessor();
            //var editor = new WorldEditor(ge, false);
            String defaultFile;
            defaultFile = Util.evalConfigDir(ge.getString("LOAD_FILE", ""));
            if (args.length > 0) {
                defaultFile = args[args.length - 1];
            }
            WorldEditor editor;
            if (!defaultFile.isEmpty()) {
                editor = new WorldEditor(ge, new File(defaultFile));
            } else {
                var defaultSzzt = ge.getBoolean("DEFAULT_SZZT", false);
                editor = new WorldEditor(ge, defaultSzzt);
            }
            
        } catch (IOException | WorldCorruptedException e) {
            JOptionPane.showMessageDialog(null, e, "Error loading world", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
