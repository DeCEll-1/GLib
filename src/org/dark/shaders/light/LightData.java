package org.dark.shaders.light;

import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.dark.shaders.util.ShaderLib;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A class for reading csv data to the program so that automatic lighting can be applied.
 * <p>
 * @author DarkRevenant
 */
public class LightData {

    static final Map<String, LightEntry> beamLightData = new HashMap<>(100);
    static final Map<String, LightEntry> projectileLightData = new HashMap<>(500);

    /**
     * Gets a copy of the requested data. Returns null if no such LightData entry exists.
     * <p>
     * @param key  The ID of the desired projectile or weapon (for beams) light data.
     * @param type Whether the desired object is a projectile or a beam.
     * <p>
     * @return The requested data. Null if no such LightData entry exists.
     */
    public static LightEntry getLightData(String key, LightDataType type) {
        if (type == LightDataType.PROJECTILE) {
            return projectileLightData.get(key);
        } else if (type == LightDataType.BEAM) {
            return beamLightData.get(key);
        } else {
            return null;
        }
    }

    /**
     * Loads a lighting data CSV and makes that data available internally. Duplicate entries will replace previous data.
     * <p>
     * @param localPath The local path to the csv file (ex. "data/lights/core_light_data.csv").
     */
    public static void readLightDataCSV(String localPath) {
        try {
            final JSONArray lightData = Global.getSettings().loadCSV(localPath);

            for (int i = 0; i < lightData.length(); i++) {
                final JSONObject entry = lightData.getJSONObject(i);

                if (!entry.optString("id").isEmpty() && !entry.optString("type").isEmpty()) {
                    final LightEntry lightEntry = new LightEntry();

                    boolean success = true;
                    switch (entry.getString("type")) {
                        case "projectile":
                            projectileLightData.put(entry.getString("id"), lightEntry);
                            break;
                        case "beam":
                            beamLightData.put(entry.getString("id"), lightEntry);
                            break;
                        default:
                            success = false;
                    }
                    if (!success) {
                        continue;
                    }

                    if (entry.optDouble("size", 0.0) > 0.0 && entry.optDouble("intensity", 0.0) > 0.0 && !entry.optString("color").isEmpty()) {
                        lightEntry.hasStandard = true;
                        lightEntry.standardSize = (float) entry.getDouble("size");
                        lightEntry.standardIntensity = (float) entry.getDouble("intensity");
                        lightEntry.standardColor = toColor3(entry.getString("color"));
                        lightEntry.standardFadeout = (float) entry.optDouble("fadeout", 0.0);
                        lightEntry.standardOffset = (float) entry.optDouble("offset", 0.0);
                    }

                    if (entry.optDouble("hit size", 0.0) > 0.0 && entry.optDouble("hit intensity", 0.0) > 0.0 && !entry.optString("hit color").isEmpty()) {
                        lightEntry.hasHit = true;
                        lightEntry.hitSize = (float) entry.getDouble("hit size");
                        lightEntry.hitIntensity = (float) entry.getDouble("hit intensity");
                        lightEntry.hitColor = toColor3(entry.getString("hit color"));
                        lightEntry.hitFadeout = (float) entry.optDouble("hit fadeout", 0.0);
                    }

                    if (entry.optDouble("flash size", 0.0) > 0.0 && entry.optDouble("flash intensity", 0.0) > 0.0 && !entry.optString("flash color").isEmpty()) {
                        lightEntry.hasFlash = true;
                        lightEntry.flashSize = (float) entry.getDouble("flash size");
                        lightEntry.flashIntensity = (float) entry.getDouble("flash intensity");
                        lightEntry.flashColor = toColor3(entry.getString("flash color"));
                        lightEntry.flashFadeout = (float) entry.optDouble("flash fadeout", 0.0);
                        lightEntry.flashOffset = (float) entry.optDouble("flash offset", 0.0);
                    }

                    lightEntry.chance = (float) entry.optDouble("chance", 1f);
                    lightEntry.fighterDim = entry.optBoolean("fighter dim", true);
                }
            }
        } catch (IOException | JSONException e) {
            Global.getLogger(ShaderLib.class).log(Level.ERROR, "Light data loading failed for " + localPath + "! " + e.getMessage());
        }
    }

