package org.dark.shaders;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.dark.graphics.plugins.MissileSelfDestruct;
import org.dark.graphics.util.ShipColors;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.light.LightData;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.post.PostProcessShader;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is the base mod plugin for ShaderLib, which initializes its shaders.
 * <p>
 * @author DarkRevenant
 */
public final class ShaderModPlugin extends BaseModPlugin {

    public static final Map<Integer, String> ASTEROID_MAP = new HashMap<>(16);

    public static boolean hasTwigLib = false;
    public static boolean templarsExists = false;

    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";

    private static boolean useLargeRipple = false;
    private static boolean useSmallRipple = false;

    private static void loadSettings() throws IOException, JSONException {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        final boolean enabled = settings.getBoolean("enableDistortion");
        if (enabled) {
            useLargeRipple = settings.getBoolean("useLargeRipple");
            if (!useLargeRipple) {
                useSmallRipple = true;
            }
        }
    }

    @Override
    public void onApplicationLoad() throws IOException, JSONException {
        Global.getLogger(ShaderModPlugin.class).setLevel(Level.WARN);

        hasTwigLib = Global.getSettings().getModManager().isModEnabled("ztwiglib");
        try {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.TEMModPlugin");
            templarsExists = true;
        } catch (ClassNotFoundException ex) {
        }

        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/asteroid1.png").getTextureId(), "asteroid1");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/asteroid2.png").getTextureId(), "asteroid2");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/asteroid3.png").getTextureId(), "asteroid3");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/asteroid4.png").getTextureId(), "asteroid4");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid00.png").getTextureId(), "ring_asteroid00");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid01.png").getTextureId(), "ring_asteroid01");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid02.png").getTextureId(), "ring_asteroid02");
        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid03.png").getTextureId(), "ring_asteroid03");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid04.png"), "ring_asteroid04");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid05.png"), "ring_asteroid05");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid06.png"), "ring_asteroid06");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid07.png"), "ring_asteroid07");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid08.png"), "ring_asteroid08");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid09.png"), "ring_asteroid09");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid10.png"), "ring_asteroid10");
//        ASTEROID_MAP.put(Global.getSettings().getSprite("graphics/asteroids/ring_asteroid11.png"), "ring_asteroid11");

        ShaderLib.init();
        ShipColors.init();
        MissileSelfDestruct.loadSettings();

        if (ShaderLib.areShadersAllowed()) {
            //ShaderLib.addShaderAPI(new LensShader());
            //ShaderLib.addShaderAPI(new InvertShader());
            ShaderLib.addShaderAPI(new PostProcessShader());
        }

        if (ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed()) {
            LightData.readLightDataCSVNoOverwrite("data/lights/core_light_data.csv");
            TextureData.readTextureDataCSVNoOverwrite("data/lights/core_texture_data.csv");
            ShaderLib.addShaderAPI(new LightShader());
            ShaderLib.addShaderAPI(new DistortionShader());

            try {
                loadSettings();
            } catch (IOException | JSONException e) {
                Global.getLogger(ShaderModPlugin.class).log(Level.ERROR, "Failed to load shader settings: " + e.getMessage());
            }

            if (useSmallRipple || useLargeRipple) {
                String path = "graphics/shaders/distortions/wave.png";
                try {
                    Global.getSettings().loadTexture(path);
                } catch (IOException e) {
                    Global.getLogger(ShaderModPlugin.class).log(Level.ERROR, "Texture loading failed at " + path + "! " + e.getMessage());
                    throw e; // Crash the game; it's probably too fucked to work at this point, anyway
                }
            }

            if (useSmallRipple) {
                String path = "";
                try {
                    for (int i = 1; i <= 60; i++) {
                        if (i < 10) {
                            path = "graphics/shaders/distortions/smallripple/000" + i + ".PNG";
                        } else {
                            path = "graphics/shaders/distortions/smallripple/00" + i + ".PNG";
                        }
                        Global.getSettings().loadTexture(path);
                    }
                } catch (IOException e) {
                    Global.getLogger(ShaderModPlugin.class).log(Level.ERROR, "Texture loading failed at " + path + "! " + e.getMessage());
                    throw e; // Crash the game; it's probably too fucked to work at this point, anyway
                }
            } else if (useLargeRipple) {
                String path = "";
                try {
                    for (int i = 1; i <= 60; i++) {
                        if (i < 10) {
                            path = "graphics/shaders/distortions/ripple/000" + i + ".PNG";
                        } else {
                            path = "graphics/shaders/distortions/ripple/00" + i + ".PNG";
                        }
                        Global.getSettings().loadTexture(path);
                    }
                } catch (IOException e) {
                    Global.getLogger(ShaderModPlugin.class).log(Level.ERROR, "Texture loading failed at " + path + "! " + e.getMessage());
                    throw e; // Crash the game; it's probably too fucked to work at this point, anyway
                }
            }
        }
    }
}
