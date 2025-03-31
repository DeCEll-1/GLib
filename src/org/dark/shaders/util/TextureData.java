package org.dark.shaders.util;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.dark.shaders.ShaderModPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A class for reading csv data to the program so that textures such as material maps and normal maps can be handled.
 * <p>
 * @author DarkRevenant
 * <p>
 * @since Alpha 1.5
 */
@SuppressWarnings("UseSpecificCatch")
public class TextureData {

    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";

    private static boolean loadMaterial = false;
    private static boolean loadNormal = false;
    private static boolean loadSurface = false;
    private static final Map<String, TextureEntry> materialData = new HashMap<>(1000);
    private static final Map<String, TextureEntry> normalData = new HashMap<>(1000);
    private static final Map<String, TextureEntry> surfaceData = new HashMap<>(1000);

    static {
        Global.getLogger(TextureData.class).setLevel(Level.ERROR);

        try {
            loadSettings();
        } catch (Exception e) {
            Global.getLogger(TextureData.class).log(Level.ERROR, "Failed to load shader settings: " + e.getMessage());
        }
    }

    /**
     * Gets a copy of the requested data. Returns null if no such TextureData entry exists.
     * <p>
     * @param key The ID of the desired material map or normal map texture.
     * @param map Whether the desired texture is a material map or normal map.
     * @param type What kind of object the texture is intended for.
     * @param frame The frame of the animation, for an animated weapon. Use 0 for non-animated weapons and ships.
     * <p>
     * @return The requested data. Null if no such TextureData entry exists.
     * <p>
     * @since Alpha 1.5
     */
    public static TextureEntry getTextureData(String key, TextureDataType map, ObjectType type, int frame) {
        if (key == null) {
            return null;
        }

        final String typeStr;
        switch (type) {
            case SHIP ->
                typeStr = "$$$ship";
            case TURRET ->
                typeStr = "$$$turret";
            case TURRET_BARREL ->
                typeStr = "$$$turretbarrel";
            case TURRET_UNDER ->
                typeStr = "$$$turretunder";
            case TURRET_COVER_SMALL ->
                typeStr = "$$$turretcoversmall";
            case TURRET_COVER_MEDIUM ->
                typeStr = "$$$turretcovermedium";
            case TURRET_COVER_LARGE ->
                typeStr = "$$$turretcoverlarge";
            case HARDPOINT ->
                typeStr = "$$$hardpoint";
            case HARDPOINT_BARREL ->
                typeStr = "$$$hardpointbarrel";
            case HARDPOINT_UNDER ->
                typeStr = "$$$hardpointunder";
            case HARDPOINT_COVER_SMALL ->
                typeStr = "$$$hardpointcoversmall";
            case HARDPOINT_COVER_MEDIUM ->
                typeStr = "$$$hardpointcovermedium";
            case HARDPOINT_COVER_LARGE ->
                typeStr = "$$$hardpointcoverlarge";
            case MISSILE ->
                typeStr = "$$$missile";
            case ASTEROID ->
                typeStr = "$$$asteroid";
            default -> {
                return null;
            }
        }
        if (map == TextureDataType.MATERIAL_MAP && loadMaterial) {
            return materialData.get(key + typeStr + frame);
        } else if (map == TextureDataType.NORMAL_MAP && loadNormal) {
            return normalData.get(key + typeStr + frame);
        } else if (map == TextureDataType.SURFACE_MAP && loadSurface) {
            return surfaceData.get(key + typeStr + frame);
        } else {
            return null;
        }
    }

