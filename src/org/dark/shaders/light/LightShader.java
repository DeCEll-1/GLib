package org.dark.shaders.light;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MissileRenderDataAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Level;
import org.dark.graphics.util.Tessellate;
import org.dark.shaders.ShaderModPlugin;
import org.dark.shaders.util.ShaderAPI;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.dark.shaders.util.TextureData.ObjectType;
import org.dark.shaders.util.TextureData.TextureDataType;
import org.dark.shaders.util.TextureEntry;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ARBTextureRg;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.vector.Vector2f;

/**
 * This implementation of ShaderAPI contains the general Starsector Lighting Engine. Do not modify.
 * <p>
 * @author DarkRevenant
 */
public class LightShader implements ShaderAPI {

    private static final String DATA_KEY = "shaderlib_LightShader";

    private static final Comparator<LightAPI> LIGHTSIZE = new Comparator<LightAPI>() {
        @Override
        public int compare(LightAPI light1, LightAPI light2) {
            if (light1.getType() == 3 && light2.getType() != 3) {
                return -1;
            } else if (light1.getType() != 3 && light2.getType() == 3) {
                return 1;
            }
            final float light1factor = light1.getSize();
            final float light2factor = light2.getSize();
            if (light1factor > light2factor) {
                return -1;
            } else if (light1factor < light2factor) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    private static final int MAX_LIGHTS = 372;
    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";

    private static final Vector2f ZERO = new Vector2f();

    private static final Color deadSurface = new Color(0, 100, 150);

    private static final Vector2f tempVec = new Vector2f();

    /**
     * Adds a light to the rendering list. If it is a StandardLight, it will be processed automatically. Lights defined
     * via csv will be created automatically through the course of gameplay; ignore this method if the data in the csv
     * is sufficient. This function will do nothing if the light shader is not enabled.
     * <p>
     * Does not check for duplicates.
     * <p>
     * @param light The light to add.
     */
    public static void addLight(LightAPI light) {
        final ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);

        if (lightShader instanceof LightShader && lightShader.isEnabled()) {
            if (light != null) {
                LocalData localData = (LocalData) Global.getCombatEngine().getCustomData().get(DATA_KEY);
                if (localData == null) {
                    return;
                }
                final List<LightAPI> lights = localData.lights;
                if (lights != null) {
                    lights.add(light);
                }
            }
        }
    }

    /**
     * Forcibly removes a light from the rendering list. This function will do nothing if the light shader is not
     * enabled.
     * <p>
     * @param light The light to remove.
     * <p>
     * @since Beta 1.03
     */
    public static void removeLight(LightAPI light) {
        final ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);

        if (lightShader instanceof LightShader && lightShader.isEnabled()) {
            if (light != null) {
                LocalData localData = (LocalData) Global.getCombatEngine().getCustomData().get(DATA_KEY);
                if (localData == null) {
                    return;
                }
                final List<LightAPI> lights = localData.lights;
                if (lights != null) {
                    lights.remove(light);
                }
            }
        }
    }

    private boolean bloomEnabled = false;
    private float bloomIntensity = 2f;
    private int bloomMips = 3;
    private int bloomQuality = 3;
    private float bloomScale = 0.5f;

    private final FloatBuffer dataBuffer = BufferUtils.createFloatBuffer(4096);
    private final FloatBuffer dataBufferPre = BufferUtils.createFloatBuffer(4096);

    private boolean enabled = false;
    private float fighterLightMultiplier = 1f;
    private float flashHeight = 150f;
    private float flatness = 0f;
    private int hdrBuffer2Id = 0;
    private int hdrBuffer3Id = 0;
    private int hdrBufferId = 0;
    private int hdrTex = 0;
    private int hdrTex2 = 0;
    private int hdrTex3 = 0;
    private final int[] index = new int[13];
    private final int[] indexAux = new int[2];
    private final int[] indexBloom1 = new int[4];
    private final int[] indexBloom2 = new int[4];
    private final int[] indexBloom3 = new int[3];
    private float lastAngle;
    private float lastFlatness;
    private float lightDepth = 0.2f;
    private float lightMultiplier = 1f;
    private float lightSizeMultiplier = 1f;
    private int lightTex = 0;
    private int maxLights = 372;
    private int maxLineLights = 372;
    private int normalBufferId = 0;
    private boolean normalEnabled = false;
    private int normalTex = 0;
    private int program = 0;
    private int programAux = 0;
    private int programBloom1 = 0;
    private int programBloom2 = 0;
    private int programBloom3 = 0;
    private float specularHardness = 0.4f;
    private float specularMultiplier = 5f;
    private float standardHeight = 100f;
    private boolean validated = false;
    private boolean validatedAux = false;
    private boolean validatedBloom1 = false;
    private boolean validatedBloom2 = false;
    private boolean validatedBloom3 = false;

