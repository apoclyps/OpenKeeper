/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package toniarts.opendungeonkeeper.tools.convert.map;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.vecmath.Vector3f;
import toniarts.opendungeonkeeper.tools.convert.Utils;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.Animation;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.Image;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.Mesh;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.Proc;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.ResourceType;
import toniarts.opendungeonkeeper.tools.convert.map.ArtResource.TerrainResource;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.Attraction;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.Spell;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.Unk7;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.X1323;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.Xe7c;
import toniarts.opendungeonkeeper.tools.convert.map.Creature.Xe94;
import toniarts.opendungeonkeeper.tools.convert.map.Door.DoorFlag;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.CREATURES;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.CREATURE_SPELLS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.DOORS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.EFFECTS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.EFFECT_ELEMENTS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.KEEPER_SPELLS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.MAP;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.OBJECTS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.PLAYERS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.ROOMS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.SHOTS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.TERRAIN;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.THINGS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.TRAPS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.TRIGGERS;
import static toniarts.opendungeonkeeper.tools.convert.map.MapDataTypeEnum.VARIABLES;
import toniarts.opendungeonkeeper.tools.convert.map.Object;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.ActionPoint;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing03;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing08;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing08.HeroPartyData;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing10;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing11;
import toniarts.opendungeonkeeper.tools.convert.map.Thing.Thing12;
import toniarts.opendungeonkeeper.tools.convert.map.Trigger.TriggerAction;
import toniarts.opendungeonkeeper.tools.convert.map.Trigger.TriggerGeneric;