    /**
     * Loads a texture data CSV and makes that data available internally. Duplicate entries will replace previous data.
     * <p>
     * @param localPath The local path to the csv file (ex. "data/lights/core_texture_data.csv").
     * <p>
     * @since Alpha 1.5
     */
    public static void readTextureDataCSV(String localPath) {
        try {
            final JSONArray textureData = Global.getSettings().loadCSV(localPath);

            for (int i = 0; i < textureData.length(); i++) {
                if (i % 10 == 0) {
                    ShaderModPlugin.refresh();
                }

                final JSONObject entry = textureData.getJSONObject(i);

                if (!entry.optString("id").isEmpty() && !entry.optString("type").isEmpty()
                        && !entry.optString("map").isEmpty()
                        && !entry.optString("path").isEmpty()) {
                    boolean success = true;
                    String type = "";
                    switch (entry.getString("type")) {
                        case "ship" ->
                            type = "$$$ship";
                        case "turret" ->
                            type = "$$$turret";
                        case "turretbarrel" ->
                            type = "$$$turretbarrel";
                        case "turretunder" ->
                            type = "$$$turretunder";
                        case "turretcoversmall" ->
                            type = "$$$turretcoversmall";
                        case "turretcovermedium" ->
                            type = "$$$turretcovermedium";
                        case "turretcoverlarge" ->
                            type = "$$$turretcoverlarge";
                        case "hardpoint" ->
                            type = "$$$hardpoint";
                        case "hardpointbarrel" ->
                            type = "$$$hardpointbarrel";
                        case "hardpointunder" ->
                            type = "$$$hardpointunder";
                        case "hardpointcoversmall" ->
                            type = "$$$hardpointcoversmall";
                        case "hardpointcovermedium" ->
                            type = "$$$hardpointcovermedium";
                        case "hardpointcoverlarge" ->
                            type = "$$$hardpointcoverlarge";
                        case "missile" ->
                            type = "$$$missile";
                        case "asteroid" ->
                            type = "$$$asteroid";
                        default ->
                            success = false;
                    }
                    if (!success) {
                        continue;
                    }

                    final String path = entry.getString("path");

                    switch (entry.getString("map")) {
                        case "material" -> {
                            if (loadMaterial) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                materialData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                        new TextureEntry(Global.getSettings().getSprite(path),
                                                (float) entry.optDouble("magnitude", 1.0)));
                            }
                        }
                        case "normal" -> {
                            if (loadNormal) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                normalData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                        new TextureEntry(Global.getSettings().getSprite(path),
                                                (float) entry.optDouble("magnitude", 1.0)));
                            }
                        }
                        case "surface" -> {
                            if (loadSurface) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                surfaceData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                        new TextureEntry(Global.getSettings().getSprite(path),
                                                (float) entry.optDouble("magnitude", 1.0)));
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(TextureData.class).log(Level.ERROR, "Texture data loading failed for " + localPath + "! "
                    + e.getMessage());
        }
    }

    /**
     * Loads a texture data CSV and makes that data available internally. Duplicate entries will NOT replace previous
     * data.
     * <p>
     * @param localPath The local path to the csv file (ex. "data/lights/core_texture_data.csv").
     * <p>
     * @since Alpha 1.5
     */
    public static void readTextureDataCSVNoOverwrite(String localPath) {
        try {
            final JSONArray textureData = Global.getSettings().loadCSV(localPath);

            for (int i = 0; i < textureData.length(); i++) {
                if (i % 10 == 0) {
                    ShaderModPlugin.refresh();
                }

                final JSONObject entry = textureData.getJSONObject(i);

                if (!entry.optString("id").isEmpty() && !entry.optString("type").isEmpty()
                        && !entry.optString("map").isEmpty()
                        && !entry.optString("path").isEmpty()) {
                    boolean success = true;
                    String type = "";
                    switch (entry.getString("type")) {
                        case "ship" ->
                            type = "$$$ship";
                        case "turret" ->
                            type = "$$$turret";
                        case "turretbarrel" ->
                            type = "$$$turretbarrel";
                        case "turretunder" ->
                            type = "$$$turretunder";
                        case "turretcoversmall" ->
                            type = "$$$turretcoversmall";
                        case "turretcovermedium" ->
                            type = "$$$turretcovermedium";
                        case "turretcoverlarge" ->
                            type = "$$$turretcoverlarge";
                        case "hardpoint" ->
                            type = "$$$hardpoint";
                        case "hardpointbarrel" ->
                            type = "$$$hardpointbarrel";
                        case "hardpointunder" ->
                            type = "$$$hardpointunder";
                        case "hardpointcoversmall" ->
                            type = "$$$hardpointcoversmall";
                        case "hardpointcovermedium" ->
                            type = "$$$hardpointcovermedium";
                        case "hardpointcoverlarge" ->
                            type = "$$$hardpointcoverlarge";
                        case "missile" ->
                            type = "$$$missile";
                        case "asteroid" ->
                            type = "$$$asteroid";
                        default ->
                            success = false;
                    }
                    if (!success) {
                        continue;
                    }

                    final String path = entry.getString("path");

                    switch (entry.getString("map")) {
                        case "material" -> {
                            if (loadMaterial) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                if (!materialData.containsKey(entry.getString("id") + type + entry.optInt("frame", 0))) {
                                    materialData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                            new TextureEntry(Global.getSettings().getSprite(
                                                    path),
                                                    (float) entry.optDouble("magnitude", 1.0)));
                                }
                            }
                        }
                        case "normal" -> {
                            if (loadNormal) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                if (!normalData.containsKey(entry.getString("id") + type + entry.optInt("frame", 0))) {
                                    normalData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                            new TextureEntry(
                                                    Global.getSettings().getSprite(path),
                                                    (float) entry.optDouble("magnitude", 1.0)));
                                }
                            }
                        }
                        case "surface" -> {
                            if (loadSurface) {
                                if (Global.getSettings().getSprite(path) == null
                                        || Global.getSettings().getSprite(path).getHeight() < 1) {
                                    try {
                                        Global.getSettings().loadTexture(path);
                                    } catch (IOException e) {
                                        Global.getLogger(TextureData.class).log(Level.ERROR,
                                                "Texture loading failed at " + path
                                                + "! " + e.getMessage());
                                        continue;
                                    }
                                }
                                if (!surfaceData.containsKey(entry.getString("id") + type + entry.optInt("frame", 0))) {
                                    surfaceData.put(entry.getString("id") + type + entry.optInt("frame", 0),
                                            new TextureEntry(Global.getSettings().getSprite(
                                                    path),
                                                    (float) entry.optDouble("magnitude", 1.0)));
                                }
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(TextureData.class).log(Level.ERROR, "Texture data loading failed for " + localPath + "! "
                    + e.getMessage());
        }
    }

    /**
     * Queries if material maps are configured to be loaded or not.
     * <p>
     * @return True if material maps should be loaded.
     * <p>
     * @since 1.10.0
     */
    public static boolean isLoadMaterial() {
        return loadMaterial;
    }

    /**
     * Queries if normal maps are configured to be loaded or not.
     * <p>
     * @return True if normal maps should be loaded.
     * <p>
     * @since 1.10.0
     */
    public static boolean isLoadNormal() {
        return loadNormal;
    }

    /**
     * Queries if surface maps are configured to be loaded or not.
     * <p>
     * @return True if surface maps should be loaded.
     * <p>
     * @since 1.10.0
     */
    public static boolean isLoadSurface() {
        return loadSurface;
    }

    private static void loadSettings() throws IOException, JSONException {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        final boolean enableShaders = settings.getBoolean("enableShaders");
        if (!enableShaders) {
            loadMaterial = false;
            loadNormal = false;
            loadSurface = false;
            return;
        }

        final boolean enabled = settings.getBoolean("enableLights");
        if (enabled) {
            loadMaterial = settings.getBoolean("loadMaterial");

            loadNormal = settings.getBoolean("enableNormal");
            if (loadNormal) {
                loadSurface = settings.getBoolean("loadSurface");
            } else {
                loadSurface = false;
            }
        } else {
            loadMaterial = false;
            loadNormal = false;
            loadSurface = false;
        }
    }

    public static enum TextureDataType {

        MATERIAL_MAP, NORMAL_MAP, SURFACE_MAP
    }

    public static enum ObjectType {

        SHIP,
        TURRET, TURRET_BARREL, TURRET_UNDER,
        TURRET_COVER_SMALL, TURRET_COVER_MEDIUM, TURRET_COVER_LARGE,
        HARDPOINT, HARDPOINT_BARREL, HARDPOINT_UNDER,
        HARDPOINT_COVER_SMALL, HARDPOINT_COVER_MEDIUM, HARDPOINT_COVER_LARGE,
        MISSILE,
        ASTEROID
    }
}