    /**
     * Loads a lighting data CSV and makes that data available internally. Duplicate entries will NOT replace previous data.
     * <p>
     * @param localPath The local path to the csv file (ex. "data/lights/core_light_data.csv").
     * <p>
     * @since Alpha 1.03
     */
    public static void readLightDataCSVNoOverwrite(String localPath) {
        try {
            final JSONArray lightData = Global.getSettings().loadCSV(localPath);

            for (int i = 0; i < lightData.length(); i++) {
                final JSONObject entry = lightData.getJSONObject(i);

                if (!entry.optString("id").isEmpty() && !entry.optString("type").isEmpty()) {
                    final LightEntry lightEntry = new LightEntry();

                    boolean success = true;
                    switch (entry.getString("type")) {
                        case "projectile":
                            if (projectileLightData.containsKey(entry.getString("id"))) {
                                success = false;
                            } else {
                                projectileLightData.put(entry.getString("id"), lightEntry);
                            }
                            break;
                        case "beam":
                            if (beamLightData.containsKey(entry.getString("id"))) {
                                success = false;
                            } else {
                                beamLightData.put(entry.getString("id"), lightEntry);
                            }
                            break;
                        default:
                            success = false;
                    }
                    if (!success) {
                        continue;
                    }

                    if (entry.optDouble("size", 0.0) > 0.0 && entry.optDouble("intensity", 0.0) > 0.0 && !entry.optString("color").isEmpty()) {
                        lightEntry.hasStandard = true;
                        lightEntry.standardSize = (float) entry.getDouble("size");
                        lightEntry.standardIntensity = (float) entry.getDouble("intensity");
                        lightEntry.standardColor = toColor3(entry.getString("color"));
                        lightEntry.standardFadeout = (float) entry.optDouble("fadeout", 0.0);
                        lightEntry.standardOffset = (float) entry.optDouble("offset", 0.0);
                    }

                    if (entry.optDouble("hit size", 0.0) > 0.0 && entry.optDouble("hit intensity", 0.0) > 0.0 && !entry.optString("hit color").isEmpty()) {
                        lightEntry.hasHit = true;
                        lightEntry.hitSize = (float) entry.getDouble("hit size");
                        lightEntry.hitIntensity = (float) entry.getDouble("hit intensity");
                        lightEntry.hitColor = toColor3(entry.getString("hit color"));
                        lightEntry.hitFadeout = (float) entry.optDouble("hit fadeout", 0.0);
                    }

                    if (entry.optDouble("flash size", 0.0) > 0.0 && entry.optDouble("flash intensity", 0.0) > 0.0 && !entry.optString("flash color").isEmpty()) {
                        lightEntry.hasFlash = true;
                        lightEntry.flashSize = (float) entry.getDouble("flash size");
                        lightEntry.flashIntensity = (float) entry.getDouble("flash intensity");
                        lightEntry.flashColor = toColor3(entry.getString("flash color"));
                        lightEntry.flashFadeout = (float) entry.optDouble("flash fadeout", 0.0);
                        lightEntry.flashOffset = (float) entry.optDouble("flash offset", 0.0);
                    }

                    lightEntry.chance = (float) entry.optDouble("chance", 1f);
                    lightEntry.fighterDim = entry.optBoolean("fighter dim", true);
                }
            }
        } catch (IOException | JSONException e) {
            Global.getLogger(ShaderLib.class).log(Level.ERROR, "Light data loading failed for " + localPath + "! " + e.getMessage());
        }
    }

    private static Color toColor3(String in) {
        final String inPredicate = in.substring(1, in.length() - 1);
        final String[] array = inPredicate.split(",");
        return new Color(Integer.parseInt(array[0]), Integer.parseInt(array[1]), Integer.parseInt(array[2]), 255);
    }

    public static enum LightDataType {

        PROJECTILE, BEAM
    }
}