    public LightShader() {
        if (!ShaderLib.areShadersAllowed() || !ShaderLib.areBuffersAllowed()) {
            enabled = false;
            return;
        }

        Global.getLogger(LightShader.class).setLevel(Level.ERROR);

        try {
            loadSettings();
        } catch (IOException | JSONException e) {
            Global.getLogger(LightShader.class).log(Level.ERROR, "Failed to load shader settings: " + e.getMessage());
            enabled = false;
            return;
        }

        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        if (!GLContext.getCapabilities().OpenGL30 || vendor.contains("Intel")) {
            Global.getLogger(LightShader.class).log(Level.WARN, "Bloom is not supported; disabling");
            bloomEnabled = false;
        }

        if (!enabled) {
            return;
        }

        String vertShader = null;
        String fragShader = null;

        if (normalEnabled) {
            try {
                vertShader = Global.getSettings().loadText("data/shaders/lights/2dtangent.vert");
                fragShader = Global.getSettings().loadText("data/shaders/lights/2dtangent.frag");
            } catch (IOException ex) {
                Global.getLogger(LightShader.class).log(Level.ERROR,
                                                        "Normal transform shader loading error!  Normals disabled!" +
                                                        ex.getMessage());
                normalEnabled = false;
            }
        }

        if (normalEnabled) {
            programAux = ShaderLib.loadShader(vertShader, fragShader);

            if (programAux == 0) {
                Global.getLogger(LightShader.class).log(Level.ERROR,
                                                        "Normal transform shader compile error!  Normals disabled!");
                normalEnabled = false;
            }
        }

        try {
            if (normalEnabled) {
                vertShader = Global.getSettings().loadText("data/shaders/lights/lightsnormal.vert");
                fragShader = Global.getSettings().loadText("data/shaders/lights/lightsnormal.frag");
            } else {
                vertShader = Global.getSettings().loadText("data/shaders/lights/lights.vert");
                fragShader = Global.getSettings().loadText("data/shaders/lights/lights.frag");
            }
        } catch (IOException ex) {
            Global.getLogger(LightShader.class).log(Level.ERROR, "Lighting shader loading error!  Lighting disabled!" +
                                                    ex.getMessage());
            enabled = false;
            return;
        }

        program = ShaderLib.loadShader(vertShader, fragShader);

        if (program == 0) {
            enabled = false;
            Global.getLogger(LightShader.class).log(Level.ERROR, "Lighting shader compile error!  Lighting disabled!");
            return;
        }

        if (bloomEnabled) {
            try {
                vertShader = Global.getSettings().loadText("data/shaders/bloom/bloom1.vert");
                if (bloomQuality == 1) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom1-5.frag");
                } else if (bloomQuality == 2) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom1-7.frag");
                } else if (bloomQuality == 3) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom1-9.frag");
                } else if (bloomQuality == 4) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom1-11.frag");
                } else if (bloomQuality == 5) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom1-13.frag");
                }
            } catch (IOException ex) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 1 loading error!  Bloom disabled!" +
                                                        ex.getMessage());
            }

            programBloom1 = ShaderLib.loadShader(vertShader, fragShader);

            if (programBloom1 == 0) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 1 compile error!  Bloom disabled!");
            }

            try {
                vertShader = Global.getSettings().loadText("data/shaders/bloom/bloom2.vert");
                if (bloomQuality == 1) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom2-5.frag");
                } else if (bloomQuality == 2) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom2-7.frag");
                } else if (bloomQuality == 3) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom2-9.frag");
                } else if (bloomQuality == 4) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom2-11.frag");
                } else if (bloomQuality == 5) {
                    fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom2-13.frag");
                }
            } catch (IOException ex) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 2 loading error!  Bloom disabled!" +
                                                        ex.getMessage());
            }

            programBloom2 = ShaderLib.loadShader(vertShader, fragShader);

            if (programBloom2 == 0) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 2 compile error!  Bloom disabled!");
            }

            try {
                vertShader = Global.getSettings().loadText("data/shaders/bloom/bloom3.vert");
                fragShader = Global.getSettings().loadText("data/shaders/bloom/bloom3.frag");
            } catch (IOException ex) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 3 loading error!  Bloom disabled!" +
                                                        ex.getMessage());
            }

            programBloom3 = ShaderLib.loadShader(vertShader, fragShader);

            if (programBloom3 == 0) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom shader 3 compile error!  Bloom disabled!");
            }
        }

        lightTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_1D, lightTex);
        if (ShaderLib.useBufferCore()) {
            GL11.glTexImage1D(GL11.GL_TEXTURE_1D, 0, GL30.GL_R32F, 4096, 0, GL11.GL_RED, GL11.GL_FLOAT,
                              (ByteBuffer) null);
        } else {
            GL11.glTexImage1D(GL11.GL_TEXTURE_1D, 0, ARBTextureRg.GL_R32F, 4096, 0, GL11.GL_RED, GL11.GL_FLOAT,
                              (ByteBuffer) null);
        }

        if (normalEnabled) {
            normalTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, ShaderLib.getInternalWidth(),
                              ShaderLib.getInternalHeight(), 0, GL11.GL_RGB,
                              GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            if (ShaderLib.useBufferCore()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else {
                EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            if (ShaderLib.useBufferCore()) {
                normalBufferId = ShaderLib.makeFramebuffer(GL30.GL_COLOR_ATTACHMENT0, normalTex,
                                                           ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(),
                                                           0);
            } else if (ShaderLib.useBufferARB()) {
                normalBufferId = ShaderLib.makeFramebuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0, normalTex,
                                                           ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(),
                                                           0);
            } else {
                normalBufferId = ShaderLib.makeFramebuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, normalTex,
                                                           ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(),
                                                           0);
            }

            if (normalBufferId == 0) {
                normalEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR,
                                                        "Normals framebuffer object error!  Normals disabled!");
            }
        }

        if (bloomEnabled) {
            hdrTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB16, ShaderLib.getInternalWidth(),
                              ShaderLib.getInternalHeight(), 0, GL11.GL_RGB,
                              GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
            if (ShaderLib.useBufferCore()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else {
                EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            if (ShaderLib.useBufferCore()) {
                hdrBufferId = ShaderLib.makeFramebuffer(GL30.GL_COLOR_ATTACHMENT0, hdrTex,
                                                        ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(), 0);
            } else if (ShaderLib.useBufferARB()) {
                hdrBufferId = ShaderLib.makeFramebuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0, hdrTex,
                                                        ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(), 0);
            } else {
                hdrBufferId = ShaderLib.makeFramebuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, hdrTex,
                                                        ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(), 0);
            }

            hdrTex2 = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex2);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8,
                              ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                              ShaderLib.getInternalHeight() / (int) Math.pow(2, bloomMips - 1), 0, GL11.GL_RGB,
                              GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            if (ShaderLib.useBufferCore()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else {
                EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            if (ShaderLib.useBufferCore()) {
                hdrBuffer2Id = ShaderLib.makeFramebuffer(GL30.GL_COLOR_ATTACHMENT0, hdrTex2,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            } else if (ShaderLib.useBufferARB()) {
                hdrBuffer2Id = ShaderLib.makeFramebuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0, hdrTex2,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            } else {
                hdrBuffer2Id = ShaderLib.makeFramebuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, hdrTex2,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            }

            hdrTex3 = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex3);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8,
                              ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                              ShaderLib.getInternalHeight() / (int) Math.pow(2, bloomMips - 1), 0, GL11.GL_RGB,
                              GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            if (ShaderLib.useBufferCore()) {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            } else {
                EXTFramebufferObject.glGenerateMipmapEXT(GL11.GL_TEXTURE_2D);
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            if (ShaderLib.useBufferCore()) {
                hdrBuffer3Id = ShaderLib.makeFramebuffer(GL30.GL_COLOR_ATTACHMENT0, hdrTex3,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            } else if (ShaderLib.useBufferARB()) {
                hdrBuffer3Id = ShaderLib.makeFramebuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0, hdrTex3,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            } else {
                hdrBuffer3Id = ShaderLib.makeFramebuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, hdrTex3,
                                                         ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1),
                                                         ShaderLib.getInternalHeight() /
                                                         (int) Math.pow(2, bloomMips - 1), 0);
            }

            if (hdrBufferId == 0 || hdrBuffer2Id == 0 || hdrBuffer3Id == 0) {
                bloomEnabled = false;
                Global.getLogger(LightShader.class).log(Level.ERROR, "Bloom framebuffer object error!  Bloom disabled!");
            }
        }

        GL20.glUseProgram(program);
        index[0] = GL20.glGetUniformLocation(program, "tex");
        index[1] = GL20.glGetUniformLocation(program, "buf");
        index[2] = GL20.glGetUniformLocation(program, "data");
        index[3] = GL20.glGetUniformLocation(program, "trans");
        index[4] = GL20.glGetUniformLocation(program, "size");
        index[5] = GL20.glGetUniformLocation(program, "norm1");
        index[6] = GL20.glGetUniformLocation(program, "norm2");
        index[7] = GL20.glGetUniformLocation(program, "norm3");
        index[8] = GL20.glGetUniformLocation(program, "hdr");
        index[9] = GL20.glGetUniformLocation(program, "specmult");
        if (normalEnabled) {
            index[10] = GL20.glGetUniformLocation(program, "normal");
            index[11] = GL20.glGetUniformLocation(program, "surface");
            index[12] = GL20.glGetUniformLocation(program, "spechard");
        }
        GL20.glUniform1i(index[0], 0);
        GL20.glUniform1i(index[1], 1);
        GL20.glUniform1i(index[2], 2);
        GL20.glUniform1f(index[3], ShaderLib.getSquareTransform());
        if (bloomEnabled) {
            GL20.glUniform1f(index[8], 1f / 16f);
        } else {
            GL20.glUniform1f(index[8], 1f);
        }
        GL20.glUniform1f(index[9], specularMultiplier);
        if (normalEnabled) {
            GL20.glUniform1i(index[10], 3);
            GL20.glUniform1i(index[11], 4);
            GL20.glUniform1f(index[12], specularHardness);
        }
        GL20.glUseProgram(0);

        if (bloomEnabled) {
            GL20.glUseProgram(programBloom1);
            indexBloom1[0] = GL20.glGetUniformLocation(programBloom1, "tex");
            indexBloom1[1] = GL20.glGetUniformLocation(programBloom1, "screen");
            indexBloom1[2] = GL20.glGetUniformLocation(programBloom1, "hdr");
            indexBloom1[3] = GL20.glGetUniformLocation(programBloom1, "scale");
            GL20.glUniform1i(indexBloom1[0], 0);
            GL20.glUniform2f(indexBloom1[1], (ShaderLib.getInternalWidth() / (int) Math.pow(2, bloomMips - 1)),
                             ShaderLib.getVisibleU());
            GL20.glUniform1f(indexBloom1[2], 16f);
            GL20.glUniform1f(indexBloom1[3], bloomScale);
            GL20.glUseProgram(programBloom2);
            indexBloom2[0] = GL20.glGetUniformLocation(programBloom2, "tex");
            indexBloom2[1] = GL20.glGetUniformLocation(programBloom2, "screen");
            indexBloom2[2] = GL20.glGetUniformLocation(programBloom2, "intensity");
            indexBloom2[3] = GL20.glGetUniformLocation(programBloom2, "scale");
            GL20.glUniform1i(indexBloom2[0], 0);
            GL20.glUniform2f(indexBloom2[1], ShaderLib.getInternalHeight() / (int) Math.pow(2, bloomMips - 1),
                             ShaderLib.getVisibleV());
            GL20.glUniform1f(indexBloom2[2], bloomIntensity);
            GL20.glUniform1f(indexBloom2[3], bloomScale);
            GL20.glUseProgram(programBloom3);
            indexBloom3[0] = GL20.glGetUniformLocation(programBloom3, "tex");
            indexBloom3[1] = GL20.glGetUniformLocation(programBloom3, "glow");
            indexBloom3[2] = GL20.glGetUniformLocation(programBloom3, "hdr");
            GL20.glUniform1i(indexBloom3[0], 0);
            GL20.glUniform1i(indexBloom3[1], 1);
            GL20.glUniform1f(indexBloom3[2], 16f);
            GL20.glUseProgram(0);
        }

        if (normalEnabled) {
            GL20.glUseProgram(programAux);
            indexAux[0] = GL20.glGetUniformLocation(programAux, "tex");
            indexAux[1] = GL20.glGetUniformLocation(programAux, "data");
            GL20.glUniform1i(indexAux[0], 0);
            GL20.glUseProgram(0);
        }

        enabled = true;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (!enabled) {
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();

        final List<LightAPI> lights = ((LocalData) engine.getCustomData().get(DATA_KEY)).lights;

        // Advance, fade out, and destroy lights
        if (!engine.isPaused()) {
            Iterator<LightAPI> iter3 = lights.iterator();
            while (iter3.hasNext()) {
                final LightAPI light = iter3.next();
                if (light.advance(amount)) {
                    iter3.remove();
                }
            }
        }
    }

    @Override
    public void destroy() {
        if (program != 0) {
            ByteBuffer countbb = ByteBuffer.allocateDirect(4);
            ByteBuffer shadersbb = ByteBuffer.allocateDirect(8);
            IntBuffer count = countbb.asIntBuffer();
            IntBuffer shaders = shadersbb.asIntBuffer();
            GL20.glGetAttachedShaders(program, count, shaders);
            for (int i = 0; i < 2; i++) {
                GL20.glDeleteShader(shaders.get());
            }
            GL20.glDeleteProgram(program);
        }
        if (programAux != 0) {
            ByteBuffer countbb = ByteBuffer.allocateDirect(4);
            ByteBuffer shadersbb = ByteBuffer.allocateDirect(8);
            IntBuffer count = countbb.asIntBuffer();
            IntBuffer shaders = shadersbb.asIntBuffer();
            GL20.glGetAttachedShaders(programAux, count, shaders);
            for (int i = 0; i < 2; i++) {
                GL20.glDeleteShader(shaders.get());
            }
            GL20.glDeleteProgram(programAux);
        }
        if (programBloom1 != 0) {
            ByteBuffer countbb = ByteBuffer.allocateDirect(4);
            ByteBuffer shadersbb = ByteBuffer.allocateDirect(8);
            IntBuffer count = countbb.asIntBuffer();
            IntBuffer shaders = shadersbb.asIntBuffer();
            GL20.glGetAttachedShaders(programBloom1, count, shaders);
            for (int i = 0; i < 2; i++) {
                GL20.glDeleteShader(shaders.get());
            }
            GL20.glDeleteProgram(programBloom1);
        }
        if (programBloom2 != 0) {
            ByteBuffer countbb = ByteBuffer.allocateDirect(4);
            ByteBuffer shadersbb = ByteBuffer.allocateDirect(8);
            IntBuffer count = countbb.asIntBuffer();
            IntBuffer shaders = shadersbb.asIntBuffer();
            GL20.glGetAttachedShaders(programBloom2, count, shaders);
            for (int i = 0; i < 2; i++) {
                GL20.glDeleteShader(shaders.get());
            }
            GL20.glDeleteProgram(programBloom2);
        }
        if (programBloom3 != 0) {
            ByteBuffer countbb = ByteBuffer.allocateDirect(4);
            ByteBuffer shadersbb = ByteBuffer.allocateDirect(8);
            IntBuffer count = countbb.asIntBuffer();
            IntBuffer shaders = shadersbb.asIntBuffer();
            GL20.glGetAttachedShaders(programBloom3, count, shaders);
            for (int i = 0; i < 2; i++) {
                GL20.glDeleteShader(shaders.get());
            }
            GL20.glDeleteProgram(programBloom3);
        }
        if (lightTex != 0) {
            GL11.glDeleteTextures(lightTex);
        }
        if (hdrTex != 0) {
            GL11.glDeleteTextures(hdrTex);
        }
        if (hdrTex2 != 0) {
            GL11.glDeleteTextures(hdrTex2);
        }
        if (hdrTex3 != 0) {
            GL11.glDeleteTextures(hdrTex3);
        }
        if (normalTex != 0) {
            GL11.glDeleteTextures(normalTex);
        }
        if (hdrBufferId != 0) {
            if (ShaderLib.useBufferCore()) {
                GL30.glDeleteFramebuffers(hdrBufferId);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glDeleteFramebuffers(hdrBufferId);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(hdrBufferId);
            }
        }
        if (hdrBuffer2Id != 0) {
            if (ShaderLib.useBufferCore()) {
                GL30.glDeleteFramebuffers(hdrBuffer2Id);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glDeleteFramebuffers(hdrBuffer2Id);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(hdrBuffer2Id);
            }
        }
        if (hdrBuffer3Id != 0) {
            if (ShaderLib.useBufferCore()) {
                GL30.glDeleteFramebuffers(hdrBuffer3Id);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glDeleteFramebuffers(hdrBuffer3Id);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(hdrBuffer3Id);
            }
        }
        if (normalBufferId != 0) {
            if (ShaderLib.useBufferCore()) {
                GL30.glDeleteFramebuffers(normalBufferId);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glDeleteFramebuffers(normalBufferId);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(normalBufferId);
            }
        }
    }

    @Override
    public RenderOrder getRenderOrder() {
        return RenderOrder.OBJECT_SPACE;
    }

    @Override
    public void initCombat() {
        Global.getCombatEngine().getCustomData().put(DATA_KEY, new LocalData());

        /*
         if (!enabled) { return; }

         StandardLight sun = new StandardLight(); sun.setType(3); sun.setDirection((Vector3f) (new Vector3f(-1f, -1f, -0.5f)).normalise());
         sun.setIntensity(2f); sun.setSpecularIntensity(3f); sun.setColor(1f, 1f, 1f); sun.makePermanent(); LightShader.addLight(sun);
         */
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void renderInScreenCoords(ViewportAPI viewport) {
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (!enabled) {
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();

        final LocalData localData = (LocalData) engine.getCustomData().get(DATA_KEY);
        final List<LightAPI> lights = localData.lights;
        final Set<BeamAPI> beams = localData.beams;
        final Map<DamagingProjectileAPI, Boolean> projectiles = localData.projectiles;

        // Add lights to new projectiles
        final List<DamagingProjectileAPI> allProjectiles = engine.getProjectiles();
        int size = allProjectiles.size();
        for (int i = 0; i < size; i++) {
            final DamagingProjectileAPI proj = allProjectiles.get(i);
            if (proj.didDamage() || proj.getElapsed() > 0.1f) {
                continue;
            }

            if (!projectiles.containsKey(proj)) {
                projectiles.put(proj, false);

                // Skip projectiles with no lighting data
                if (!LightData.projectileLightData.containsKey(proj.getProjectileSpecId())) {
                    continue;
                }

                final LightEntry data = LightData.projectileLightData.get(proj.getProjectileSpecId());

                // Attached light
                if (data.hasStandard) {
                    if ((float) Math.random() <= data.chance) {
                        final StandardLight light;
                        if (proj.getSource() != null && proj.getSource().getHullSize() == HullSize.FIGHTER &&
                                data.fighterDim) {
                            light = new StandardLight(ZERO, ZERO, ZERO, proj, data.standardIntensity *
                                                      fighterLightMultiplier, data.standardSize *
                                                      fighterLightMultiplier);
                        } else {
                            light = new StandardLight(ZERO, ZERO, new Vector2f(-data.standardOffset, 0f), proj,
                                                      data.standardIntensity, data.standardSize);
                        }
                        light.setColor(data.standardColor);
                        light.setAutoFadeOutTime(data.standardFadeout);
                        light.setHeight(standardHeight);
                        lights.add(light);
                    }
                }

                // Flash light
                if (data.hasFlash) {
                    if ((float) Math.random() <= data.chance) {
                        final StandardLight light;
                        if (Float.compare(data.flashOffset, 0f) != 0) {
                            double facing = proj.getFacing() - 180.0;
                            if (facing < 0f) {
                                facing += 360.0;
                            }
                            facing = Math.toRadians(facing);
                            tempVec.set((float) ShaderLib.fastCos(facing), (float) ShaderLib.fastSin(facing));
                            tempVec.scale(data.flashOffset);
                            Vector2f.add(proj.getLocation(), tempVec, tempVec);
                            light = new StandardLight(tempVec, ZERO, ZERO, null);
                        } else {
                            light = new StandardLight(proj.getLocation(), ZERO, ZERO, null);
                        }
                        if (proj.getSource() != null && proj.getSource().getHullSize() == HullSize.FIGHTER &&
                                data.fighterDim) {
                            light.setIntensity(data.flashIntensity * fighterLightMultiplier);
                            light.setSize(data.flashSize * fighterLightMultiplier);
                        } else {
                            light.setIntensity(data.flashIntensity);
                            light.setSize(data.flashSize);
                        }
                        light.setColor(data.flashColor);
                        light.fadeOut(data.flashFadeout);
                        light.setHeight(flashHeight);
                        lights.add(light);
                    }
                }
            }
        }

        // Handle faded or dead projectiles
        final Iterator<Map.Entry<DamagingProjectileAPI, Boolean>> iter1 = projectiles.entrySet().iterator();
        while (iter1.hasNext()) {
            final Map.Entry<DamagingProjectileAPI, Boolean> entry = iter1.next();
            final DamagingProjectileAPI proj = entry.getKey();

            if (!entry.getValue()) {
                if (proj.isFading() || (!engine.isEntityInPlay(proj) && !proj.didDamage()) ||
                        (proj instanceof MissileAPI && ((MissileAPI) proj).isFizzling())) {
                    entry.setValue(true);

                    for (LightAPI light : lights) {
                        if (light instanceof StandardLight) {
                            final StandardLight sLight = (StandardLight) light;

                            if (sLight.getAttachment() == proj) {
                                // Fadeout light
                                if (!engine.isEntityInPlay(proj)) {
                                    sLight.unattach();
                                    sLight.setLocation(proj.getLocation());
                                }
                                sLight.fadeOut(sLight.getAutoFadeOutTime());
                            }
                        }
                    }
                }
            }

            if (proj.didDamage()) {
                LightEntry data = null;
                if (LightData.projectileLightData.containsKey(proj.getProjectileSpecId())) {
                    data = LightData.projectileLightData.get(proj.getProjectileSpecId());
                }

                boolean hadAttachment = false;
                float currentFactor = 1f;

                final Iterator<LightAPI> iter2 = lights.iterator();
                while (iter2.hasNext()) {
                    final LightAPI light = iter2.next();

                    if (light instanceof StandardLight) {
                        final StandardLight sLight = (StandardLight) light;

                        if (sLight.getAttachment() == proj) {
                            hadAttachment = true;

                            if (data == null || !data.hasHit) {
                                // Fadeout light
                                sLight.unattach();
                                sLight.setLocation(proj.getLocation());
                                sLight.fadeOut(sLight.getAutoFadeOutTime());
                            } else {
                                // Prepare for hit light
                                if (!(proj instanceof MissileAPI)) {
                                    currentFactor = sLight.getIntensity() / data.standardIntensity;
                                }
                                sLight.unattach();
                                iter2.remove();
                            }
                        }
                    }
                }

                if (data != null) {
                    // Hit light
                    if (data.hasHit) {
                        if (((float) Math.random() <= data.chance || hadAttachment) && proj.getDamageTarget() != null) {
                            final StandardLight light = new StandardLight(proj.getLocation(), ZERO, ZERO, null);
                            if (proj.getSource() != null && proj.getSource().getHullSize() == HullSize.FIGHTER &&
                                    data.fighterDim) {
                                light.setIntensity(data.hitIntensity * fighterLightMultiplier * currentFactor);
                                light.setSize(data.hitSize * fighterLightMultiplier * currentFactor);
                            } else {
                                light.setIntensity(data.hitIntensity * currentFactor);
                                light.setSize(data.hitSize * currentFactor);
                            }
                            light.setColor(data.hitColor);
                            light.fadeOut(data.hitFadeout * currentFactor);
                            light.setHeight(standardHeight);
                            lights.add(light);
                        }
                    }
                }

                iter1.remove();
                continue;
            }

            if (!engine.isEntityInPlay(proj)) {
                iter1.remove();
            }
        }

        // Add lights to new beams
        final List<BeamAPI> allBeams = engine.getBeams();
        size = allBeams.size();
        for (int i = 0; i < size; i++) {
            final BeamAPI beam = allBeams.get(i);
            if (beam.getBrightness() <= 0f) {
                continue;
            }

            if (!beams.contains(beam)) {
                beams.add(beam);

                // Skip beams with no lighting data
                if (!LightData.beamLightData.containsKey(beam.getWeapon().getId())) {
                    continue;
                }

                final LightEntry data = LightData.beamLightData.get(beam.getWeapon().getId());

                // Beams look strange if the various parts can be dropped individually, so they all get lumped into the same RNG result
                if ((float) Math.random() <= data.chance) {
                    // Attached light
                    if (data.hasStandard) {
                        final StandardLight light = new StandardLight(ZERO, ZERO, ZERO, ZERO, beam);
                        if (beam.getSource() != null && beam.getSource().getHullSize() == HullSize.FIGHTER &&
                                data.fighterDim) {
                            light.setIntensity(data.standardIntensity * fighterLightMultiplier);
                            light.setSize(data.standardSize * fighterLightMultiplier);
                        } else {
                            light.setIntensity(data.standardIntensity);
                            light.setSize(data.standardSize);
                        }
                        light.setColor(data.standardColor);
                        light.setHeight(standardHeight);
                        light.makePermanent();
                        lights.add(light);
                    }

                    // Flash light
                    if (data.hasFlash) {
                        final StandardLight light = new StandardLight(ZERO, ZERO, beam, false);
                        if (beam.getSource() != null && beam.getSource().getHullSize() == HullSize.FIGHTER &&
                                data.fighterDim) {
                            light.setIntensity(data.flashIntensity * fighterLightMultiplier);
                            light.setSize(data.flashSize * fighterLightMultiplier);
                        } else {
                            light.setIntensity(data.flashIntensity);
                            light.setSize(data.flashSize);
                        }
                        light.setColor(data.flashColor);
                        light.setHeight(flashHeight);
                        light.makePermanent();
                        lights.add(light);
                    }

                    // Hit light
                    if (data.hasHit) {
                        final StandardLight light = new StandardLight(ZERO, ZERO, beam, true);
                        if (beam.getSource() != null && beam.getSource().getHullSize() == HullSize.FIGHTER &&
                                data.fighterDim) {
                            light.setIntensity(data.hitIntensity * fighterLightMultiplier);
                            light.setSize(data.hitSize * fighterLightMultiplier);
                        } else {
                            light.setIntensity(data.hitIntensity);
                            light.setSize(data.hitSize);
                        }
                        light.setColor(data.hitColor);
                        light.setHeight(standardHeight);
                        light.makePermanent();
                        lights.add(light);
                    }
                }
            }
        }

        // Handle beam lights
        final Iterator<BeamAPI> iter2 = beams.iterator();
        while (iter2.hasNext()) {
            final BeamAPI beam = iter2.next();

            if (beam.getBrightness() <= 0f) {
                final Iterator<LightAPI> iter3 = lights.iterator();
                while (iter3.hasNext()) {
                    final LightAPI light = iter3.next();

                    if (light instanceof StandardLight) {
                        final StandardLight sLight = (StandardLight) light;

                        if (sLight.getBeamLink() == beam) {
                            sLight.unattach();
                            iter3.remove();
                        }
                    }
                }

                iter2.remove();
            }
        }

        Collections.sort(lights, LIGHTSIZE);
        drawLights(viewport);
    }

    private void drawLights(ViewportAPI viewport) {
        // Exit if there is nothing to do
        if (ShaderLib.isForegroundEmpty(viewport)) {
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();

        final List<LightAPI> lights = ((LocalData) engine.getCustomData().get(DATA_KEY)).lights;

        if (normalEnabled) {
            drawNormalMaps(viewport);

            drawSurfaceMaps(viewport);
        }

        ShaderLib.beginDraw(program);

        // Load all the data into a 1-dimensional texture
        Vector2f maxCoords = null;
        Vector2f minCoords = null;
        Vector2f maxCoords2 = null;
        Vector2f minCoords2 = null;
        float maxSize = 0f;
        float maxIntensity = 0f;
        float minHeight = 0f;
        float maxHeight = 0f;

        int lightCount = 0;
        int lineLightCount = 0;
        final float[] bufferPut = new float[11];
        for (LightAPI light : lights) {
            float size = Math.max(light.getSize() * lightSizeMultiplier, 0f);
            final int type = light.getType();

            if (lineLightCount >= maxLineLights && type == 1) {
                continue;
            }

            if (type == 1) {
                if (!ShaderLib.isOnScreen(light.getLocation(), light.getLocation2(), size)) {
                    continue;
                }
            } else if (type != 3) {
                if (!ShaderLib.isOnScreen(light.getLocation(), size)) {
                    continue;
                }
            }

            if (type == 0) {
                final Vector2f coords = ShaderLib.transformScreenToUV(ShaderLib.transformWorldToScreen(
                               light.getLocation()));
                size = ShaderLib.unitsToUV(size);
                final float height = ShaderLib.unitsToUV(Math.max(light.getHeight(), light.getSize() * lightDepth));
                final float intensity = Math.max(light.getIntensity() * lightMultiplier, 0f);

                if (maxCoords == null || minCoords == null) {
                    maxCoords = new Vector2f(coords);
                    minCoords = new Vector2f(coords);
                } else {
                    if (coords.x > maxCoords.x) {
                        maxCoords.x = coords.x;
                    } else if (coords.x < minCoords.x) {
                        minCoords.x = coords.x;
                    }
                    if (coords.y > maxCoords.y) {
                        maxCoords.y = coords.y;
                    } else if (coords.y < minCoords.y) {
                        minCoords.y = coords.y;
                    }
                }
                if (maxCoords2 == null || minCoords2 == null) {
                    maxCoords2 = new Vector2f(coords);
                    minCoords2 = new Vector2f(coords);
                } else {
                    if (coords.x > maxCoords2.x) {
                        maxCoords2.x = coords.x;
                    } else if (coords.x < minCoords2.x) {
                        minCoords2.x = coords.x;
                    }
                    if (coords.y > maxCoords2.y) {
                        maxCoords2.y = coords.y;
                    } else if (coords.y < minCoords2.y) {
                        minCoords2.y = coords.y;
                    }
                }
                if (size > maxSize) {
                    maxSize = size;
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }
                if (height > maxHeight) {
                    maxHeight = height;
                } else if (height < minHeight) {
                    minHeight = height;
                }

                bufferPut[0] = coords.x;
                bufferPut[1] = coords.y;
                bufferPut[2] = light.getColor().x;
                bufferPut[3] = light.getColor().y;
                bufferPut[4] = light.getColor().z;
                bufferPut[5] = size;
                bufferPut[6] = intensity;
                bufferPut[7] = 0f;
                //bufferPut[8] = 0f;
                //bufferPut[9] = 0f;
                bufferPut[10] = height;
                dataBufferPre.put(bufferPut);
            } else if (type == 1) {
                final Vector2f coords = ShaderLib.transformScreenToUV(ShaderLib.transformWorldToScreen(
                               light.getLocation()));
                final Vector2f coords2 = ShaderLib.transformScreenToUV(ShaderLib.transformWorldToScreen(
                               light.getLocation2()));
                size = ShaderLib.unitsToUV(size);
                final float height = ShaderLib.unitsToUV(Math.max(light.getHeight(), light.getSize() * lightDepth));
                final float intensity = Math.max(light.getIntensity() * lightMultiplier, 0f);

                if (maxCoords == null || minCoords == null) {
                    maxCoords = new Vector2f(coords);
                    minCoords = new Vector2f(coords);
                } else {
                    if (coords.x > maxCoords.x) {
                        maxCoords.x = coords.x;
                    } else if (coords.x < minCoords.x) {
                        minCoords.x = coords.x;
                    }
                    if (coords.y > maxCoords.y) {
                        maxCoords.y = coords.y;
                    } else if (coords.y < minCoords.y) {
                        minCoords.y = coords.y;
                    }
                }
                if (maxCoords2 == null || minCoords2 == null) {
                    maxCoords2 = new Vector2f(coords2);
                    minCoords2 = new Vector2f(coords2);
                } else {
                    if (coords2.x > maxCoords2.x) {
                        maxCoords2.x = coords2.x;
                    } else if (coords2.x < minCoords2.x) {
                        minCoords2.x = coords2.x;
                    }
                    if (coords2.y > maxCoords2.y) {
                        maxCoords2.y = coords2.y;
                    } else if (coords2.y < minCoords2.y) {
                        minCoords2.y = coords2.y;
                    }
                }
                if (size > maxSize) {
                    maxSize = size;
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }
                if (height > maxHeight) {
                    maxHeight = height;
                } else if (height < minHeight) {
                    minHeight = height;
                }

                bufferPut[0] = coords.x;
                bufferPut[1] = coords.y;
                bufferPut[2] = light.getColor().x;
                bufferPut[3] = light.getColor().y;
                bufferPut[4] = light.getColor().z;
                bufferPut[5] = size;
                bufferPut[6] = intensity;
                bufferPut[7] = 0.33f;
                bufferPut[8] = coords2.x;
                bufferPut[9] = coords2.y;
                bufferPut[10] = height;
                dataBufferPre.put(bufferPut);
            } else if (type == 2) {
                final Vector2f coords = ShaderLib.transformScreenToUV(ShaderLib.transformWorldToScreen(
                               light.getLocation()));
                final float anglesx = (float) Math.toRadians(light.getArcStart());
                final float anglesy = (float) Math.toRadians(light.getArcEnd());
                size = ShaderLib.unitsToUV(size);
                final float height = ShaderLib.unitsToUV(Math.max(light.getHeight(), light.getSize() * lightDepth));
                final float intensity = Math.max(light.getIntensity() * lightMultiplier, 0f);

                if (maxCoords == null || minCoords == null) {
                    maxCoords = new Vector2f(coords);
                    minCoords = new Vector2f(coords);
                } else {
                    if (coords.x > maxCoords.x) {
                        maxCoords.x = coords.x;
                    } else if (coords.x < minCoords.x) {
                        minCoords.x = coords.x;
                    }
                    if (coords.y > maxCoords.y) {
                        maxCoords.y = coords.y;
                    } else if (coords.y < minCoords.y) {
                        minCoords.y = coords.y;
                    }
                }
                if (maxCoords2 == null || minCoords2 == null) {
                    maxCoords2 = new Vector2f(anglesx, anglesy);
                    minCoords2 = new Vector2f(anglesx, anglesy);
                } else {
                    if (anglesx > maxCoords2.x) {
                        maxCoords2.x = anglesx;
                    } else if (anglesx < minCoords2.x) {
                        minCoords2.x = anglesx;
                    }
                    if (anglesy > maxCoords2.y) {
                        maxCoords2.y = anglesy;
                    } else if (anglesy < minCoords2.y) {
                        minCoords2.y = anglesy;
                    }
                }
                if (size > maxSize) {
                    maxSize = size;
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }
                if (height > maxHeight) {
                    maxHeight = height;
                } else if (height < minHeight) {
                    minHeight = height;
                }

                bufferPut[0] = coords.x;
                bufferPut[1] = coords.y;
                bufferPut[2] = light.getColor().x;
                bufferPut[3] = light.getColor().y;
                bufferPut[4] = light.getColor().z;
                bufferPut[5] = size;
                bufferPut[6] = intensity;
                bufferPut[7] = 0.67f;
                bufferPut[8] = anglesx;
                bufferPut[9] = anglesy;
                bufferPut[10] = height;
                dataBufferPre.put(bufferPut);
            } else {
                final float directionx = light.getDirection().x;
                final float directiony = light.getDirection().y;
                final float directionz = light.getDirection().z;
                final float intensity = Math.max(light.getIntensity() * lightMultiplier, 0f);
                final float specularIntensity = Math.max(light.getSpecularIntensity() * lightMultiplier, 0f);

                if (maxCoords == null || minCoords == null) {
                    maxCoords = new Vector2f(directionx, directiony);
                    minCoords = new Vector2f(directionx, directiony);
                } else {
                    if (directionx > maxCoords.x) {
                        maxCoords.x = directionx;
                    } else if (directionx < minCoords.x) {
                        minCoords.x = directionx;
                    }
                    if (directiony > maxCoords.y) {
                        maxCoords.y = directiony;
                    } else if (directiony < minCoords.y) {
                        minCoords.y = directiony;
                    }
                }
                if (maxCoords2 == null || minCoords2 == null) {
                    maxCoords2 = new Vector2f(directionz, 0.5f);
                    minCoords2 = new Vector2f(directionz, 0.5f);
                } else {
                    if (directionz > maxCoords2.x) {
                        maxCoords2.x = directionz;
                    } else if (directionz < minCoords2.x) {
                        minCoords2.x = directionz;
                    }
                }
                if (specularIntensity > maxSize) {
                    maxSize = specularIntensity;
                }
                if (intensity > maxIntensity) {
                    maxIntensity = intensity;
                }

                bufferPut[0] = directionx;
                bufferPut[1] = directiony;
                bufferPut[2] = light.getColor().x;
                bufferPut[3] = light.getColor().y;
                bufferPut[4] = light.getColor().z;
                bufferPut[5] = specularIntensity;
                bufferPut[6] = intensity;
                bufferPut[7] = 1f;
                bufferPut[8] = directionz;
                //bufferPut[9] = 0f;
                //bufferPut[10] = 0f;
                dataBufferPre.put(bufferPut);
            }

            if (type == 1) {
                lineLightCount++;
            }
            lightCount++;
            if (lightCount >= Math.min(maxLights, MAX_LIGHTS)) {
                break;
            }
        }

        final Vector2f normX;
        final Vector2f normY;
        final Vector2f normX2;
        final Vector2f normY2;
        final Vector2f normH;
        final Vector2f normS;
        final Vector2f normI;

        // Simplify things if there is nothing to do
        if (lightCount <= 0 || minCoords == null || maxCoords == null || minCoords2 == null || maxCoords2 == null) {
            normX = ZERO;
            normY = ZERO;
            normX2 = ZERO;
            normY2 = ZERO;
            normH = ZERO;
            normS = ZERO;
            normI = ZERO;
            lightCount = 0;
        } else {
            // Normalize the data to fit in a [0,1] clamp
            normX = ShaderLib.getTextureDataNormalization(minCoords.x, maxCoords.x);
            normY = ShaderLib.getTextureDataNormalization(minCoords.y, maxCoords.y);
            normX2 = ShaderLib.getTextureDataNormalization(minCoords2.x, maxCoords2.x);
            normY2 = ShaderLib.getTextureDataNormalization(minCoords2.y, maxCoords2.y);
            normH = ShaderLib.getTextureDataNormalization(minHeight, maxHeight);
            normS = ShaderLib.getTextureDataNormalization(0f, maxSize);
            normI = ShaderLib.getTextureDataNormalization(0f, maxIntensity);

            dataBufferPre.flip();
            final int size = lightCount * 11;
            for (int i = 0; i < size; i++) {
                switch (i % 11) {
                    case 0:
                        dataBuffer.put((dataBufferPre.get() - normX.y) / normX.x);
                        break;
                    case 1:
                        dataBuffer.put((dataBufferPre.get() - normY.y) / normY.x);
                        break;
                    case 8:
                        dataBuffer.put((dataBufferPre.get() - normX2.y) / normX2.x);
                        break;
                    case 9:
                        dataBuffer.put((dataBufferPre.get() - normY2.y) / normY2.x);
                        break;
                    case 10:
                        dataBuffer.put((dataBufferPre.get() - normH.y) / normH.x);
                        break;
                    case 5:
                        dataBuffer.put(dataBufferPre.get() / normS.x);
                        break;
                    case 6:
                        dataBuffer.put(dataBufferPre.get() / normI.x);
                        break;
                    default:
                        dataBuffer.put(dataBufferPre.get());
                }
            }
        }

        dataBuffer.flip();

        // Bind the data to our texture
        GL11.glBindTexture(GL11.GL_TEXTURE_1D, lightTex);
        GL11.glTexSubImage1D(GL11.GL_TEXTURE_1D, 0, 0, dataBuffer.remaining(), GL11.GL_RED, GL11.GL_FLOAT, dataBuffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        GL20.glUniform1i(index[4], lightCount); // size
        GL20.glUniform4f(index[5], normX.x, normX.y, normY.x, normY.y); // norm1
        GL20.glUniform4f(index[6], normS.x, normI.x, normH.x, normH.y); // norm2
        GL20.glUniform4f(index[7], normX2.x, normX2.y, normY2.x, normY2.y); // norm3

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getForegroundTexture(viewport));
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_1D, lightTex);
        if (normalEnabled) {
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalTex);
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getAuxiliaryBufferTexture());
        }

        if (!validated) {
            validated = true;

            // This stuff here is for AMD compatability, normally it would be way back in the shader loader
            GL20.glValidateProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(program));
                ShaderLib.exitDraw();
                dataBuffer.clear();
                dataBufferPre.clear();
                enabled = false;
                return;
            }
        }

        if (bloomEnabled) {
            ShaderLib.copyScreen(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

            if (ShaderLib.useBufferCore()) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hdrBufferId);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, hdrBufferId);
            } else {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, hdrBufferId);
            }

            GL11.glColorMask(true, true, true, false);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glDisable(GL11.GL_BLEND);
            ShaderLib.drawScreenQuad(1f);

            ShaderLib.exitDraw();

            ShaderLib.beginDraw(programBloom1);

            if (ShaderLib.useBufferCore()) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hdrBuffer2Id);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, hdrBuffer2Id);
            } else {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, hdrBuffer2Id);
            }

            GL11.glColorMask(true, true, true, false);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex);

            if (!validatedBloom1) {
                validatedBloom1 = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programBloom1);
                if (GL20.glGetProgrami(programBloom1, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programBloom1));
                    if (ShaderLib.useBufferCore()) {
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    } else if (ShaderLib.useBufferARB()) {
                        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
                    } else {
                        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    }
                    ShaderLib.exitDraw();
                    dataBuffer.clear();
                    dataBufferPre.clear();
                    bloomEnabled = false;
                    return;
                }
            }

            GL11.glDisable(GL11.GL_BLEND);
            ShaderLib.drawScreenQuad(1f / (float) Math.pow(2, bloomMips - 1));

            ShaderLib.exitDraw();

            ShaderLib.beginDraw(programBloom2);

            if (ShaderLib.useBufferCore()) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hdrBuffer3Id);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, hdrBuffer3Id);
            } else {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, hdrBuffer3Id);
            }

            GL11.glColorMask(true, true, true, false);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex2);

            if (!validatedBloom2) {
                validatedBloom2 = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programBloom2);
                if (GL20.glGetProgrami(programBloom2, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programBloom2));
                    if (ShaderLib.useBufferCore()) {
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    } else if (ShaderLib.useBufferARB()) {
                        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
                    } else {
                        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    }
                    ShaderLib.exitDraw();
                    dataBuffer.clear();
                    dataBufferPre.clear();
                    bloomEnabled = false;
                    return;
                }
            }

            GL11.glDisable(GL11.GL_BLEND);
            ShaderLib.drawScreenQuad(1f / (float) Math.pow(2, bloomMips - 1));

            ShaderLib.exitDraw();

            ShaderLib.beginDraw(programBloom3);

            if (ShaderLib.useBufferCore()) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
            } else {
                EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hdrTex3);

            if (!validatedBloom3) {
                validatedBloom3 = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programBloom3);
                if (GL20.glGetProgrami(programBloom3, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programBloom3));
                    ShaderLib.exitDraw();
                    dataBuffer.clear();
                    dataBufferPre.clear();
                    bloomEnabled = false;
                    return;
                }
            }

            GL11.glDisable(GL11.GL_BLEND);
            ShaderLib.drawScreenQuad(1f);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
            ShaderLib.screenDraw(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);
        }

        ShaderLib.exitDraw();

        dataBuffer.clear();
        dataBufferPre.clear();
    }

    private void drawNormalMaps(ViewportAPI viewport) {
        GL20.glUseProgram(programAux);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, normalBufferId);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, normalBufferId);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, normalBufferId);
        }

        GL11.glViewport(0, 0, (int) (Global.getSettings().getScreenWidth() * Display.getPixelScaleFactor()),
                        (int) (Global.getSettings().getScreenHeight() *
                               Display.getPixelScaleFactor()));

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(), viewport.getLLY(),
                     viewport.getLLY() + viewport.getVisibleHeight(),
                     -2000,
                     2000);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glColorMask(true, true, true, true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        final List<CombatEntityAPI> asteroids = Global.getCombatEngine().getAsteroids();
        int size = asteroids.size();
        for (int i = 0; i < size; i++) {
            final CombatEntityAPI asteroid = asteroids.get(i);
            final Vector2f asteroidLocation = asteroid.getLocation();

            if (!ShaderLib.isOnScreen(asteroidLocation, 100f)) { // You can't trust asteroid collision radius.
                continue;
            }

            final CombatAsteroidAPI assteroid = (CombatAsteroidAPI) asteroid;

            final SpriteAPI asteroidSprite = assteroid.getSpriteAPI();
            String asteroidType = ShaderModPlugin.ASTEROID_MAP.get(asteroidSprite.getTextureId());
            if (asteroidType == null) {
                asteroidType = "nil";
            }

            final boolean hasNormal;
            final TextureEntry entry = TextureData.getTextureData(asteroidType, TextureDataType.NORMAL_MAP,
                                                                  ObjectType.ASTEROID, 0);
            final SpriteAPI sprite;
            float depth = 1f;
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(asteroidSprite.getAngle());
                sprite.setSize(asteroidSprite.getWidth(), asteroidSprite.getHeight());
                sprite.setCenter(asteroidSprite.getCenterX(), asteroidSprite.getCenterY());
                sprite.setAlphaMult(asteroidSprite.getAlphaMult());
                depth = entry.magnitude;
                hasNormal = true;
            } else {
                sprite = asteroidSprite;
                hasNormal = false;
            }

            float uniformAngle = asteroidSprite.getAngle();
            float uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
            if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                lastAngle = uniformAngle;
                lastFlatness = uniformFlatness;
                GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
            }

            if (!validatedAux) {
                validatedAux = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programAux);
                if (GL20.glGetProgrami(programAux, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programAux));
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    if (ShaderLib.useBufferCore()) {
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    } else if (ShaderLib.useBufferARB()) {
                        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
                    } else {
                        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    }
                    GL11.glPopAttrib();

                    GL11.glViewport(0, 0, (int) (Global.getSettings().getScreenWidth() * Display.getPixelScaleFactor()),
                                    (int) (Global.getSettings().getScreenHeight() * Display.getPixelScaleFactor()));
                    normalEnabled = false;
                    enabled = false;
                    return;
                }
            }

            sprite.renderAtCenter(asteroidLocation.x, asteroidLocation.y);
        }

        final List<ShipAPI> ships = Global.getCombatEngine().getShips();
        Collections.sort(ships, ShaderLib.SHIP_DRAW_ORDER);
        size = ships.size();
        for (int i = 0; i < size; i++) {
            final ShipAPI ship = ships.get(i);
            Vector2f shipLocation = ship.getLocation();

            if (!ShaderLib.isOnScreen(shipLocation, 1.25f * ship.getCollisionRadius())) {
                continue;
            }

            boolean hasNormal;
            TextureEntry entry = TextureData.getTextureData(ship.getHullSpec().getHullId(), TextureDataType.NORMAL_MAP,
                                                            ObjectType.SHIP, 0);
            if (entry == null) {
                entry = TextureData.getTextureData(ship.getHullSpec().getDParentHullId(), TextureDataType.NORMAL_MAP,
                                                   ObjectType.SHIP, 0);
            }
            if (entry == null) {
                entry = TextureData.getTextureData(ship.getHullSpec().getBaseHullId(), TextureDataType.NORMAL_MAP,
                                                   ObjectType.SHIP, 0);
            }
            SpriteAPI sprite;
            float depth = 1f;
            SpriteAPI originalSprite = ship.getSpriteAPI();
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(originalSprite.getAngle());
                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                sprite.setAlphaMult(ship.getCombinedAlphaMult());
                depth = entry.magnitude;
                hasNormal = true;
            } else {
                sprite = originalSprite;
                hasNormal = false;
            }

            float uniformAngle = originalSprite.getAngle();
            float uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
            if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                lastAngle = uniformAngle;
                lastFlatness = uniformFlatness;
                GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
            }

            if (!validatedAux) {
                validatedAux = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programAux);
                if (GL20.glGetProgrami(programAux, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programAux));
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    if (ShaderLib.useBufferCore()) {
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    } else if (ShaderLib.useBufferARB()) {
                        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
                    } else {
                        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    }
                    GL11.glPopAttrib();

                    GL11.glViewport(0, 0, (int) (Global.getSettings().getScreenWidth() * Display.getPixelScaleFactor()),
                                    (int) (Global.getSettings().getScreenHeight() * Display.getPixelScaleFactor()));
                    normalEnabled = false;
                    enabled = false;
                    return;
                }
            }

            BoundsAPI bounds = ship.getVisualBounds();
            if (bounds != null) {
                bounds.update(ship.getLocation(), ship.getFacing());

                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColorMask(false, false, false, false);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 16, 0xFF); // Set stencil to 16
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                GL11.glStencilMask(0xFF); // Write to stencil buffer
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT); // Clear stencil buffer

                Tessellate.render(bounds, 1f, 1f, 1f, ship.getId());

                GL11.glColorMask(true, true, true, true);
                GL11.glStencilFunc(GL11.GL_EQUAL, 16, 0xFF); // Pass test if stencil value is 16
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GL11.glStencilMask(0x00); // Don't write anything to stencil buffer

                sprite.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                sprite.renderAtCenter(shipLocation.x, shipLocation.y);

                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF); // Pass test always
            } else {
                sprite.renderAtCenter(shipLocation.x, shipLocation.y);
            }

            final Vector2f renderOffset = VectorUtils.rotate(ship.getRenderOffset(), ship.getFacing(), new Vector2f());

            final List<WeaponAPI> weapons = ship.getAllWeapons();
            final List<WeaponSlotAPI> emptySlots = ship.getHullSpec().getAllWeaponSlotsCopy();
            final int weaponSize = weapons.size();
            for (int j = 0; j < weaponSize; j++) {
                final WeaponAPI weapon = weapons.get(j);
                if (!emptySlots.remove(weapon.getSlot())) {
                    for (Iterator<WeaponSlotAPI> iter = emptySlots.iterator(); iter.hasNext();) {
                        final WeaponSlotAPI slot = iter.next();
                        final Vector2f slotLocation = Vector2f.add(slot.computePosition(ship), renderOffset,
                                                                   new Vector2f());
                        final Vector2f weaponLocation = Vector2f.add(weapon.getLocation(), renderOffset, new Vector2f());
                        if (MathUtils.getDistance(slotLocation, weaponLocation) <= 1f) {
                            iter.remove();
                            break;
                        }
                    }
                }
            }

            if (bounds == null) {
                final int slotSize = emptySlots.size();
                for (int j = 0; j < slotSize; j++) {
                    final WeaponSlotAPI slot = emptySlots.get(j);
                    if (slot.isDecorative() || slot.isHidden() || slot.isSystemSlot() ||
                            (slot.getWeaponType() == WeaponType.LAUNCH_BAY) || slot.isStationModule() ||
                            slot.isBuiltIn()) {
                        continue;
                    }
                    final Vector2f slotLocation = Vector2f.add(slot.computePosition(ship), renderOffset, new Vector2f());
                    switch (slot.getSlotSize()) {
                        default:
                        case SMALL:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_COVER_SMALL, 0);
                                originalSprite = ship.getSmallHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_COVER_SMALL, 0);
                                originalSprite = ship.getSmallTurretCover();
                            }
                            break;
                        case MEDIUM:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_COVER_MEDIUM, 0);
                                originalSprite = ship.getMediumHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_COVER_MEDIUM, 0);
                                originalSprite = ship.getMediumTurretCover();
                            }
                            break;
                        case LARGE:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_COVER_LARGE, 0);
                                originalSprite = ship.getLargeHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_COVER_LARGE, 0);
                                originalSprite = ship.getLargeTurretCover();
                            }
                            break;
                    }
                    if (originalSprite == null || originalSprite.getTextureId() == 0) {
                        continue;
                    }

                    if (entry != null) {
                        sprite = entry.sprite;
                        sprite.setAngle(slot.getAngle() + ship.getFacing() - 90f);
                        sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                        sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                        sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                        depth = entry.magnitude;
                        hasNormal = true;
                    } else {
                        sprite = originalSprite;
                        sprite.setAngle(slot.getAngle() + ship.getFacing() - 90f);
                        hasNormal = false;
                    }

                    uniformAngle = originalSprite.getAngle();
                    uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                    if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                        lastAngle = uniformAngle;
                        lastFlatness = uniformFlatness;
                        GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                    }

                    sprite.renderAtCenter(slotLocation.x, slotLocation.y);
                }
            }

            for (int j = 0; j < weaponSize; j++) {
                final WeaponAPI weapon = weapons.get(j);
                if (!weapon.getSlot().isHidden()) {
                    final Vector2f weaponLocation = Vector2f.add(weapon.getLocation(), renderOffset, new Vector2f());

                    if (weapon.getUnderSpriteAPI() != null) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_UNDER,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_UNDER, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_UNDER,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_UNDER, 0);
                            }
                        }
                        originalSprite = weapon.getUnderSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setAngle(originalSprite.getAngle());
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                            depth = entry.magnitude;
                            hasNormal = true;
                        } else {
                            sprite = originalSprite;
                            hasNormal = false;
                        }

                        uniformAngle = originalSprite.getAngle();
                        uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                        if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                            lastAngle = uniformAngle;
                            lastFlatness = uniformFlatness;
                            GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                        }

                        sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);
                    }

                    if (weapon.getBarrelSpriteAPI() != null && weapon.isRenderBarrelBelow()) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_BARREL, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_BARREL, 0);
                            }
                        }
                        originalSprite = weapon.getBarrelSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            depth = entry.magnitude;
                            hasNormal = true;
                        } else {
                            sprite = originalSprite;
                            hasNormal = false;
                        }

                        uniformAngle = originalSprite.getAngle();
                        uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                        if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                            lastAngle = uniformAngle;
                            lastFlatness = uniformFlatness;
                            GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                        }

                        weapon.renderBarrel(sprite, weaponLocation,
                                            Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                    }

                    if (weapon.getSprite() != null) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET, 0);
                            }
                        }
                        originalSprite = weapon.getSprite();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setAngle(originalSprite.getAngle());
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                            depth = entry.magnitude;
                            hasNormal = true;
                        } else {
                            sprite = originalSprite;
                            hasNormal = false;
                        }

                        uniformAngle = originalSprite.getAngle();
                        uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                        if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                            lastAngle = uniformAngle;
                            lastFlatness = uniformFlatness;
                            GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                        }

                        sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);
                    }

                    if (weapon.getBarrelSpriteAPI() != null && !weapon.isRenderBarrelBelow()) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.HARDPOINT_BARREL, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.NORMAL_MAP,
                                                                   ObjectType.TURRET_BARREL, 0);
                            }
                        }
                        originalSprite = weapon.getBarrelSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            depth = entry.magnitude;
                            hasNormal = true;
                        } else {
                            sprite = originalSprite;
                            hasNormal = false;
                        }

                        uniformAngle = originalSprite.getAngle();
                        uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                        if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                            lastAngle = uniformAngle;
                            lastFlatness = uniformFlatness;
                            GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                        }

                        weapon.renderBarrel(sprite, weaponLocation,
                                            Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                    }

                    if (weapon.getMissileRenderData() != null && !weapon.getMissileRenderData().isEmpty() &&
                            (!weapon.usesAmmo() || weapon.getAmmo() > 0)) {
                        final List<MissileRenderDataAPI> msls = weapon.getMissileRenderData();
                        final int mslSize = msls.size();
                        for (int k = 0; k < mslSize; k++) {
                            final MissileRenderDataAPI msl = msls.get(k);
                            if (msl.getMissileSpecId() == null) {
                                continue;
                            }

                            final Vector2f missileLocation = msl.getMissileCenterLocation();

                            entry = TextureData.getTextureData(msl.getMissileSpecId(), TextureDataType.NORMAL_MAP,
                                                               ObjectType.MISSILE, 0);
                            originalSprite = msl.getSprite();
                            if (entry != null) {
                                sprite = entry.sprite;
                                sprite.setAngle(msl.getMissileFacing() - 90f);
                                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                                sprite.setAlphaMult(
                                        Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()) *
                                        msl.getBrightness());
                                depth = entry.magnitude;
                                hasNormal = true;
                            } else {
                                sprite = originalSprite;
                                hasNormal = false;
                            }

                            uniformAngle = msl.getMissileFacing() - 90f;
                            uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
                            if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                                lastAngle = uniformAngle;
                                lastFlatness = uniformFlatness;
                                GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
                            }

                            sprite.renderAtCenter(missileLocation.x + renderOffset.x,
                                                  missileLocation.y + renderOffset.y);
                        }
                    }
                }
            }
        }

        final List<MissileAPI> missiles = Global.getCombatEngine().getMissiles();
        size = missiles.size();
        for (int i = 0; i < size; i++) {
            final MissileAPI missile = missiles.get(i);
            final Vector2f missileLocation = missile.getLocation();

            if (!ShaderLib.isOnScreen(missileLocation, 1.25f * missile.getCollisionRadius())) {
                continue;
            }

            if (missile.getProjectileSpecId() == null) {
                continue;
            }

            final boolean hasNormal;
            final TextureEntry entry = TextureData.getTextureData(missile.getProjectileSpecId(),
                                                                  TextureDataType.NORMAL_MAP, ObjectType.MISSILE, 0);
            final SpriteAPI sprite;
            float depth = 1f;
            final SpriteAPI originalSprite = missile.getSpriteAPI();
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(originalSprite.getAngle());
                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                sprite.setAlphaMult(originalSprite.getAlphaMult());
                depth = entry.magnitude;
                hasNormal = true;
            } else {
                sprite = originalSprite;
                hasNormal = false;
            }

            float uniformAngle = originalSprite.getAngle();
            float uniformFlatness = hasNormal ? 1f - ((1f - flatness) * depth) : 2f;
            if (uniformAngle != lastAngle || uniformFlatness != lastFlatness) {
                lastAngle = uniformAngle;
                lastFlatness = uniformFlatness;
                GL20.glUniform2f(indexAux[1], uniformAngle, uniformFlatness); // data
            }

            if (!validatedAux) {
                validatedAux = true;

                // This stuff here is for AMD compatability, normally it would be way back in the shader loader
                GL20.glValidateProgram(programAux);
                if (GL20.glGetProgrami(programAux, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                    Global.getLogger(ShaderLib.class).log(Level.ERROR, ShaderLib.getProgramLogInfo(programAux));
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPopMatrix();
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                    if (ShaderLib.useBufferCore()) {
                        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                    } else if (ShaderLib.useBufferARB()) {
                        ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
                    } else {
                        EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
                    }
                    GL11.glPopAttrib();

                    GL11.glViewport(0, 0, (int) (Global.getSettings().getScreenWidth() * Display.getPixelScaleFactor()),
                                    (int) (Global.getSettings().getScreenHeight() * Display.getPixelScaleFactor()));
                    normalEnabled = false;
                    enabled = false;
                    return;
                }
            }

            sprite.renderAtCenter(missileLocation.x, missileLocation.y);
        }

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }
        GL11.glPopAttrib();
    }

    private void drawSurfaceMaps(ViewportAPI viewport) {
        GL20.glUseProgram(0);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ShaderLib.getAuxiliaryBufferId());
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, ShaderLib.getAuxiliaryBufferId());
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                                                      ShaderLib.getAuxiliaryBufferId());
        }

        GL11.glViewport(0, 0, (int) (Global.getSettings().getScreenWidth() * Display.getPixelScaleFactor()),
                        (int) (Global.getSettings().getScreenHeight() *
                               Display.getPixelScaleFactor()));

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(), viewport.getLLY(),
                     viewport.getLLY() + viewport.getVisibleHeight(),
                     -2000, 2000);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glColorMask(true, true, true, true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        final List<CombatEntityAPI> asteroids = Global.getCombatEngine().getAsteroids();
        int size = asteroids.size();
        for (int i = 0; i < size; i++) {
            final CombatEntityAPI asteroid = asteroids.get(i);
            final Vector2f asteroidLocation = asteroid.getLocation();

            if (!ShaderLib.isOnScreen(asteroidLocation, 100f)) { // You can't trust asteroid collision radius.
                continue;
            }

            final CombatAsteroidAPI assteroid = (CombatAsteroidAPI) asteroid;

            final SpriteAPI asteroidSprite = assteroid.getSpriteAPI();
            String asteroidType = ShaderModPlugin.ASTEROID_MAP.get(asteroidSprite.getTextureId());
            if (asteroidType == null) {
                asteroidType = "nil";
            }

            final TextureEntry entry = TextureData.getTextureData(asteroidType, TextureDataType.SURFACE_MAP,
                                                                  ObjectType.ASTEROID, 0);
            final SpriteAPI sprite;
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(asteroidSprite.getAngle());
                sprite.setSize(asteroidSprite.getWidth(), asteroidSprite.getHeight());
                sprite.setCenter(asteroidSprite.getCenterX(), asteroidSprite.getCenterY());
                sprite.setAlphaMult(asteroidSprite.getAlphaMult());
                sprite.renderAtCenter(asteroidLocation.x, asteroidLocation.y);
            } else {
                sprite = asteroidSprite;
                final Color originalColor = sprite.getColor();

                sprite.setColor(Color.BLACK);
                sprite.renderAtCenter(asteroidLocation.x, asteroidLocation.y);

                sprite.setColor(originalColor);
            }
        }

        final List<ShipAPI> ships = Global.getCombatEngine().getShips();
        Collections.sort(ships, ShaderLib.SHIP_DRAW_ORDER);
        size = ships.size();
        for (int i = 0; i < size; i++) {
            final ShipAPI ship = ships.get(i);
            final Vector2f shipLocation = ship.getLocation();

            if (!ShaderLib.isOnScreen(shipLocation, 1.25f * ship.getCollisionRadius())) {
                continue;
            }

            TextureEntry entry = TextureData.getTextureData(ship.getHullSpec().getHullId(), TextureDataType.SURFACE_MAP,
                                                            ObjectType.SHIP, 0);
            if (entry == null) {
                entry = TextureData.getTextureData(ship.getHullSpec().getDParentHullId(), TextureDataType.SURFACE_MAP,
                                                   ObjectType.SHIP, 0);
            }
            if (entry == null) {
                entry = TextureData.getTextureData(ship.getHullSpec().getBaseHullId(), TextureDataType.SURFACE_MAP,
                                                   ObjectType.SHIP, 0);
            }
            SpriteAPI sprite;
            SpriteAPI originalSprite = ship.getSpriteAPI();
            Color originalColor = null;
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(originalSprite.getAngle());
                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                sprite.setAlphaMult(ship.getCombinedAlphaMult());
                if (ship.isHulk()) {
                    sprite.setColor(deadSurface);
                }
            } else {
                sprite = originalSprite;
                originalColor = sprite.getColor();

                sprite.setColor(Color.BLACK);
            }

            BoundsAPI bounds = ship.getVisualBounds();
            if (bounds != null) {
                bounds.update(ship.getLocation(), ship.getFacing());

                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColorMask(false, false, false, false);
                GL11.glStencilFunc(GL11.GL_ALWAYS, 16, 0xFF); // Set stencil to 16
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                GL11.glStencilMask(0xFF); // Write to stencil buffer
                GL11.glClearStencil(0);
                GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT); // Clear stencil buffer

                Tessellate.render(bounds, 1f, 1f, 1f, ship.getId());

                GL11.glColorMask(true, true, true, true);
                GL11.glStencilFunc(GL11.GL_EQUAL, 16, 0xFF); // Pass test if stencil value is 16
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                GL11.glStencilMask(0x00); // Don't write anything to stencil buffer

                sprite.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                sprite.renderAtCenter(shipLocation.x, shipLocation.y);

                GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF); // Pass test always
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            } else {
                sprite.renderAtCenter(shipLocation.x, shipLocation.y);
            }

            if (entry == null) {
                sprite.setColor(originalColor);
            }

            final Vector2f renderOffset = VectorUtils.rotate(ship.getRenderOffset(), ship.getFacing(), new Vector2f());

            final List<WeaponAPI> weapons = ship.getAllWeapons();
            final List<WeaponSlotAPI> emptySlots = ship.getHullSpec().getAllWeaponSlotsCopy();
            final int weaponSize = weapons.size();
            for (int j = 0; j < weaponSize; j++) {
                final WeaponAPI weapon = weapons.get(j);
                if (!emptySlots.remove(weapon.getSlot())) {
                    for (Iterator<WeaponSlotAPI> iter = emptySlots.iterator(); iter.hasNext();) {
                        final WeaponSlotAPI slot = iter.next();
                        final Vector2f slotLocation = Vector2f.add(slot.computePosition(ship), renderOffset,
                                                                   new Vector2f());
                        final Vector2f weaponLocation = Vector2f.add(weapon.getLocation(), renderOffset, new Vector2f());
                        if (MathUtils.getDistance(slotLocation, weaponLocation) <= 1f) {
                            iter.remove();
                            break;
                        }
                    }
                }
            }

            if (bounds == null) {
                final int slotSize = emptySlots.size();
                for (int j = 0; j < slotSize; j++) {
                    final WeaponSlotAPI slot = emptySlots.get(j);
                    if (slot.isDecorative() || slot.isHidden() || slot.isSystemSlot() ||
                            (slot.getWeaponType() == WeaponType.LAUNCH_BAY) || slot.isStationModule() ||
                            slot.isBuiltIn()) {
                        continue;
                    }
                    final Vector2f slotLocation = Vector2f.add(slot.computePosition(ship), renderOffset, new Vector2f());
                    switch (slot.getSlotSize()) {
                        default:
                        case SMALL:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_COVER_SMALL, 0);
                                originalSprite = ship.getSmallHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_COVER_SMALL, 0);
                                originalSprite = ship.getSmallTurretCover();
                            }
                            break;
                        case MEDIUM:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_COVER_MEDIUM, 0);
                                originalSprite = ship.getMediumHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_COVER_MEDIUM, 0);
                                originalSprite = ship.getMediumTurretCover();
                            }
                            break;
                        case LARGE:
                            if (slot.isHardpoint()) {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_COVER_LARGE, 0);
                                originalSprite = ship.getLargeHardpointCover();
                            } else {
                                entry = TextureData.getTextureData(ship.getHullStyleId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_COVER_LARGE, 0);
                                originalSprite = ship.getLargeTurretCover();
                            }
                            break;
                    }
                    if (originalSprite == null || originalSprite.getTextureId() == 0) {
                        continue;
                    }

                    if (entry != null) {
                        sprite = entry.sprite;
                        sprite.setAngle(slot.getAngle() + ship.getFacing() - 90f);
                        sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                        sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                        sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                        if (ship.isHulk()) {
                            sprite.setColor(deadSurface);
                        }
                        sprite.renderAtCenter(slotLocation.x, slotLocation.y);
                    } else {
                        sprite = originalSprite;
                        sprite.setAngle(slot.getAngle() + ship.getFacing() - 90f);
                        originalColor = sprite.getColor();

                        sprite.setColor(Color.BLACK);
                        sprite.renderAtCenter(slotLocation.x, slotLocation.y);

                        sprite.setColor(originalColor);
                    }
                }
            }

            for (int j = 0; j < weaponSize; j++) {
                final WeaponAPI weapon = weapons.get(j);
                if (!weapon.getSlot().isHidden()) {
                    final Vector2f weaponLocation = Vector2f.add(weapon.getLocation(), renderOffset, new Vector2f());

                    if (weapon.getUnderSpriteAPI() != null) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_UNDER,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_UNDER, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_UNDER,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_UNDER, 0);
                            }
                        }
                        originalSprite = weapon.getUnderSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setAngle(originalSprite.getAngle());
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                            if (ship.isHulk()) {
                                sprite.setColor(deadSurface);
                            }
                            sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);
                        } else {
                            sprite = originalSprite;
                            originalColor = sprite.getColor();

                            sprite.setColor(Color.BLACK);
                            sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);

                            sprite.setColor(originalColor);
                        }
                    }

                    if (weapon.getBarrelSpriteAPI() != null && weapon.isRenderBarrelBelow()) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_BARREL, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_BARREL, 0);
                            }
                        }
                        originalSprite = weapon.getBarrelSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            if (ship.isHulk()) {
                                sprite.setColor(deadSurface);
                            }
                            weapon.renderBarrel(sprite, weaponLocation,
                                                Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                        } else {
                            sprite = originalSprite;
                            originalColor = sprite.getColor();

                            sprite.setColor(Color.BLACK);
                            weapon.renderBarrel(sprite, weaponLocation,
                                                Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));

                            sprite.setColor(originalColor);
                        }
                    }

                    if (weapon.getSprite() != null) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET, 0);
                            }
                        }
                        originalSprite = weapon.getSprite();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setAngle(originalSprite.getAngle());
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            sprite.setAlphaMult(Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                            if (ship.isHulk()) {
                                sprite.setColor(deadSurface);
                            }
                            sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);
                        } else {
                            sprite = originalSprite;
                            originalColor = sprite.getColor();

                            sprite.setColor(Color.BLACK);
                            sprite.renderAtCenter(weaponLocation.x, weaponLocation.y);

                            sprite.setColor(originalColor);
                        }
                    }

                    if (weapon.getBarrelSpriteAPI() != null && !weapon.isRenderBarrelBelow()) {
                        if (weapon.getSlot().isHardpoint()) {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.HARDPOINT_BARREL, 0);
                            }
                        } else {
                            if (weapon.getAnimation() != null) {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_BARREL,
                                                                   weapon.getAnimation().getFrame());
                            } else {
                                entry = TextureData.getTextureData(weapon.getId(), TextureDataType.SURFACE_MAP,
                                                                   ObjectType.TURRET_BARREL, 0);
                            }
                        }
                        originalSprite = weapon.getBarrelSpriteAPI();
                        if (entry != null) {
                            sprite = entry.sprite;
                            sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                            sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                            if (ship.isHulk()) {
                                sprite.setColor(deadSurface);
                            }
                            weapon.renderBarrel(sprite, weaponLocation,
                                                Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));
                        } else {
                            sprite = originalSprite;
                            originalColor = sprite.getColor();

                            sprite.setColor(Color.BLACK);
                            weapon.renderBarrel(sprite, weaponLocation,
                                                Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()));

                            sprite.setColor(originalColor);
                        }
                    }

                    if (weapon.getMissileRenderData() != null && !weapon.getMissileRenderData().isEmpty() &&
                            (!weapon.usesAmmo() || weapon.getAmmo() > 0)) {
                        final List<MissileRenderDataAPI> msls = weapon.getMissileRenderData();
                        final int mslSize = msls.size();
                        for (int k = 0; k < mslSize; k++) {
                            final MissileRenderDataAPI msl = msls.get(k);
                            if (msl.getMissileSpecId() == null) {
                                continue;
                            }

                            final Vector2f missileLocation = msl.getMissileCenterLocation();

                            entry = TextureData.getTextureData(msl.getMissileSpecId(), TextureDataType.SURFACE_MAP,
                                                               ObjectType.MISSILE, 0);
                            originalSprite = msl.getSprite();
                            if (entry != null) {
                                sprite = entry.sprite;
                                sprite.setAngle(msl.getMissileFacing() - 90f);
                                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                                sprite.setAlphaMult(
                                        Math.min(ship.getCombinedAlphaMult(), originalSprite.getAlphaMult()) *
                                        msl.getBrightness());
                                if (ship.isHulk()) {
                                    sprite.setColor(deadSurface);
                                }
                                sprite.renderAtCenter(missileLocation.x + renderOffset.x,
                                                      missileLocation.y + renderOffset.y);
                            } else {
                                sprite = originalSprite;
                                originalColor = sprite.getColor();

                                sprite.setColor(Color.BLACK);
                                sprite.renderAtCenter(missileLocation.x + renderOffset.x,
                                                      missileLocation.y + renderOffset.y);

                                sprite.setColor(originalColor);
                            }
                        }
                    }
                }
            }
        }

        final List<MissileAPI> missiles = Global.getCombatEngine().getMissiles();
        size = missiles.size();
        for (int i = 0; i < size; i++) {
            final MissileAPI missile = missiles.get(i);
            final Vector2f missileLocation = missile.getLocation();

            if (!ShaderLib.isOnScreen(missileLocation, 1.25f * missile.getCollisionRadius())) {
                continue;
            }

            if (missile.getProjectileSpecId() == null) {
                continue;
            }

            final TextureEntry entry = TextureData.getTextureData(missile.getProjectileSpecId(),
                                                                  TextureDataType.SURFACE_MAP, ObjectType.MISSILE, 0);
            final SpriteAPI sprite;
            final SpriteAPI originalSprite = missile.getSpriteAPI();
            if (entry != null) {
                sprite = entry.sprite;
                sprite.setAngle(originalSprite.getAngle());
                sprite.setSize(originalSprite.getWidth(), originalSprite.getHeight());
                sprite.setCenter(originalSprite.getCenterX(), originalSprite.getCenterY());
                sprite.setAlphaMult(originalSprite.getAlphaMult());
                sprite.renderAtCenter(missileLocation.x, missileLocation.y);
            } else {
                sprite = originalSprite;
                final Color originalColor = sprite.getColor();

                sprite.setColor(Color.BLACK);
                sprite.renderAtCenter(missileLocation.x, missileLocation.y);

                sprite.setColor(originalColor);
            }
        }

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }
        GL11.glPopAttrib();
    }

    private void loadSettings() throws IOException, JSONException {
        final JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        enabled = settings.getBoolean("enableLights");
        maxLights = settings.getInt("maximumLights");
        maxLineLights = settings.getInt("maximumLineLights");
        lightMultiplier = (float) settings.getDouble("intensityScale");
        lightSizeMultiplier = (float) settings.getDouble("sizeScale");
        fighterLightMultiplier = (float) settings.getDouble("fighterBrightnessScale");
        bloomEnabled = settings.getBoolean("enableBloom");
        bloomQuality = Math.max(Math.min(settings.getInt("bloomQuality"), 5), 1);
        bloomMips = Math.max(Math.min(settings.getInt("bloomMips"), 5), 1);
        bloomScale = (float) settings.getDouble("bloomScale");
        bloomIntensity = (float) settings.getDouble("bloomIntensity");
        normalEnabled = settings.getBoolean("enableNormal");
        specularMultiplier = (float) settings.getDouble("specularIntensity");
        specularHardness = (float) settings.getDouble("specularHardness");
        flatness = (float) settings.getDouble("normalFlatness");
        lightDepth = (float) settings.getDouble("lightDepth");
        flashHeight = (float) settings.getDouble("weaponFlashHeight");
        standardHeight = (float) settings.getDouble("weaponLightHeight");
    }

    private static final class LocalData {

        final Set<BeamAPI> beams = new LinkedHashSet<>(200);
        final List<LightAPI> lights = new LinkedList<>();
        final Map<DamagingProjectileAPI, Boolean> projectiles = new LinkedHashMap<>(2000);
    }
}
