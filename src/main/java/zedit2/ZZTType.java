package zedit2;

public class ZZTType extends ZType {
    private static final int[] charcodes = {32, 32, 32, 32, 2, 132, 157,
            4, 12, 10, 232, 240, 250, 11, 127, 179, 47,
            92, 248, 176, 176, 219, 178, 177, 254, 18, 29,
            178, 32, 206, 0, 249, 42, 205, 153, 5,
            32, 42, 94, 24, 31, 234, 227, 186, 233, 79};
    private static final int[] textcols = {8, 31, 47, 63, 79, 95, 111, 15};
    private static final String[] kindnames = {"Empty", "BoardEdge", "Messenger", "Monitor", "Player", "Ammo", "Torch",
            "Gem", "Key", "Door", "Scroll", "Passage", "Duplicator", "Bomb", "Energizer", "Star", "Clockwise",
            "Counter", "Bullet", "Water", "Forest", "Solid", "Normal", "Breakable", "Boulder", "SliderNS", "SliderEW",
            "Fake", "Invisible", "BlinkWall", "Transporter", "Line", "Ricochet", "HBlinkRay", "Bear", "Ruffian",
            "Object", "Slime", "Shark", "SpinningGun", "Pusher", "Lion", "Tiger", "VBlinkRay", "Head", "Segment",
            "CustomText", "BlueText", "GreenText", "CyanText", "RedText", "PurpleText", "BrownText", "BlackText"};
    private static final int[] linechars = {206, 204, 185, 186, 202, 200, 188, 208, 203, 201, 187, 210, 205, 198, 181, 249};
    private static final int[] duplicatorFrames = {250, 250, 249, 248, 111, 79};

    private static boolean isLineOrEdge(int id) {
        return (id == LINE) || (id == BOARDEDGE);
    }
    private static Stat getFirstStat(Board board, int x, int y)
    {
        var stats = board.getStatsAt(x, y);
        if (stats.isEmpty()) return null;
        return stats.get(0);
    }
    public static String getName(int id)
    {
        if (id >= kindnames.length) return null;
        return kindnames[id];
    }
    public static int getChar(Board board, int x, int y)
    {
        int id = board.getTileId(x, y);

        switch (id) {
            case DUPLICATOR: {
                var lastStat = getFirstStat(board, x, y);
                if (lastStat == null) break;
                return duplicatorFrames[lastStat.getP1()];
            }
            case BOMB: {
                var lastStat = getFirstStat(board, x, y);
                if (lastStat == null) break;
                int bombChar = (48 + lastStat.getP1()) & 0xFF;
                if (bombChar == 48 || bombChar == 49) bombChar = 11;
                return bombChar;
            }
            case TRANSPORTER: {
                var lastStat = getFirstStat(board, x, y);
                if (lastStat == null) break;
                int xs = lastStat.getStepX();
                int ys = lastStat.getStepY();
                if (xs < 0 && ys == 0) return 40; // '('
                if (xs > 0 && ys == 0) return 41; // ')'
                if (xs == 0 && ys > 0) return 118; // 'v'
                return 94; // '^'
            }
            case OBJECT: {
                var lastStat = getFirstStat(board, x, y);
                if (lastStat == null) break;
                return lastStat.getP1();
            }
            case PUSHER: {
                var lastStat = getFirstStat(board, x, y);
                if (lastStat == null) break;
                int xs = lastStat.getStepX();
                int ys = lastStat.getStepY();
                if (xs < 0 && ys == 0) return 17;
                if (xs > 0 && ys == 0) return 16;
                if (xs == 0 && ys < 0) return 30;
                return 31;
            }
            case LINE: {
                // For lines, the char depends on surrounding tiles
                int lch = 15;
                if ((y == 0) || isLineOrEdge(board.getTileId(x, y - 1))) lch -= 8;
                if ((y == board.getHeight() - 1) || isLineOrEdge(board.getTileId(x, y + 1))) lch -= 4;
                if ((x == board.getWidth() - 1) || isLineOrEdge(board.getTileId(x + 1, y))) lch -= 2;
                if ((x == 0) || isLineOrEdge(board.getTileId(x - 1, y))) lch -= 1;
                return linechars[lch];
            }
            case INVISIBLE: {
                if (GlobalEditor.getGlobalEditor().getBoolean("SHOW_INVISIBLES", false)) {
                    return 176;
                } else {
                    return 32;
                }
            }
        }
        // Otherwise, check in the charcodes list
        if (id < charcodes.length) return charcodes[id];
        // Otherwise, return the char as color
        return board.getTileCol(x, y);
    }

    public static int getColour(Board board, int x, int y)
    {
        int id = board.getTileId(x, y);

        if (id >= 128) {
            return id - 128;
        }
        switch (id) {
            case EMPTY: {
                // Empty is a little special- it's always black
                return 0x00;
            }
            case PLAYER: {
                // With stats, the player is c1F
                if (!board.getStatsAt(x, y).isEmpty()) {
                    return 0x1F;
                }
                break;
            }
            case CUSTOMTEXT:
            case BLUETEXT:
            case GREENTEXT:
            case CYANTEXT:
            case REDTEXT:
            case PURPLETEXT:
            case BROWNTEXT:
            case BLACKTEXT: {
                // For text kinds, the colour is based on the kind
                return getTextColour(id);
            }
            default:
                break;
        }
        // Otherwise, use the given colour
        return board.getTileCol(x, y);
    }

    public static boolean isFloor(Tile tile) {
        int id = tile.getId();
        if (id == EMPTY || id == FAKE || id == WATER) return true;
        else return false;
    }

    public static int getTextColour(int id) {
        if (id < CUSTOMTEXT) return -1;
        if (id <= BLACKTEXT) return textcols[id - CUSTOMTEXT];
        if (id < 128) return -1;
        return (id - 128);
    }

    public static int getTextId(int colour) {
        int id = (colour / 16) - 1 + BLUETEXT;
        if (id == (BLUETEXT - 1)) id = BLACKTEXT;
        return id;
    }

    public static final int TORCH = 6;

    public static final int STAR = 15;

    public static final int BULLET = 18;
    public static final int WATER = 19;

    public static final int HBLINKRAY = 33;

    public static final int SHARK = 38;

    public static final int VBLINKRAY = 43;

    public static final int CUSTOMTEXT = 46;
    public static final int BLUETEXT = 47;
    public static final int GREENTEXT = 48;
    public static final int CYANTEXT = 49;
    public static final int REDTEXT = 50;
    public static final int PURPLETEXT = 51;
    public static final int BROWNTEXT = 52;
    public static final int BLACKTEXT = 53;
    
}