/**
 * Reads a DK II map file, the KWD is the file name of the main map identifier,
 * reads the KLDs actually<br>
 * The files are LITTLE ENDIAN I might say<br>
 * Some values are 3D coordinates or scale values presented in fixed point
 * integers. They are automatically converted to floats (divided by 2^12 = 4096
 * or 2^16 = 65536)<br>
 * Many parts adapted from C code by:
 * <li>George Gensure (werkt)</li>
 * And another C code implementation by:
 * <li>Thomasz Lis</li>
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class KwdFile {

    public enum LevFlag implements IFlagEnum {

        //UNKNOWN(0x0004), // unknown; always on in maps
        ALWAYS_IMPRISON_ENEMIES(0x0008), // Always imprison enemies
        ONE_SHOT_HORNY(0x0010), // Set if one shot Horny spell is available
        IS_SECRET_LEVEL(0x0020), // The map is Secret level
        IS_SPECIAL_LEVEL(0x0040), // The map is Special level
        SHOW_HERO_KILLS(0x0080), // Display "Heroes killed" tally
        AUTO_OBJECTIVE_BOX(0x0100), // Automatic show objective box
        HEART_MAKES_GEM(0x0200), // Last heart generates Portal Gem
        IS_MULTIPLAYER_LEVEL(0x0400), // The map is Multiplayer level
        IS_SKIRMISH_LEVEL(0x0800), // The map is Skirmish level
        FREEZE_OPTIONS(0x1000), // Freeze game options
        IS_MY_PET_DUNGEON_LEVEL(0x2000); // The map is My Pet Dungeon level
        private final long flagValue;

        private LevFlag(long flagValue) {
            this.flagValue = flagValue;
        }

        @Override
        public long getFlagValue() {
            return flagValue;
        }
    };
    private static final float FIXED_POINT_DIVISION = 4096f;
    private static final float FIXED_POINT5_DIVISION = 65536f;
    // KWD data
//    struct LevelInfoBlock {
//        ucs2le_t m_wsName[64]; /* 134 */
//        ucs2le_t m_wsDescription[1024]; /* 1b4 */
//        ucs2le_t m_wsAuthor[64]; /* 9b4 */
//        ucs2le_t m_wsEmail[64]; /* a34 */
//        ucs2le_t m_wsInformation[1024]; /* ab4 */
//        uint16_t m_wShortId0;
//        uint16_t m_wShortId1;
//        uint8_t x01184[520];
//        ucs2le_t m_wsUnknown0138c[20][512];
//        uint16_t x0638c;
//        char x0638e[32];
//        uint8_t x063ae;
//        uint8_t x063af[4];
//        uint8_t x063b3[4];
//        uint8_t x063b7;
//        uint8_t x063b8;
//        uint16_t x063b9;
//        uint16_t x063bb;
//        uint16_t x063bd;
//        uint16_t x063bf;
//        uint16_t x063c3;
//        uint16_t x063c5;
//        uint16_t x063c7;
//        uint16_t x063c9;
//        uint16_t x063ca;
//        uint8_t x063cb[8];
//        uint16_t x063d3[8];
//        char x063e3[32];
//        uint8_t x06403;
//        uint8_t x06404;
//        uint8_t x06405;
//        uint8_t x06406;
//        uint16_t x06407;
//        uint16_t x06409[5];
//        ucs2le_t x06413[32];
//        };
    private String name;
    private String description;
    private String author;
    private String email;
    private String information;
    private int mWShortId0;
    private int ticksPerSec;
    private short x01184[];
    private String messages[];
    private EnumSet<LevFlag> lvflags;
    private String speechStr;
    private short talismanPieces;
    private short rewardPrev[];
    private short rewardNext[];
    private short soundTrack;
    private short textTableId;
    private int textTitleId;
    private int textPlotId;
    private int textDebriefId;
    private int textObjectvId;
    private int x063c3; //this may be first text_subobjctv_id - not sure
    private int textSubobjctvId1;
    private int textSubobjctvId2;
    private int textSubobjctvId3;
    private int speclvlIdx;
    private short textIntrdcOverrdObj[];
    private int textIntrdcOverrdId[];
    private String terrainPath;
    private short oneShotHornyLev;
    private short x06404;
    private short x06405; // rewardPrev[4]??
    private short x06406; // rewardNext[4]??
    private int speechHornyId;
    private int speechPrelvlId;
    private int speechPostlvlWin;
    private int speechPostlvlLost;
    private int speechPostlvlNews;
    private int speechPrelvlGenr;
    private String heroName;
    //
    private Date timestamp1; // Seem to be the same these two timeStamps, maybe checks?
    private Date timestamp2;
    private FilePath paths[];
    private int unknown[];
    //
    private Map[][] tiles;
    private int width;
    private int height;
    private HashMap<Short, Player> players;
    private HashMap<Short, Terrain> terrainTiles;
    private HashMap<Short, Door> doors;
    private List<Trap> traps;
    private HashMap<Short, Room> rooms;
    private HashMap<Short, Creature> creatures;
    private HashMap<Short, Object> objects;
    private List<CreatureSpell> creatureSpells;
    private HashMap<Integer, EffectElement> effectElements;
    private HashMap<Integer, Effect> effects;
    private HashMap<Short, KeeperSpell> keeperSpells;
    private List<Thing> things;
    private HashMap<Short, Shot> shots;
    private List<Trigger> triggers;
    private List<Variable> variables;
    private boolean customOverrides = false;
    //
    private static final Logger logger = Logger.getLogger(KwdFile.class.getName());
    /**
     * Somehow reading a global overrided file some of the items are not
     * correctly sized, but they seem to load ok<br>
     * It is not empty padding, it is data, but what kind, I don't know
     */
    private static final HashMap<MapDataTypeEnum, Long> ITEM_SIZES = new HashMap<>(MapDataTypeEnum.values().length);

    static {
        ITEM_SIZES.put(MapDataTypeEnum.CREATURES, 5449l);
        ITEM_SIZES.put(MapDataTypeEnum.CREATURE_SPELLS, 266l);
        ITEM_SIZES.put(MapDataTypeEnum.DOORS, 616l);
        ITEM_SIZES.put(MapDataTypeEnum.EFFECTS, 246l);
        ITEM_SIZES.put(MapDataTypeEnum.EFFECT_ELEMENTS, 182l);
        ITEM_SIZES.put(MapDataTypeEnum.KEEPER_SPELLS, 406l);
        ITEM_SIZES.put(MapDataTypeEnum.OBJECTS, 894l);
        ITEM_SIZES.put(MapDataTypeEnum.PLAYERS, 205l);
        ITEM_SIZES.put(MapDataTypeEnum.ROOMS, 1055l);
        ITEM_SIZES.put(MapDataTypeEnum.SHOTS, 239l);
        ITEM_SIZES.put(MapDataTypeEnum.TERRAIN, 552l);
        ITEM_SIZES.put(MapDataTypeEnum.TRAPS, 579l);
    }

    /**
     * Constructs a new KWD file reader<br>
     * Reads the whole map and its catalogs (either standard ones or custom
     * ones)
     *
     * @param basePath path to DK II main path (or where ever is the "root")
     * @param file the KWD file to read
     */
    public KwdFile(String basePath, File file) {

        // Load the actual main map info (paths to catalogs most importantly)
        readMapInfo(file);

        // Now we have the paths, read all of those in order
        if (!basePath.endsWith(File.separator)) {
            basePath = basePath.concat(File.separator);
        }
        for (FilePath path : paths) {

            // Paths are relative to the base path, may or may not have an extension (assume kwd if none found)
            String filePath = basePath.concat(path.getPath().replaceAll(Pattern.quote("\\"), Matcher.quoteReplacement(File.separator)));
            if (!".".equals(filePath.substring(filePath.length() - 4, filePath.length() - 3))) {
                filePath = filePath.concat(".kwd");
            }

            // See if the globals are present
            if (path.getId() == MapDataTypeEnum.GLOBALS) {
                customOverrides = true;
                logger.info("The map uses custom overrides!");
            }

            // Open the file
            try (RandomAccessFile data = new RandomAccessFile(filePath, "r")) {

                // Read the file until EOF, normally it is one data type per file, but with Globals, it is all in the same file
                do {

                    // Read header (and put the file pointer to the data start)
                    KwdHeader header = readKwdHeader(data);
                    readFileContents(header, data);

                    // Only loop with Globals
                } while ((data.getFilePointer() <= data.length() && path.getId() == MapDataTypeEnum.GLOBALS));

            } catch (Exception e) {

                //Fug
                throw new RuntimeException("Failed to read the file " + filePath + "!", e);
            }
        }

        // Hmm, seems that normal maps don't refer the effects nor effect elements
        List<String> unreadFilePaths = new ArrayList<>();
        if (effects == null) {
            unreadFilePaths.add(basePath.concat("Data").concat(File.separator).concat("editor").concat(File.separator).concat("Effects.kwd"));
        }
        if (effectElements == null) {
            unreadFilePaths.add(basePath.concat("Data").concat(File.separator).concat("editor").concat(File.separator).concat("EffectElements.kwd"));
        }

        // Loop through the unprocessed files
        for (String filePath : unreadFilePaths) {
            try (RandomAccessFile data = new RandomAccessFile(filePath, "r")) {

                // Read header (and put the file pointer to the data start)
                KwdHeader header = readKwdHeader(data);
                readFileContents(header, data);
            } catch (Exception e) {

                //Fug
                throw new RuntimeException("Failed to read the file " + filePath + "!", e);
            }
        }
    }

    /**
     * Reads the common KWD header
     *
     * @param data the data file
     * @return the header
     * @throws IOException may fail reading
     */
    private KwdHeader readKwdHeader(RandomAccessFile data) throws IOException {

        //Mark the position
        long offset = data.getFilePointer();

        KwdHeader header = new KwdHeader();
        header.setId(parseEnum(Utils.readUnsignedInteger(data), MapDataTypeEnum.class));
        int size = Utils.readUnsignedInteger(data); // Bytes in the real size indicator, well seems to be 4 always
        byte[] bytes = new byte[size];
        data.read(bytes);
        if (size == 2) {
            header.setSize(Utils.readUnsignedShort(bytes));
        } else if (size == 4) {
            header.setSize(Utils.readUnsignedInteger(bytes));
        }

        // Handle few special cases, always rewind the file to data start
        switch (header.getId()) {
            case MAP: {

                // Width & height
                data.seek(offset + 20);
                header.setWidth(Utils.readUnsignedInteger(data));
                header.setHeight(Utils.readUnsignedInteger(data));

                // Seek to start, starts straight after 36 byte header
                data.seek(offset + 36);
                header.setHeaderSize(36);
                break;
            }
            case TRIGGERS: {

                // A bit special, item count is dw08 + x0c[0]
                data.seek(offset + 20);
                header.setItemCount(Utils.readUnsignedInteger(data) + Utils.readUnsignedInteger(data));

                // Seek to start (40 byte header + 20 bytes of something)
                data.seek(offset + 60);
                header.setHeaderSize(60);
                break;
            }
            default: {

                // Item count
                data.seek(offset + 20);
                header.setItemCount(Utils.readUnsignedInteger(data));

                // Seek to start (36 byte header + 20 bytes of something)
                data.seek(offset + 56);
            }
        }

        return header;
    }

    private void readFileContents(KwdHeader header, RandomAccessFile data) throws IOException {

        // Check the item size (just log)
        Long wantedItemSize = ITEM_SIZES.get(header.getId());
        if (wantedItemSize != null) {
            if (wantedItemSize != header.getItemSize()) {
                logger.log(Level.WARNING, "{0} item size is {1} and it should be {2}!", new java.lang.Object[]{header.getId(), header.getItemSize(), wantedItemSize});
            }
        }

        // Handle all the cases (we kinda skip the globals with this logic, so no need)
        // All readers must read the whole data they inted to read
        switch (header.getId()) {
            case CREATURES: {
                readCreatures(header, data);
                break;
            }
            case CREATURE_SPELLS: {
                readCreatureSpells(header, data);
                break;
            }
            case DOORS: {
                readDoors(header, data);
                break;
            }
            case EFFECTS: {

                // Hmm, seem not to be referenced on normal maps
                readEffects(header, data);
                break;
            }
            case EFFECT_ELEMENTS: {

                // Hmm, seem not to be referenced on normal maps
                readEffectElements(header, data);
                break;
            }
            case KEEPER_SPELLS: {
                readKeeperSpells(header, data);
                break;
            }
            case MAP: {
                readMap(header, data);
                break;
            }
            case OBJECTS: {
                readObjects(header, data);
                break;
            }
            case PLAYERS: {
                readPlayers(header, data);
                break;
            }
            case ROOMS: {
                readRooms(header, data);
                break;
            }
            case SHOTS: {
                readShots(header, data);
                break;
            }
            case TERRAIN: {
                readTerrain(header, data);
                break;
            }
            case THINGS: {
                readThings(header, data);
                break;
            }
            case TRAPS: {
                readTraps(header, data);
                break;
            }
            case TRIGGERS: {
                readTriggers(header, data);
                break;
            }
            case VARIABLES: {

                // The global variables is not read to its full extend...
                readVariables(header, data);
                break;
            }
        }
    }

    /**
     * Get the map width
     *
     * @return map width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the map height
     *
     * @return map height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Reads the *Map.kld
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readMap(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the requested MAP file
        logger.info("Reading map!");
        width = header.getWidth();
        height = header.getHeight();
        tiles = new Map[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Map map = new Map();
                map.setTerrainId((short) file.readUnsignedByte());
                map.setPlayerId((short) file.readUnsignedByte());
                map.setFlag((short) file.readUnsignedByte());
                map.setUnknown((short) file.readUnsignedByte());
                tiles[x][y] = map;
            }
        }
    }

    /**
     * Reads the *Players.kld
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readPlayers(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the requested PLAYER file
        logger.info("Reading players!");
        players = new HashMap<>(header.getItemCount());
        for (int playerIndex = 0; playerIndex < header.getItemCount(); playerIndex++) {
            long offset = file.getFilePointer();
            Player player = new Player();
            player.setStartingGold(Utils.readInteger(file));
            player.setUnknown2(Utils.readInteger(file));
            short[] unknown3 = new short[158];
            for (int i = 0; i < unknown3.length; i++) {
                unknown3[i] = (short) file.readUnsignedByte();
            }
            player.setUnknown3(unknown3);
            player.setUnknown4(Utils.readUnsignedShort(file));
            player.setPlayerId((short) file.readUnsignedByte());
            player.setUnknown5(Utils.readUnsignedShort(file));
            player.setUnknown6(Utils.readUnsignedShort(file));
            byte[] bytes = new byte[32];
            file.read(bytes);
            player.setName(Utils.bytesToString(bytes).trim());

            // Add to the hash by the player ID
            players.put(player.getPlayerId(), player);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the Terrain.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readTerrain(KwdHeader header, RandomAccessFile file) throws RuntimeException, IOException {

        // Read the terrain catalog
        logger.info("Reading terrain!");
        terrainTiles = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Terrain terrain = new Terrain();
            byte[] bytes = new byte[32];
            file.read(bytes);
            terrain.setName(Utils.bytesToString(bytes).trim());
            terrain.setComplete(readArtResource(file));
            terrain.setSide(readArtResource(file));
            terrain.setTop(readArtResource(file));
            terrain.setTagged(readArtResource(file));
            terrain.setStringIds(readStringId(file));
            terrain.setUnk188(Utils.readUnsignedInteger(file));
            terrain.setLightHeight(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            terrain.setFlags(Utils.readUnsignedInteger(file));
            terrain.setDamage(Utils.readUnsignedShort(file));
            terrain.setUnk196(Utils.readUnsignedShort(file));
            terrain.setUnk198(Utils.readUnsignedShort(file));
            terrain.setGoldValue(Utils.readUnsignedShort(file));
            terrain.setManaGain(Utils.readUnsignedShort(file));
            terrain.setMaxManaGain(Utils.readUnsignedShort(file));
            terrain.setUnk1a0(Utils.readUnsignedShort(file));
            terrain.setUnk1a2(Utils.readUnsignedShort(file));
            terrain.setUnk1a4(Utils.readUnsignedShort(file));
            terrain.setUnk1a6(Utils.readUnsignedShort(file));
            terrain.setUnk1a8(Utils.readUnsignedShort(file));
            terrain.setUnk1aa(Utils.readUnsignedShort(file));
            terrain.setUnk1ac(Utils.readUnsignedShort(file));
            int[] unk1ae = new int[16];
            for (int x = 0; x < unk1ae.length; x++) {
                unk1ae[x] = Utils.readUnsignedShort(file);
            }
            terrain.setUnk1ae(unk1ae);
            terrain.setWibbleH((short) file.readUnsignedByte());
            short[] leanH = new short[3];
            for (int x = 0; x < leanH.length; x++) {
                leanH[x] = (short) file.readUnsignedByte();
            }
            terrain.setLeanH(leanH);
            terrain.setWibbleV((short) file.readUnsignedByte());
            short[] leanV = new short[3];
            for (int x = 0; x < leanV.length; x++) {
                leanV[x] = (short) file.readUnsignedByte();
            }
            terrain.setLeanV(leanV);
            terrain.setTerrainId((short) file.readUnsignedByte());
            terrain.setStartingHealth(Utils.readUnsignedShort(file));
            terrain.setMaxHealthType((short) file.readUnsignedByte());
            terrain.setDestroyedType((short) file.readUnsignedByte());
            terrain.setTerrainLight(new Color(file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte()));
            terrain.setTextureFrames((short) file.readUnsignedByte());
            bytes = new byte[32];
            file.read(bytes);
            terrain.setStr1(Utils.bytesToString(bytes).trim());
            terrain.setMaxHealth(Utils.readUnsignedShort(file));
            terrain.setAmbientLight(new Color(file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte()));
            bytes = new byte[32];
            file.read(bytes);
            terrain.setStr2(Utils.bytesToString(bytes).trim());
            terrain.setUnk224(Utils.readUnsignedInteger(file));

            // Add to the hash by the terrain ID
            terrainTiles.put(terrain.getTerrainId(), terrain);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads and parses an ArtResource object from the current file location
     *
     * @param file the file stream to parse from
     * @return an ArtResource
     */
    private ArtResource readArtResource(RandomAccessFile file) throws IOException {
        ArtResource artResource = new ArtResource();

        // Read the data
        byte[] bytes = new byte[64];
        file.read(bytes);
        artResource.setName(Utils.bytesToString(bytes).trim());
        int flags = Utils.readUnsignedInteger(file);
        bytes = new byte[12];
        file.read(bytes); // Depends on the type how these are interpreted?
        short type = (short) file.readUnsignedByte();
        short startAf = (short) file.readUnsignedByte();
        short endAf = (short) file.readUnsignedByte();
        short sometimesOne = (short) file.readUnsignedByte();

        // Debug
//        System.out.println("Name: " + artResource.getName());
//        System.out.println("Type: " + type);
//        System.out.println("Flag: " + flags);

        // Mesh collection (type 8) has just the name, reference to GROP meshes probably
        // And alphas and images probably share the same attributes
        ResourceType resourceType = artResource.new ResourceType();
        switch (type) {
            case 1:
            case 2:
            case 3: { // Images of different type
                resourceType = artResource.new Image();
                ((Image) resourceType).setWidth(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 0, 4)) / FIXED_POINT_DIVISION);
                ((Image) resourceType).setHeight(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 4, 8)) / FIXED_POINT_DIVISION);
                ((Image) resourceType).setFrames(Utils.readUnsignedShort(Arrays.copyOfRange(bytes, 8, 10)));
                break;
            }
            case 4: {
                resourceType = artResource.new TerrainResource();
                ((TerrainResource) resourceType).setX00(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 0, 4)));
                ((TerrainResource) resourceType).setX04(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 4, 8)));
                ((TerrainResource) resourceType).setFrames(Utils.toUnsignedByte(bytes[8]));
                break;
            }
            case 5: {
                resourceType = artResource.new Mesh();
                ((Mesh) resourceType).setScale(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 0, 4)) / FIXED_POINT_DIVISION);
                ((Mesh) resourceType).setFrames(Utils.readUnsignedShort(Arrays.copyOfRange(bytes, 4, 6)));
                break;
            }
            case 6: {
                resourceType = artResource.new Animation();
                ((Animation) resourceType).setFrames(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 0, 4)));
                ((Animation) resourceType).setFps(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 4, 8)));
                ((Animation) resourceType).setStartDist(Utils.readUnsignedShort(Arrays.copyOfRange(bytes, 8, 10)));
                ((Animation) resourceType).setEndDist(Utils.readUnsignedShort(Arrays.copyOfRange(bytes, 10, 12)));
                break;
            }
            case 7: {
                resourceType = artResource.new Proc();
                ((Proc) resourceType).setId(Utils.readUnsignedInteger(Arrays.copyOfRange(bytes, 0, 4)));
                break;


            }
        }

        // Add the common values
        resourceType.setFlags(parseFlagValue(flags, ArtResource.ArtResourceFlag.class));
        resourceType.setType(parseEnum(type, ArtResource.Type.class));
        resourceType.setStartAf(startAf);

        resourceType.setEndAf(endAf);

        resourceType.setSometimesOne(sometimesOne);

        artResource.setSettings(resourceType);
        return artResource;
    }

    /**
     * Reads and parses an StringId object from the current file location
     *
     * @param file the file stream to parse from
     * @return an StringId
     */
    private StringId readStringId(RandomAccessFile file) throws IOException {

        // Read the IDs
        int[] ids = new int[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Utils.readUnsignedInteger(file);
        }

        // And the unknowns
        short[] x14 = new short[4];
        for (int i = 0; i < x14.length; i++) {
            x14[i] = (short) file.readUnsignedByte();
        }

        return new StringId(ids, x14);
    }

    /**
     * Reads the Doors.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readDoors(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the doors catalog
        logger.info("Reading doors!");
        doors = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Door door = new Door();
            byte[] bytes = new byte[32];
            file.read(bytes);
            door.setName(Utils.bytesToString(bytes).trim());
            door.setMesh(readArtResource(file));
            door.setGuiIcon(readArtResource(file));
            door.setEditorIcon(readArtResource(file));
            door.setFlowerIcon(readArtResource(file));
            door.setOpenResource(readArtResource(file));
            door.setCloseResource(readArtResource(file));
            door.setHeight(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            door.setHealthGain(Utils.readUnsignedShort(file));
            short[] unknown2 = new short[8];
            for (int x = 0; x < unknown2.length; x++) {
                unknown2[x] = (short) file.readUnsignedByte();
            }
            door.setUnknown2(unknown2);
            door.setMaterial(parseEnum(file.readUnsignedByte(), Material.class));
            door.setTrapTypeId((short) file.readUnsignedByte());
            int flag = Utils.readUnsignedInteger(file);
            door.setFlags(parseFlagValue(flag, DoorFlag.class));
            door.setHealth(Utils.readUnsignedShort(file));
            door.setGoldCost(Utils.readUnsignedShort(file));
            short[] unknown3 = new short[2];
            for (int x = 0; x < unknown3.length; x++) {
                unknown3[x] = (short) file.readUnsignedByte();
            }
            door.setUnknown3(unknown3);
            door.setDeathEffectId(Utils.readUnsignedShort(file));
            door.setManufToBuild(Utils.readUnsignedInteger(file));
            door.setManaCost(Utils.readUnsignedShort(file));
            door.setTooltipStringId(Utils.readUnsignedShort(file));
            door.setNameStringId(Utils.readUnsignedShort(file));
            door.setGeneralDescriptionStringId(Utils.readUnsignedShort(file));
            door.setStrengthStringId(Utils.readUnsignedShort(file));
            door.setWeaknessStringId(Utils.readUnsignedShort(file));
            door.setDoorId((short) file.readUnsignedByte());
            door.setOrderInEditor((short) file.readUnsignedByte());
            door.setManufCrateObjectId((short) file.readUnsignedByte());
            door.setKeyObjectId((short) file.readUnsignedByte());
            bytes = new byte[32];
            file.read(bytes);
            door.setSoundGategory(Utils.bytesToString(bytes).trim());

            doors.put(door.getDoorId(), door);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the Traps.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readTraps(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the traps catalog
        logger.info("Reading traps!");
        traps = new ArrayList<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Trap trap = new Trap();
            byte[] bytes = new byte[32];
            file.read(bytes);
            trap.setName(Utils.bytesToString(bytes).trim());
            ArtResource[] ref = new ArtResource[5];
            for (int x = 0; x < ref.length; x++) {
                ref[x] = readArtResource(file);
            }
            trap.setRef(ref);
            short[] data = new short[127];
            for (int x = 0; x < data.length; x++) {
                data[x] = (short) file.readUnsignedByte();
            }
            trap.setData(data);

            traps.add(trap);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the Rooms.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readRooms(KwdHeader header, RandomAccessFile file) throws RuntimeException, IOException {

        // Read the rooms catalog
        logger.info("Reading rooms!");
        rooms = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Room room = new Room();
            byte[] bytes = new byte[32];
            file.read(bytes);
            room.setName(Utils.bytesToString(bytes).trim());
            room.setGuiIcon(readArtResource(file));
            room.setRoomIcon(readArtResource(file));
            room.setComplete(readArtResource(file));
            ArtResource[] ref = new ArtResource[7];
            for (int x = 0; x < ref.length; x++) {
                ref[x] = readArtResource(file);
            }
            room.setRef(ref);
            room.setUnknown1(Utils.readUnsignedInteger(file));
            room.setUnknown2(Utils.readUnsignedShort(file));
            room.setTorchIntensity(Utils.readUnsignedShort(file));
            room.setUnknown3(Utils.readUnsignedInteger(file));
            room.setX374(Utils.readUnsignedShort(file));
            room.setX376(Utils.readUnsignedShort(file));
            room.setX378(Utils.readUnsignedShort(file));
            room.setX37a(Utils.readUnsignedShort(file));
            room.setX37c(Utils.readUnsignedShort(file));
            room.setX37e(Utils.readUnsignedShort(file));
            room.setTorchRadius(Utils.readUnsignedShort(file) / FIXED_POINT_DIVISION);
            int[] effects = new int[8];
            for (int x = 0; x < effects.length; x++) {
                effects[x] = Utils.readUnsignedShort(file);
            }
            room.setEffects(effects);
            room.setRoomId((short) file.readUnsignedByte());
            room.setUnknown7((short) file.readUnsignedByte());
            room.setTerrainId((short) file.readUnsignedByte());
            room.setTileConstruction((short) file.readUnsignedByte());
            room.setUnknown8((short) file.readUnsignedByte());
            room.setTorchColor(new Color(file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte()));
            short[] objects = new short[8];
            for (int x = 0; x < objects.length; x++) {
                objects[x] = (short) file.readUnsignedByte();
            }
            room.setObjects(objects);
            bytes = new byte[32];
            file.read(bytes);
            room.setSoundCategory(Utils.bytesToString(bytes).trim());
            room.setX3c2((short) file.readUnsignedByte());
            room.setX3c3((short) file.readUnsignedByte());
            room.setUnknown10(Utils.readUnsignedShort(file));
            room.setUnknown11((short) file.readUnsignedByte());
            room.setTorch(readArtResource(file));
            room.setX41b((short) file.readUnsignedByte());
            room.setX41c((short) file.readUnsignedByte());
            room.setX41d(Utils.readShort(file));

            // Add to the hash by the room ID
            rooms.put(room.getRoomId(), room);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the *.kwd
     *
     * @param file the original map KWD file
     * @throws RuntimeException reading may fail
     */
    private void readMapInfo(File file) throws RuntimeException {

        // Read the file
        try (RandomAccessFile rawMapInfo = new RandomAccessFile(file, "r")) {

            rawMapInfo.seek(20); // End of header

            //Additional header data
            int pathCount = Utils.readUnsignedShort(rawMapInfo);
            int unknownCount = Utils.readUnsignedShort(rawMapInfo);
            rawMapInfo.skipBytes(4);

            //Gather the timestamps
            timestamp1 = readTimestamp(rawMapInfo);
            timestamp2 = readTimestamp(rawMapInfo);
            rawMapInfo.skipBytes(8);

            //Property data
            byte[] bytes = new byte[64 * 2];
            rawMapInfo.read(bytes);
            name = Utils.bytesToStringUtf16(bytes).trim();

            bytes = new byte[1024 * 2];
            rawMapInfo.read(bytes);
            description = Utils.bytesToStringUtf16(bytes).trim();

            bytes = new byte[64 * 2];
            rawMapInfo.read(bytes);
            author = Utils.bytesToStringUtf16(bytes).trim();

            bytes = new byte[64 * 2];
            rawMapInfo.read(bytes);
            email = Utils.bytesToStringUtf16(bytes).trim();

            bytes = new byte[1024 * 2];
            rawMapInfo.read(bytes);
            information = Utils.bytesToStringUtf16(bytes).trim();

            mWShortId0 = Utils.readUnsignedShort(rawMapInfo);
            ticksPerSec = Utils.readUnsignedShort(rawMapInfo);
            x01184 = new short[520];
            for (int x = 0; x < x01184.length; x++) {
                x01184[x] = (short) rawMapInfo.readUnsignedByte();
            }
            messages = new String[512];
            for (int x = 0; x < messages.length; x++) {
                bytes = new byte[20 * 2];
                rawMapInfo.read(bytes);
                messages[x] = Utils.bytesToStringUtf16(bytes).trim();
            }
            int flag = Utils.readUnsignedShort(rawMapInfo);
            lvflags = parseFlagValue(flag, LevFlag.class);
            bytes = new byte[32];
            rawMapInfo.read(bytes);
            speechStr = Utils.bytesToString(bytes).trim();
            talismanPieces = (short) rawMapInfo.readUnsignedByte();
            rewardPrev = new short[4];
            for (int x = 0; x < rewardPrev.length; x++) {
                rewardPrev[x] = (short) rawMapInfo.readUnsignedByte();
            }
            rewardNext = new short[4];
            for (int x = 0; x < rewardNext.length; x++) {
                rewardNext[x] = (short) rawMapInfo.readUnsignedByte();
            }
            soundTrack = (short) rawMapInfo.readUnsignedByte();
            textTableId = (short) rawMapInfo.readUnsignedByte();
            textTitleId = Utils.readUnsignedShort(rawMapInfo);
            textPlotId = Utils.readUnsignedShort(rawMapInfo);
            textDebriefId = Utils.readUnsignedShort(rawMapInfo);
            textObjectvId = Utils.readUnsignedShort(rawMapInfo);
            x063c3 = Utils.readUnsignedShort(rawMapInfo);
            textSubobjctvId1 = Utils.readUnsignedShort(rawMapInfo);
            textSubobjctvId2 = Utils.readUnsignedShort(rawMapInfo);
            textSubobjctvId3 = Utils.readUnsignedShort(rawMapInfo);
            speclvlIdx = Utils.readUnsignedShort(rawMapInfo);
            textIntrdcOverrdObj = new short[8];
            for (int x = 0; x < textIntrdcOverrdObj.length; x++) {
                textIntrdcOverrdObj[x] = (short) rawMapInfo.readUnsignedByte();
            }
            textIntrdcOverrdId = new int[8];
            for (int x = 0; x < textIntrdcOverrdId.length; x++) {
                textIntrdcOverrdId[x] = Utils.readUnsignedShort(rawMapInfo);
            }
            bytes = new byte[32];
            rawMapInfo.read(bytes);
            terrainPath = Utils.bytesToString(bytes).trim();
            oneShotHornyLev = (short) rawMapInfo.readUnsignedByte();
            x06404 = (short) rawMapInfo.readUnsignedByte();
            x06405 = (short) rawMapInfo.readUnsignedByte();
            x06406 = (short) rawMapInfo.readUnsignedByte();
            speechHornyId = Utils.readUnsignedShort(rawMapInfo);
            speechPrelvlId = Utils.readUnsignedShort(rawMapInfo);
            speechPostlvlWin = Utils.readUnsignedShort(rawMapInfo);
            speechPostlvlLost = Utils.readUnsignedShort(rawMapInfo);
            speechPostlvlNews = Utils.readUnsignedShort(rawMapInfo);
            speechPrelvlGenr = Utils.readUnsignedShort(rawMapInfo);
            bytes = new byte[32 * 2];
            rawMapInfo.read(bytes);
            heroName = Utils.bytesToStringUtf16(bytes).trim();

            // Paths and the unknown array
            rawMapInfo.skipBytes(8);
            paths = new FilePath[pathCount];
            for (int x = 0; x < paths.length; x++) {
                FilePath filePath = new FilePath();
                filePath.setId(parseEnum(Utils.readUnsignedInteger(rawMapInfo), MapDataTypeEnum.class));
                filePath.setUnknown2(Utils.readInteger(rawMapInfo));
                bytes = new byte[64];
                rawMapInfo.read(bytes);
                filePath.setPath(Utils.bytesToString(bytes).trim());
                paths[x] = filePath;
            }
            unknown = new int[unknownCount];
            for (int x = 0; x < unknown.length; x++) {
                unknown[x] = Utils.readUnsignedShort(rawMapInfo);
            }
        } catch (IOException e) {

            //Fug
            throw new RuntimeException("Failed to read the file " + file + "!", e);
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getEmail() {
        return email;
    }

    public String getInformation() {
        return information;
    }

    /**
     * Reads the Creatures.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readCreatures(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the creatures catalog
        logger.info("Reading creatures!");
        creatures = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Creature creature = new Creature();
            byte[] bytes = new byte[32];
            file.read(bytes);
            creature.setName(Utils.bytesToString(bytes).trim());
            ArtResource[] ref1 = new ArtResource[39];
            for (int x = 0; x < ref1.length; x++) {
                ref1[x] = readArtResource(file);
            }
            creature.setRef1(ref1);
            creature.setUnkcec(Utils.readUnsignedShort(file));
            creature.setUnkcee(Utils.readUnsignedInteger(file));
            creature.setUnkcf2(Utils.readUnsignedInteger(file));
            creature.setOrderInEditor((short) file.readUnsignedByte());
            creature.setAngerStringIdGeneral(Utils.readUnsignedShort(file));
            creature.setShotDelay(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            creature.setOlhiEffectId(Utils.readUnsignedShort(file));
            creature.setIntroductionStringId(Utils.readUnsignedShort(file));
            creature.setUnkd01(Utils.readUnsignedInteger(file));
            creature.setAngerStringIdLair(Utils.readUnsignedShort(file));
            creature.setAngerStringIdFood(Utils.readUnsignedShort(file));
            creature.setAngerStringIdPay(Utils.readUnsignedShort(file));
            creature.setAngerStringIdWork(Utils.readUnsignedShort(file));
            creature.setAngerStringIdSlap(Utils.readUnsignedShort(file));
            creature.setAngerStringIdHeld(Utils.readUnsignedShort(file));
            creature.setAngerStringIdLonely(Utils.readUnsignedShort(file));
            creature.setAngerStringIdHatred(Utils.readUnsignedShort(file));
            creature.setAngerStringIdTorture(Utils.readUnsignedShort(file));
            bytes = new byte[32];
            file.read(bytes);
            creature.setTranslationSoundGategory(Utils.bytesToString(bytes).trim());
            creature.setShuffleSpeed(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            creature.setCreatureId((short) file.readUnsignedByte());
            short[] unk2e = new short[2];
            for (int x = 0; x < unk2e.length; x++) {
                unk2e[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk2e(unk2e);
            creature.setIntroCameraPathIndex((short) file.readUnsignedByte());
            creature.setUnk2e2((short) file.readUnsignedByte());
            creature.setRef2(readArtResource(file));
            creature.setLight(readLight(file));
            Attraction[] attractions = new Attraction[2];
            for (int x = 0; x < attractions.length; x++) {
                Attraction attraction = creature.new Attraction();
                attraction.setPresent(Utils.readUnsignedInteger(file));
                attraction.setRoomId(Utils.readUnsignedShort(file));
                attraction.setRoomSize(Utils.readUnsignedShort(file));
                attractions[x] = attraction;
            }
            creature.setAttractions(attractions);
            creature.setUnkdbc(Utils.readUnsignedInteger(file));
            creature.setUnkdc0(Utils.readUnsignedInteger(file));
            Spell[] spells = new Spell[3];
            for (int x = 0; x < spells.length; x++) {
                Spell spell = creature.new Spell();
                spell.setShotOffset(new Vector3f(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION, Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION, Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION));
                spell.setX0c((short) file.readUnsignedByte());
                spell.setPlayAnimation((short) file.readUnsignedByte() == 1 ? true : false);
                spell.setX0e((short) file.readUnsignedByte());
                spell.setX0f((short) file.readUnsignedByte());
                spell.setShotDelay(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
                spell.setX14((short) file.readUnsignedByte());
                spell.setX15((short) file.readUnsignedByte());
                spell.setCreatureSpellId((short) file.readUnsignedByte());
                spell.setLevelAvailable((short) file.readUnsignedByte());
                spells[x] = spell;
            }
            creature.setSpells(spells);
            Creature.Resistance[] resistances = new Creature.Resistance[4];
            for (int x = 0; x < resistances.length; x++) {
                Creature.Resistance resistance = creature.new Resistance();
                resistance.setAttackType(parseEnum(file.readUnsignedByte(), Creature.AttackType.class));
                resistance.setValue((short) file.readUnsignedByte());
                resistances[x] = resistance;
            }
            creature.setResistances(resistances);
            creature.setHappyJobs(readJobPreferences(3, creature, file));
            creature.setUnhappyJobs(readJobPreferences(2, creature, file));
            creature.setAngryJobs(readJobPreferences(3, creature, file));
            Creature.JobType[] hateJobs = new Creature.JobType[2];
            for (int x = 0; x < hateJobs.length; x++) {
                hateJobs[x] = parseEnum(Utils.readUnsignedInteger(file), Creature.JobType.class);
            }
            creature.setHateJobs(hateJobs);
            Xe7c[] xe7cs = new Xe7c[3];
            for (int x = 0; x < xe7cs.length; x++) {
                Xe7c xe7c = creature.new Xe7c();
                xe7c.setX00(Utils.readUnsignedInteger(file));
                xe7c.setX04(Utils.readUnsignedShort(file));
                xe7c.setX06(Utils.readUnsignedShort(file));
                xe7cs[x] = xe7c;
            }
            creature.setXe7c(xe7cs);
            Xe94 xe94 = creature.new Xe94();
            xe94.setX00(Utils.readUnsignedInteger(file));
            xe94.setX04(Utils.readUnsignedInteger(file));
            xe94.setX08(Utils.readUnsignedInteger(file));
            creature.setXe94(xe94);
            creature.setUnkea0(Utils.readInteger(file));
            creature.setHeight(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            creature.setUnkea8(Utils.readUnsignedInteger(file));
            creature.setUnk3ab(Utils.readUnsignedInteger(file));
            creature.setEyeHeight(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            creature.setSpeed(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            creature.setRunSpeed(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            creature.setUnk3ac(Utils.readUnsignedInteger(file));
            creature.setTimeAwake(Utils.readUnsignedInteger(file));
            creature.setTimeSleep(Utils.readUnsignedInteger(file));
            creature.setUnkec8(Utils.readUnsignedInteger(file));
            creature.setUnkecc(Utils.readUnsignedInteger(file));
            creature.setUnked0(Utils.readUnsignedInteger(file));
            creature.setUnked4(Utils.readUnsignedInteger(file));
            creature.setUnked8(Utils.readUnsignedInteger(file));
            creature.setSlapFearlessDuration(Utils.readInteger(file));
            creature.setUnkee0(Utils.readInteger(file));
            creature.setUnkee4(Utils.readInteger(file));
            creature.setPossessionManaCost(Utils.readShort(file));
            creature.setOwnLandHealthIncrease(Utils.readShort(file));
            creature.setRange(Utils.readInteger(file));
            creature.setUnkef0(Utils.readUnsignedInteger(file));
            creature.setUnk3af(Utils.readUnsignedInteger(file));
            creature.setMeleeRecharge(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            // The flags is actually very big, pushing the boundaries, a true uint32, need to -> long
            creature.setFlags(parseFlagValue(Utils.readUnsignedIntegerAsLong(file), Creature.CreatureFlag.class));
            creature.setExpForNextLevel(Utils.readUnsignedShort(file));
            creature.setJobClass(parseEnum(file.readUnsignedByte(), Creature.JobClass.class));
            creature.setFightStyle(parseEnum(file.readUnsignedByte(), Creature.FightStyle.class));
            creature.setExpPerSecond(Utils.readUnsignedShort(file));
            creature.setExpPerSecondTraining(Utils.readUnsignedShort(file));
            creature.setResearchPerSecond(Utils.readUnsignedShort(file));
            creature.setManufacturePerSecond(Utils.readUnsignedShort(file));
            creature.setHp(Utils.readUnsignedShort(file));
            creature.setHpFromChicken(Utils.readUnsignedShort(file));
            creature.setFear(Utils.readUnsignedShort(file));
            creature.setThreat(Utils.readUnsignedShort(file));
            creature.setMeleeDamage(Utils.readUnsignedShort(file));
            creature.setSlapDamage(Utils.readUnsignedShort(file));
            creature.setManaGenPrayer(Utils.readUnsignedShort(file));
            creature.setUnk3cb(Utils.readUnsignedShort(file));
            creature.setPay(Utils.readUnsignedShort(file));
            creature.setMaxGoldHeld(Utils.readUnsignedShort(file));
            creature.setUnk3cc(Utils.readUnsignedShort(file));
            creature.setDecomposeValue(Utils.readUnsignedShort(file));
            creature.setNameStringId(Utils.readUnsignedShort(file));
            creature.setTooltipStringId(Utils.readUnsignedShort(file));
            creature.setAngerNoLair(Utils.readShort(file));
            creature.setAngerNoFood(Utils.readShort(file));
            creature.setAngerNoPay(Utils.readShort(file));
            creature.setAngerNoWork(Utils.readShort(file));
            creature.setAngerSlap(Utils.readShort(file));
            creature.setAngerInHand(Utils.readShort(file));
            creature.setInitialGoldHeld(Utils.readShort(file));
            creature.setEntranceEffectId(Utils.readUnsignedShort(file));
            creature.setGeneralDescriptionStringId(Utils.readUnsignedShort(file));
            creature.setStrengthStringId(Utils.readUnsignedShort(file));
            creature.setWeaknessStringId(Utils.readUnsignedShort(file));
            creature.setSlapEffectId(Utils.readUnsignedShort(file));
            creature.setDeathEffectId(Utils.readUnsignedShort(file));
            short[] unk3d = new short[3];
            for (int x = 0; x < unk3d.length; x++) {
                unk3d[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk3d(unk3d);
            creature.setUnkf45((short) file.readUnsignedByte());
            short[] unk40 = new short[2];
            for (int x = 0; x < unk40.length; x++) {
                unk40[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk40(unk40);
            short[] unkf48 = new short[3];
            for (int x = 0; x < unkf48.length; x++) {
                unkf48[x] = (short) file.readUnsignedByte();
            }
            creature.setUnkf48(unkf48);
            creature.setCreatureId((short) file.readUnsignedByte());
            short[] unk3ea = new short[3];
            for (int x = 0; x < unk3ea.length; x++) {
                unk3ea[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk3ea(unk3ea);
            creature.setUnhappyThreshold((short) file.readUnsignedByte());
            short[] unk3eb = new short[2];
            for (int x = 0; x < unk3eb.length; x++) {
                unk3eb[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk3eb(unk3eb);
            creature.setLairObjectId((short) file.readUnsignedByte());
            creature.setUnk3f1((short) file.readUnsignedByte());
            creature.setDeathFallDirection(parseEnum(file.readUnsignedByte(), Creature.DeathFallDirection.class));
            creature.setUnk3f2((short) file.readUnsignedByte());
            bytes = new byte[32];
            file.read(bytes);
            creature.setSoundGategory(Utils.bytesToString(bytes).trim());
            creature.setMaterial(parseEnum(file.readUnsignedByte(), Material.class));
            creature.setReff77(readArtResource(file));
            creature.setUnkfcb(Utils.readUnsignedShort(file));
            creature.setUnk4(Utils.readUnsignedInteger(file));
            creature.setRef3(readArtResource(file));
            short[] unk5 = new short[2];
            for (int x = 0; x < unk5.length; x++) {
                unk5[x] = (short) file.readUnsignedByte();
            }
            creature.setUnk5(unk5);
            creature.setRef4(readArtResource(file));
            creature.setUnk6(Utils.readUnsignedInteger(file));
            creature.setTortureHpChange(Utils.readShort(file));
            creature.setTortureMoodChange(Utils.readShort(file));
            ArtResource[] ref5 = new ArtResource[6];
            for (int x = 0; x < ref5.length; x++) {
                ref5[x] = readArtResource(file);
            }
            creature.setRef5(ref5);
            Unk7[] unk7s = new Unk7[7];
            for (int x = 0; x < unk7s.length; x++) {
                Unk7 unk7 = creature.new Unk7();
                unk7.setX00(Utils.readUnsignedInteger(file));
                unk7.setX04(Utils.readUnsignedInteger(file));
                unk7.setX08(Utils.readUnsignedInteger(file));
                unk7s[x] = unk7;
            }
            creature.setUnk7(unk7s);
            creature.setRef6(readArtResource(file));
            X1323[] x1323s = new X1323[48];
            for (int x = 0; x < x1323s.length; x++) {
                X1323 x1323 = creature.new X1323();
                x1323.setX00(Utils.readUnsignedShort(file));
                x1323.setX02(Utils.readUnsignedShort(file));
                x1323s[x] = x1323;
            }
            creature.setX1323(x1323s);
            ArtResource[] ref7 = new ArtResource[3];
            for (int x = 0; x < ref7.length; x++) {
                ref7[x] = readArtResource(file);
            }
            creature.setRef7(ref7);
            creature.setUniqueNameTextId(Utils.readUnsignedShort(file));
            int[] x14e1 = new int[2];
            for (int x = 0; x < x14e1.length; x++) {
                x14e1[x] = Utils.readUnsignedInteger(file);
            }
            creature.setX14e1(x14e1);
            int[] x14e9 = new int[2];
            for (int x = 0; x < x14e9.length; x++) {
                x14e9[x] = Utils.readUnsignedInteger(file);
            }
            creature.setX14e9(x14e9);
            creature.setRef8(readArtResource(file));
            creature.setUnk1545(Utils.readUnsignedInteger(file));

            // Add to the hash by the creature ID
            creatures.put(creature.getCreatureId(), creature);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Read job preferences for a creature
     *
     * @param count amount of job preference records
     * @param creature creature instance, just for creating a job preference
     * instance
     * @param file the file to read the data from
     * @return job preferences
     * @throws IOException may fail
     */
    private Creature.JobPreference[] readJobPreferences(int count, Creature creature, RandomAccessFile file) throws IOException {
        Creature.JobPreference[] preferences = new Creature.JobPreference[count];
        for (int x = 0; x < preferences.length; x++) {
            Creature.JobPreference jobPreference = creature.new JobPreference();
            jobPreference.setJobType(parseEnum(Utils.readUnsignedInteger(file), Creature.JobType.class));
            jobPreference.setMoodChange(Utils.readUnsignedShort(file));
            jobPreference.setManaChange(Utils.readUnsignedShort(file));
            jobPreference.setChance((short) file.readUnsignedByte());
            jobPreference.setX09((short) file.readUnsignedByte());
            jobPreference.setX0a((short) file.readUnsignedByte());
            jobPreference.setX0b((short) file.readUnsignedByte());
            preferences[x] = jobPreference;
        }
        return preferences;
    }

    /**
     * Reads and parses an Light object from the current file location
     *
     * @param file the file stream to parse from
     * @return a Light
     */
    private Light readLight(RandomAccessFile file) throws IOException {
        Light light = new Light();

        // Read the data
        light.setmKPos(new Vector3f(Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION));
        light.setRadius(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);

        //NOTE: interestingly enough, here a uint8 sized flag is enough I think, and the editor seems to read 0-511 (9 bits, or probably 10 bits but the sign bit is always positive) for each color element
        //      But I also think it might be a mistake in the editor/file format
        //      Some lights seem to be logical with this structure.... so who knows
        light.setFlags(parseFlagValue(Utils.readUnsignedInteger(file), Light.LightFlag.class));
        light.setColor(new Color(file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte()));

        return light;
    }

    /**
     * Reads the Objects.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readObjects(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the objects catalog
        logger.info("Reading objects!");
        objects = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Object object = new Object();
            byte[] bytes = new byte[32];
            file.read(bytes);
            object.setName(Utils.bytesToString(bytes).trim());
            object.setMeshResource(readArtResource(file));
            object.setGuiIconResource(readArtResource(file));
            object.setInHandIconResource(readArtResource(file));
            object.setInHandMeshResource(readArtResource(file));
            object.setkUnknownResource(readArtResource(file));
            ArtResource[] additionalResources = new ArtResource[4];
            for (int x = 0; x < additionalResources.length; x++) {
                additionalResources[x] = readArtResource(file);
            }
            object.setAdditionalResources(additionalResources);
            object.setLight(readLight(file));
            object.setWidth(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            object.setHeight(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            object.setMass(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            object.setUnknown1(Utils.readUnsignedInteger(file));
            object.setUnknown2(Utils.readUnsignedInteger(file));
            object.setMaterial(parseEnum(file.readUnsignedByte(), Material.class));
            short[] unknown3 = new short[3];
            for (int x = 0; x < unknown3.length; x++) {
                unknown3[x] = (short) file.readUnsignedByte();
            }
            object.setUnknown3(unknown3);
            object.setFlags(Utils.readUnsignedInteger(file));
            object.setHp(Utils.readUnsignedShort(file));
            object.setUnknown4(Utils.readUnsignedShort(file));
            object.setX34c(Utils.readUnsignedShort(file));
            object.setX34e(Utils.readUnsignedShort(file));
            object.setX350(Utils.readUnsignedShort(file));
            object.setX352(Utils.readUnsignedShort(file));
            object.setSlapEffect(Utils.readUnsignedShort(file));
            object.setDeathEffect(Utils.readUnsignedShort(file));
            object.setUnknown5(Utils.readUnsignedShort(file));
            object.setObjectId((short) file.readUnsignedByte());
            object.setUnknown6((short) file.readUnsignedByte());
            object.setRoomCapacity((short) file.readUnsignedByte());
            object.setUnknown7((short) file.readUnsignedByte());
            bytes = new byte[32];
            file.read(bytes);
            object.setSoundCategory(Utils.bytesToString(bytes).trim());

            // Add to the hash by the object ID
            objects.put(object.getObjectId(), object);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the CreatureSpells.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readCreatureSpells(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the creature spells catalog
        logger.info("Reading creature spells!");
        creatureSpells = new ArrayList<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            CreatureSpell creatureSpell = new CreatureSpell();
            byte[] bytes = new byte[32];
            file.read(bytes);
            creatureSpell.setName(Utils.bytesToString(bytes).trim());
            short[] data = new short[234];
            for (int x = 0; x < data.length; x++) {
                data[x] = (short) file.readUnsignedByte();
            }
            creatureSpell.setData(data);

            // Add to the list
            creatureSpells.add(creatureSpell);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the EffectElements.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readEffectElements(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the effect elements catalog
        logger.info("Reading effect elements!");
        effectElements = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            EffectElement effectElement = new EffectElement();
            byte[] bytes = new byte[32];
            file.read(bytes);
            effectElement.setName(Utils.bytesToString(bytes).trim());
            effectElement.setArtResource(readArtResource(file));
            effectElement.setMass(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setAirFriction(Utils.readUnsignedInteger(file) / FIXED_POINT5_DIVISION);
            effectElement.setElasticity(Utils.readUnsignedInteger(file) / FIXED_POINT5_DIVISION);
            effectElement.setMinSpeedXy(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setMaxSpeedXy(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setMinSpeedYz(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setMaxSpeedYz(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setMinScale(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setMaxScale(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setScaleRatio(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effectElement.setFlags(Utils.readUnsignedInteger(file));
            effectElement.setEffectElementId(Utils.readUnsignedShort(file));
            effectElement.setMinHp(Utils.readUnsignedShort(file));
            effectElement.setMaxHp(Utils.readUnsignedShort(file));
            effectElement.setDeathElement(Utils.readUnsignedShort(file));
            effectElement.setHitSolidElement(Utils.readUnsignedShort(file));
            effectElement.setHitWaterElement(Utils.readUnsignedShort(file));
            effectElement.setHitLavaElement(Utils.readUnsignedShort(file));
            effectElement.setColor(new Color(file.readUnsignedByte(), file.readUnsignedByte(), file.readUnsignedByte()));
            effectElement.setRandomColorIndex((short) file.readUnsignedByte());
            effectElement.setTableColorIndex((short) file.readUnsignedByte());
            effectElement.setFadePercentage((short) file.readUnsignedByte());
            effectElement.setNextEffect(Utils.readUnsignedShort(file));

            // Add to the hash by the effect element ID
            effectElements.put(effectElement.getEffectElementId(), effectElement);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the Effects.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readEffects(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the effects catalog
        logger.info("Reading effects!");
        effects = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            Effect effect = new Effect();
            byte[] bytes = new byte[32];
            file.read(bytes);
            effect.setName(Utils.bytesToString(bytes).trim());
            effect.setArtResource(readArtResource(file));
            effect.setLight(readLight(file));
            effect.setMass(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effect.setAirFriction(Utils.readUnsignedInteger(file) / FIXED_POINT5_DIVISION);
            effect.setElasticity(Utils.readUnsignedInteger(file) / FIXED_POINT5_DIVISION);
            effect.setRadius(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effect.setMinSpeedXy(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effect.setMaxSpeedXy(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effect.setMinSpeedYz(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effect.setMaxSpeedYz(Utils.readInteger(file) / FIXED_POINT_DIVISION);
            effect.setMinScale(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effect.setMaxScale(Utils.readUnsignedInteger(file) / FIXED_POINT_DIVISION);
            effect.setFlags(Utils.readUnsignedInteger(file));
            effect.setEffectId(Utils.readUnsignedShort(file));
            effect.setMinHp(Utils.readUnsignedShort(file));
            effect.setMaxHp(Utils.readUnsignedShort(file));
            effect.setFadeDuration(Utils.readUnsignedShort(file));
            effect.setNextEffect(Utils.readUnsignedShort(file));
            effect.setDeathEffect(Utils.readUnsignedShort(file));
            effect.setHitSolidEffect(Utils.readUnsignedShort(file));
            effect.setHitWaterEffect(Utils.readUnsignedShort(file));
            effect.setHitLavaEffect(Utils.readUnsignedShort(file));
            int[] generateIds = new int[8];
            for (int x = 0; x < generateIds.length; x++) {
                generateIds[x] = Utils.readUnsignedShort(file);
            }
            effect.setGenerateIds(generateIds);
            effect.setOuterOriginRange(Utils.readUnsignedShort(file));
            effect.setLowerHeightLimit(Utils.readUnsignedShort(file));
            effect.setUpperHeightLimit(Utils.readUnsignedShort(file));
            effect.setOrientationRange(Utils.readUnsignedShort(file));
            effect.setSpriteSpinRateRange(Utils.readUnsignedShort(file));
            effect.setWhirlpoolRate(Utils.readUnsignedShort(file));
            effect.setDirectionalSpread(Utils.readUnsignedShort(file));
            effect.setCircularPathRate(Utils.readUnsignedShort(file));
            effect.setInnerOriginRange(Utils.readUnsignedShort(file));
            effect.setGenerateRandomness(Utils.readUnsignedShort(file));
            effect.setMisc2(Utils.readUnsignedShort(file));
            effect.setMisc3(Utils.readUnsignedShort(file));
            effect.setUnknown1((short) file.readUnsignedByte());
            effect.setElementsPerTurn((short) file.readUnsignedByte());
            effect.setUnknown3(Utils.readUnsignedShort(file));

            // Add to the hash by the effect ID
            effects.put(effect.getEffectId(), effect);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the KeeperSpells.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readKeeperSpells(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the keeper spells catalog
        logger.info("Reading keeper spells!");
        keeperSpells = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();
            KeeperSpell keeperSpell = new KeeperSpell();
            byte[] bytes = new byte[32];
            file.read(bytes);
            keeperSpell.setName(Utils.bytesToString(bytes).trim());
            keeperSpell.setRef1(readArtResource(file));
            keeperSpell.setRef3(readArtResource(file));
            keeperSpell.setXc8(Utils.readInteger(file));
            keeperSpell.setXcc(Utils.readInteger(file));
            keeperSpell.setShotData1(Utils.readInteger(file));
            keeperSpell.setShotData2(Utils.readInteger(file));
            keeperSpell.setXd8(Utils.readUnsignedShort(file));
            keeperSpell.setXda((short) file.readUnsignedByte());
            keeperSpell.setXdb((short) file.readUnsignedByte());
            keeperSpell.setXdc(Utils.readInteger(file));
            keeperSpell.setXe0Unreferenced(Utils.readUnsignedShort(file));
            keeperSpell.setManaDrain(Utils.readUnsignedShort(file));
            keeperSpell.setXe4(Utils.readUnsignedShort(file));
            keeperSpell.setXe6(Utils.readUnsignedShort(file));
            keeperSpell.setXe8(Utils.readUnsignedShort(file));
            keeperSpell.setXea(Utils.readUnsignedShort(file));
            keeperSpell.setXec(Utils.readUnsignedShort(file));
            keeperSpell.setKeeperSpellId((short) file.readUnsignedByte());
            keeperSpell.setXef((short) file.readUnsignedByte());
            keeperSpell.setXf0((short) file.readUnsignedByte());
            bytes = new byte[32];
            file.read(bytes);
            keeperSpell.setYName(Utils.bytesToString(bytes).trim());
            keeperSpell.setBonusRTime(Utils.readUnsignedShort(file));
            keeperSpell.setBonusShotType((short) file.readUnsignedByte());
            keeperSpell.setBonusShotData1(Utils.readInteger(file));
            keeperSpell.setBonusShotData2(Utils.readInteger(file));
            keeperSpell.setManaCost(Utils.readInteger(file));
            keeperSpell.setRef2(readArtResource(file));
            bytes = new byte[32];
            file.read(bytes);
            keeperSpell.setXName(Utils.bytesToString(bytes).trim());
            keeperSpell.setX194((short) file.readUnsignedByte());
            keeperSpell.setX195((short) file.readUnsignedByte());

            // Add to the hash by the keeper spell ID
            keeperSpells.put(keeperSpell.getKeeperSpellId(), keeperSpell);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the *Things.kld
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readThings(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the requested Things file
        logger.info("Reading things!");
        things = new ArrayList<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            Thing thing = new Thing() {
            };
            int[] thingTag = new int[2];
            for (int x = 0; x < thingTag.length; x++) {
                thingTag[x] = Utils.readUnsignedInteger(file);
            }

            // Figure out the type
            switch (thingTag[0]) {
                case 194: {

                    // Thing06
                    file.skipBytes(thingTag[1]);
                    break;
                }
                case 195: {

                    // Thing05
                    file.skipBytes(thingTag[1]);
                    break;
                }
                case 196: {

                    // Thing04
                    file.skipBytes(thingTag[1]);
                    break;
                }
                case 197: {

                    // ActionPoint
                    thing = thing.new ActionPoint();
                    ((ActionPoint) thing).setX00(Utils.readInteger(file));
                    ((ActionPoint) thing).setX04(Utils.readInteger(file));
                    ((ActionPoint) thing).setX08(Utils.readInteger(file));
                    ((ActionPoint) thing).setX0c(Utils.readInteger(file));
                    ((ActionPoint) thing).setX10(Utils.readInteger(file));
                    ((ActionPoint) thing).setX14(Utils.readUnsignedShort(file));
                    ((ActionPoint) thing).setId((short) file.readUnsignedByte());
                    ((ActionPoint) thing).setX17((short) file.readUnsignedByte());
                    byte[] bytes = new byte[32];
                    file.read(bytes);
                    ((ActionPoint) thing).setName(Utils.bytesToString(bytes).trim());
                    break;
                }
                case 198: {

                    // Thing01
                    file.skipBytes(thingTag[1]);
                    break;
                }
                case 199: {

                    // Thing02
                    file.skipBytes(thingTag[1]);
                    break;
                }
                case 200: {

                    // Thing03
                    thing = thing.new Thing03();
                    ((Thing03) thing).setPos(new Vector3f(Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION));
                    ((Thing03) thing).setX0c(Utils.readUnsignedShort(file));
                    ((Thing03) thing).setX0e((short) file.readUnsignedByte());
                    ((Thing03) thing).setX0f((short) file.readUnsignedByte());
                    ((Thing03) thing).setX10(Utils.readInteger(file));
                    ((Thing03) thing).setX14(Utils.readInteger(file));
                    ((Thing03) thing).setX18(Utils.readUnsignedShort(file));
                    ((Thing03) thing).setId((short) file.readUnsignedByte());
                    ((Thing03) thing).setX1b((short) file.readUnsignedByte());
                    break;
                }
                case 201: {

                    // Thing08 -- not tested
                    thing = thing.new Thing08();
                    byte[] bytes = new byte[32];
                    file.read(bytes);
                    ((Thing08) thing).setName(Utils.bytesToString(bytes).trim());
                    ((Thing08) thing).setX20(Utils.readUnsignedShort(file));
                    ((Thing08) thing).setX22((short) file.readUnsignedByte());
                    ((Thing08) thing).setX23(Utils.readInteger(file));
                    ((Thing08) thing).setX27(Utils.readInteger(file));
                    HeroPartyData[] x2b = new HeroPartyData[16];
                    for (int x = 0; x < x2b.length; x++) {
                        HeroPartyData heroPartyData = ((Thing08) thing).new HeroPartyData();
                        heroPartyData.setX00(Utils.readInteger(file));
                        heroPartyData.setX04(Utils.readInteger(file));
                        heroPartyData.setX08(Utils.readInteger(file));
                        heroPartyData.setGoldHeld(Utils.readUnsignedShort(file));
                        heroPartyData.setX0e((short) file.readUnsignedByte());
                        heroPartyData.setX0f((short) file.readUnsignedByte());
                        heroPartyData.setX10(Utils.readInteger(file));
                        heroPartyData.setInitialHealth(Utils.readInteger(file));
                        heroPartyData.setX18(Utils.readUnsignedShort(file));
                        heroPartyData.setX1a((short) file.readUnsignedByte());
                        heroPartyData.setX1b((short) file.readUnsignedByte());
                        heroPartyData.setX1c((short) file.readUnsignedByte());
                        heroPartyData.setX1d((short) file.readUnsignedByte());
                        heroPartyData.setX1e((short) file.readUnsignedByte());
                        heroPartyData.setX1f((short) file.readUnsignedByte());
                        x2b[x] = heroPartyData;
                    }
                    ((Thing08) thing).setX2b(x2b);
                    break;
                }
                case 203: {

                    // Thing10 -- not tested
                    thing = thing.new Thing10();
                    ((Thing10) thing).setX00(Utils.readInteger(file));
                    ((Thing10) thing).setX04(Utils.readInteger(file));
                    ((Thing10) thing).setX08(Utils.readInteger(file));
                    ((Thing10) thing).setX0c(Utils.readInteger(file));
                    ((Thing10) thing).setX10(Utils.readUnsignedShort(file));
                    ((Thing10) thing).setX12(Utils.readUnsignedShort(file));
                    int[] x14 = new int[4];
                    for (int x = 0; x < x14.length; x++) {
                        x14[x] = Utils.readUnsignedShort(file);
                    }
                    ((Thing10) thing).setX14(x14);
                    ((Thing10) thing).setX1c((short) file.readUnsignedByte());
                    ((Thing10) thing).setX1d((short) file.readUnsignedByte());
                    short[] pad = new short[6];
                    for (int x = 0; x < pad.length; x++) {
                        pad[x] = (short) file.readUnsignedByte();
                    }
                    ((Thing10) thing).setPad(pad);
                    break;
                }
                case 204: {

                    // Thing11
                    thing = thing.new Thing11();
                    ((Thing11) thing).setX00(Utils.readInteger(file));
                    ((Thing11) thing).setX04(Utils.readInteger(file));
                    ((Thing11) thing).setX08(Utils.readInteger(file));
                    ((Thing11) thing).setX0c(Utils.readUnsignedShort(file));
                    ((Thing11) thing).setX0e((short) file.readUnsignedByte());
                    ((Thing11) thing).setX0f((short) file.readUnsignedByte());
                    ((Thing11) thing).setX10(Utils.readUnsignedShort(file));
                    ((Thing11) thing).setX12((short) file.readUnsignedByte());
                    ((Thing11) thing).setX13((short) file.readUnsignedByte());
                    break;
                }
                case 205: {

                    // Thing12 -- not tested
                    thing = thing.new Thing12();
                    ((Thing12) thing).setX00(new Vector3f(Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION));
                    ((Thing12) thing).setX0c(new Vector3f(Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION));
                    ((Thing12) thing).setX18(new Vector3f(Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION, Utils.readInteger(file) / FIXED_POINT_DIVISION));
                    ((Thing12) thing).setX24(Utils.readInteger(file));
                    ((Thing12) thing).setX28(Utils.readInteger(file));
                    ((Thing12) thing).setX2c(Utils.readInteger(file));
                    ((Thing12) thing).setX30(Utils.readInteger(file));
                    ((Thing12) thing).setX34(Utils.readInteger(file));
                    ((Thing12) thing).setX38(Utils.readInteger(file));
                    ((Thing12) thing).setX3c(Utils.readInteger(file));
                    ((Thing12) thing).setX40(Utils.readInteger(file));
                    ((Thing12) thing).setX44(Utils.readInteger(file));
                    ((Thing12) thing).setX48(Utils.readInteger(file));
                    ((Thing12) thing).setX4c(Utils.readUnsignedShort(file));
                    ((Thing12) thing).setX4e(Utils.readUnsignedShort(file));
                    ((Thing12) thing).setX50(Utils.readUnsignedShort(file));
                    ((Thing12) thing).setX52((short) file.readUnsignedByte());
                    break;
                }
                default: {

                    // Just skip the bytes
                    file.skipBytes(thingTag[1]);
                }
            }

            System.out.println(thingTag[0] + " type");

            // Add to the list
            things.add(thing);
        }
    }

    /**
     * Reads the Shots.kwd
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readShots(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the shots catalog
        logger.info("Reading shots!");
        shots = new HashMap<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            long offset = file.getFilePointer();

            // One shot is 239 bytes
            Shot shot = new Shot();
            byte[] bytes = new byte[32];
            file.read(bytes);
            shot.setName(Utils.bytesToString(bytes).trim());

            // The ID is probably a uint8 @ 190
            file.skipBytes(158);
            shot.setShotId((short) file.readUnsignedByte());
            file.skipBytes(48);

            // Add to the hash by the shot ID
            shots.put(shot.getShotId(), shot);

            // Check file offset
            checkOffset(header, file, offset);
        }
    }

    /**
     * Reads the *Triggers.kld
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readTriggers(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the requested Triggers file
        logger.info("Reading triggers!");
        triggers = new ArrayList<>(header.getItemCount());
        for (int i = 0; i < header.getItemCount(); i++) {
            Trigger trigger = new Trigger() {
            };
            int[] triggerTag = new int[2];
            for (int x = 0; x < triggerTag.length; x++) {
                triggerTag[x] = Utils.readUnsignedInteger(file);
            }

            // Figure out the type
            switch (triggerTag[0]) {
                case 213: {

                    // TriggerGeneric
                    trigger = trigger.new TriggerGeneric();
                    ((TriggerGeneric) trigger).setX00(Utils.readInteger(file));
                    ((TriggerGeneric) trigger).setX04(Utils.readInteger(file));
                    ((TriggerGeneric) trigger).setX08(Utils.readUnsignedShort(file));
                    ((TriggerGeneric) trigger).setX0a(Utils.readUnsignedShort(file));
                    ((TriggerGeneric) trigger).setX0c(Utils.readUnsignedShort(file));
                    ((TriggerGeneric) trigger).setX0e((short) file.readUnsignedByte());
                    ((TriggerGeneric) trigger).setX0f((short) file.readUnsignedByte());
                    break;
                }
                case 214: {

                    // TriggerAction
                    trigger = trigger.new TriggerAction();
                    ((TriggerAction) trigger).setX00(Utils.readInteger(file));
                    ((TriggerAction) trigger).setX04(Utils.readInteger(file));
                    ((TriggerAction) trigger).setX08(Utils.readUnsignedShort(file));
                    ((TriggerAction) trigger).setX0a(Utils.readUnsignedShort(file));
                    ((TriggerAction) trigger).setX0c(Utils.readUnsignedShort(file));
                    ((TriggerAction) trigger).setX0e((short) file.readUnsignedByte());
                    file.skipBytes(1); // ????
                    break;
                }
                default: {

                    // Just skip the bytes
                    file.skipBytes(triggerTag[1]);
                    System.out.println(triggerTag[0] + " type");
                }
            }

            // Add to the list
            triggers.add(trigger);
        }
    }

    /**
     * Reads the *Variables.kld
     *
     * @param header Kwd header data
     * @param file the file data, rewind to data position
     * @throws IOException the reading may fail
     */
    private void readVariables(KwdHeader header, RandomAccessFile file) throws IOException {

        // Read the requested VARIABLES file
        // Should be the GlobalVariables first, then the level's own
        // TODO: Overriding
        logger.info("Reading variables!");
        if (variables == null) {
            variables = new ArrayList<>(header.getItemCount());
        }
        for (int i = 0; i < header.getItemCount(); i++) {
            Variable variable = new Variable();
            variable.setX00(Utils.readInteger(file));
            variable.setX04(Utils.readInteger(file));
            variable.setX08(Utils.readInteger(file));
            variable.setX0c(Utils.readInteger(file));

            // Add to the list
            variables.add(variable);
        }
    }

    /**
     * Get list of different terrain tiles
     *
     * @return list of terrain tiles
     */
    public Collection<Terrain> getTerrainList() {
        return terrainTiles.values();
    }

    /**
     * Reads a DK2 style timestamp
     *
     * @param file the file to read from
     * @return the date in current locale
     * @throws IOException may fail
     */
    private Date readTimestamp(RandomAccessFile file) throws IOException {

        // Dates are in UTC
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.YEAR, Utils.readUnsignedShort(file));
        cal.set(Calendar.DAY_OF_MONTH, file.readUnsignedByte());
        cal.set(Calendar.MONTH, file.readUnsignedByte());
        file.skipBytes(2);
        cal.set(Calendar.HOUR_OF_DAY, file.readUnsignedByte());
        cal.set(Calendar.MINUTE, file.readUnsignedByte());
        cal.set(Calendar.SECOND, file.readUnsignedByte());
        file.skipBytes(1);
        return cal.getTime();
    }

    /**
     * Parse a flag to enumeration set of given class
     *
     * @param flag the flag value
     * @param enumeration the enumeration class<br>It is super important that it
     * implements the IFlagEnum (I couldn't figure out how to correctly set
     * generics here)
     * @return the set
     */
    private EnumSet parseFlagValue(long flag, Class<? extends Enum> enumeration) {
        EnumSet set = EnumSet.noneOf(enumeration);
        for (Enum e : enumeration.getEnumConstants()) {
            long flagValue = ((IFlagEnum) e).getFlagValue();
            if ((flagValue & flag) == flagValue) {
                set.add(e);
            }
        }
        return set;
    }

    /**
     * Parses a value to a enum of a wanted enum class
     *
     * @param <E> The enumeration class
     * @param value the id value
     * @param enumeration the enumeration class
     * @return Enum value, returns null if no enum is found with given value
     */
    private <E extends Enum & IValueEnum> E parseEnum(int value, Class<E> enumeration) {
        for (E e : enumeration.getEnumConstants()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        return null;
    }

    /**
     * Not all the data types are of the length that suits us, do our best to
     * ignore it<br>
     * Skips the file to the correct position after an item is read
     *
     * @param header the header
     * @param file the file
     * @param offset the file offset before the last item was read
     */
    private void checkOffset(KwdHeader header, RandomAccessFile file, long offset) throws IOException {
        long wantedOffset = offset + header.getItemSize();
        if (file.getFilePointer() != wantedOffset) {
            file.seek(wantedOffset);
        }
    }

    /**
     * Kwd header, few different kinds, handles all
     */
    private class KwdHeader {

//            struct kwdHeader {
//                unsigned int id;
//                unsigned int size;
//                union {
//                struct {
//                uint16_t w08;
//                uint16_t w0a;
//                } level;
//                unsigned int dw08;
//                };
//                unsigned int x0c[7];
//                };
        private MapDataTypeEnum id;
        private long size;
        private int headerSize = 56; // Well, header and the id data
        private int width;
        private int height;
        private int itemCount;

        public KwdHeader() {
        }

        public MapDataTypeEnum getId() {
            return id;
        }

        protected void setId(MapDataTypeEnum id) {
            this.id = id;
        }

        public long getSize() {
            return size;
        }

        protected void setSize(long size) {
            this.size = size;
        }

        public int getHeaderSize() {
            return headerSize;
        }

        protected void setHeaderSize(int headerSize) {
            this.headerSize = headerSize;
        }

        public int getWidth() {
            return width;
        }

        protected void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        protected void setHeight(int height) {
            this.height = height;
        }

        public int getItemCount() {
            return itemCount;
        }

        protected void setItemCount(int itemCount) {
            this.itemCount = itemCount;
        }

        /**
         * Get the individiual item size (warning, does not apply to all!)
         *
         * @return
         */
        public long getItemSize() {
            return (getSize() - getHeaderSize()) / getItemCount();
        }
    }
}
