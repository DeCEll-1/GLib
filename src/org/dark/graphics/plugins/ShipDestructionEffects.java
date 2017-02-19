package org.dark.graphics.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Level;
import org.dark.graphics.util.AnamorphicFlare;
import org.dark.graphics.util.ShipColors;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.dark.shaders.util.ShaderLib;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;

import static org.dark.graphics.util.ShipColors.EXPLOSION_COLORS;

public class ShipDestructionEffects extends BaseEveryFrameCombatPlugin {

    private static final Map<String, Float> BOSS_SHIPS = new HashMap<>(9);

    private static final Color COLOR_BLACK1 = new Color(0, 0, 0, 0);
    private static final Color COLOR_BLACK2 = new Color(0, 0, 0, 50);
    private static final Color COLOR_MAIN = new Color(255, 160, 30);
    private static final Color COLOR_WHITE1 = new Color(255, 255, 255, 255);
    private static final Color COLOR_WHITE2 = new Color(255, 255, 255, 50);

    private static final String DATA_KEY = "GLib_ShipDestructionEffects";

    private static final Map<HullSize, Float> EXPLOSION_LENGTH = new HashMap<>(6);
    private static final Map<HullSize, Float> EXPLOSION_SIZE_MOD = new HashMap<>(6);
    private static final Map<HullSize, Float> FLARE_BRIGHTNESS = new HashMap<>(6);
    private static final Map<HullSize, Float> FLARE_THICKNESS = new HashMap<>(6);
    private static final Map<HullSize, Float> LIGHT_DURATION = new HashMap<>(6);
    private static final Map<HullSize, Float> LIGHT_INTENSITY = new HashMap<>(6);

    private static final float OFFSCREEN_GRACE_CONSTANT = 500f;
    private static final float OFFSCREEN_GRACE_FACTOR = 2f;

    private static final Map<HullSize, Float> RIPPLE_LENGTH = new HashMap<>(6);

    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";

    private static final Vector2f ZERO = new Vector2f();

    private static boolean enabled = true;
    private static boolean explosionEnabled = true;
    private static boolean offscreen = false;
    private static boolean shockwaveEnabled = true;
    private static float trailScale = 1f;
    private static boolean trailsEnabled = true;

    static {
        BOSS_SHIPS.put("ssp_superhyperion", 4f);
        BOSS_SHIPS.put("ssp_oberon", 2f);
        BOSS_SHIPS.put("ssp_ultron", 2f);
        BOSS_SHIPS.put("ssp_zeus", 2f);
        BOSS_SHIPS.put("ssp_ezekiel", 3f);
        BOSS_SHIPS.put("ssp_cristarium", 2f);
        BOSS_SHIPS.put("ssp_zero", 4f);
        BOSS_SHIPS.put("ssp_superzero", 5f);
        BOSS_SHIPS.put("ssp_hyperzero", 6f);
    }

    static {
        EXPLOSION_LENGTH.put(HullSize.FIGHTER, 0.5f);
        EXPLOSION_LENGTH.put(HullSize.FRIGATE, 2.5f);
        EXPLOSION_LENGTH.put(HullSize.DESTROYER, 5f);
        EXPLOSION_LENGTH.put(HullSize.DEFAULT, 5f);
        EXPLOSION_LENGTH.put(HullSize.CRUISER, 7.5f);
        EXPLOSION_LENGTH.put(HullSize.CAPITAL_SHIP, 10f);
    }

    static {
        EXPLOSION_SIZE_MOD.put(HullSize.FIGHTER, 0.5f);
        EXPLOSION_SIZE_MOD.put(HullSize.FRIGATE, 0.75f);
        EXPLOSION_SIZE_MOD.put(HullSize.DESTROYER, 1f);
        EXPLOSION_SIZE_MOD.put(HullSize.DEFAULT, 1f);
        EXPLOSION_SIZE_MOD.put(HullSize.CRUISER, 1.25f);
        EXPLOSION_SIZE_MOD.put(HullSize.CAPITAL_SHIP, 1.5f);
    }

    static {
        FLARE_BRIGHTNESS.put(HullSize.FIGHTER, 10f);
        FLARE_BRIGHTNESS.put(HullSize.FRIGATE, 25f);
        FLARE_BRIGHTNESS.put(HullSize.DESTROYER, 50f);
        FLARE_BRIGHTNESS.put(HullSize.DEFAULT, 50f);
        FLARE_BRIGHTNESS.put(HullSize.CRUISER, 100f);
        FLARE_BRIGHTNESS.put(HullSize.CAPITAL_SHIP, 150f);
    }

    static {
        FLARE_THICKNESS.put(HullSize.FIGHTER, 0.33f);
        FLARE_THICKNESS.put(HullSize.FRIGATE, 0.10f);
        FLARE_THICKNESS.put(HullSize.DESTROYER, 0.06f);
        FLARE_THICKNESS.put(HullSize.DEFAULT, 0.06f);
        FLARE_THICKNESS.put(HullSize.CRUISER, 0.04f);
        FLARE_THICKNESS.put(HullSize.CAPITAL_SHIP, 0.03f);
    }

    static {
        LIGHT_DURATION.put(HullSize.FIGHTER, 0f);
        LIGHT_DURATION.put(HullSize.FRIGATE, 0.5f);
        LIGHT_DURATION.put(HullSize.DESTROYER, 1f);
        LIGHT_DURATION.put(HullSize.DEFAULT, 1f);
        LIGHT_DURATION.put(HullSize.CRUISER, 1.5f);
        LIGHT_DURATION.put(HullSize.CAPITAL_SHIP, 2f);
    }

    static {
        LIGHT_INTENSITY.put(HullSize.FIGHTER, 0.5f);
        LIGHT_INTENSITY.put(HullSize.FRIGATE, 1f);
        LIGHT_INTENSITY.put(HullSize.DESTROYER, 1.5f);
        LIGHT_INTENSITY.put(HullSize.DEFAULT, 1.5f);
        LIGHT_INTENSITY.put(HullSize.CRUISER, 1.75f);
        LIGHT_INTENSITY.put(HullSize.CAPITAL_SHIP, 2f);
    }

    static {
        RIPPLE_LENGTH.put(HullSize.FIGHTER, 0.5f);
        RIPPLE_LENGTH.put(HullSize.FRIGATE, 0.75f);
        RIPPLE_LENGTH.put(HullSize.DESTROYER, 1f);
        RIPPLE_LENGTH.put(HullSize.DEFAULT, 1f);
        RIPPLE_LENGTH.put(HullSize.CRUISER, 1.25f);
        RIPPLE_LENGTH.put(HullSize.CAPITAL_SHIP, 1.5f);
    }

    static {
        try {
            loadSettings();
        } catch (IOException | JSONException e) {
            Global.getLogger(ShipDestructionEffects.class).log(Level.ERROR, "Failed to load performance settings: " + e.getMessage());
            enabled = false;
        }
    }

    private static void loadSettings() throws IOException, JSONException {
        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        explosionEnabled = settings.getBoolean("enableExplosionEffects");
        offscreen = settings.getBoolean("drawOffscreenParticles");
        shockwaveEnabled = settings.getBoolean("enableExplosionShockwave");
        trailsEnabled = settings.getBoolean("enableExplosionTrails");
        trailScale = (float) settings.getDouble("explosionTrailScale");

        if (trailScale <= 0.01f) {
            trailsEnabled = false;
        }
        enabled = explosionEnabled || shockwaveEnabled || trailsEnabled;
    }

    private CombatEngineAPI engine;
    private IntervalUtil interval;

    /* We're not going to bother with per-ship time manipulation applying to this.  Chances are a dead ship won't be warping time. */
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || !enabled) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }

        final LocalData localData = (LocalData) engine.getCustomData().get(DATA_KEY);
        final Set<ShipAPI> deadShips = localData.deadShips;
        final List<ExplodingShip> explodingShips = localData.explodingShips;

        interval.advance(amount);

        List<ShipAPI> ships = engine.getShips();

        // We run through all the ships and check for newly-destroyed ships, adding them to the graphics loop
        int shipsSize = ships.size();
        for (int i = 0; i < shipsSize; i++) {
            ShipAPI ship = ships.get(i);
            if (ship == null) {
                continue;
            }

            if (ship.isHulk() == true) {
                if (!deadShips.contains(ship)) {
                    deadShips.add(ship);
                    Vector2f shipLoc = ship.getLocation();
                    Vector2f shipVel = ship.getVelocity();
                    float shipRadius = ship.getCollisionRadius();
                    HullSize shipHullSize = ship.getHullSize();

                    String style = ship.getHullStyleId();
                    if (EXPLOSION_COLORS.get(style) == null) {
                        style = "MIDLINE";
                    }

                    if (explosionEnabled && shipHullSize != ShipAPI.HullSize.FIGHTER && !ship.isDrone()) {
                        float intensity = FLARE_BRIGHTNESS.get(shipHullSize) / 10f;
                        float intensity2 = FLARE_THICKNESS.get(shipHullSize) / 3f;
                        AnamorphicFlare.createFlare(ship, new Vector2f(shipLoc), engine, intensity, intensity2, 0f, 15f, 1f, EXPLOSION_COLORS.get(style),
                                                    COLOR_WHITE1);
                    }

                    if (shockwaveEnabled && !ship.isFighter()) {
                        RippleDistortion ripple = new RippleDistortion(shipLoc, shipVel);
                        ripple.setSize(shipRadius * 4.5f);
                        ripple.setIntensity(shipRadius);
                        ripple.setFrameRate(60f / RIPPLE_LENGTH.get(shipHullSize));
                        ripple.fadeInSize(RIPPLE_LENGTH.get(shipHullSize) * 1.5f);
                        ripple.fadeOutIntensity(RIPPLE_LENGTH.get(shipHullSize));
                        ripple.setSize(shipRadius * 1.5f);
                        DistortionShader.addDistortion(ripple);
                    }

                    if (explosionEnabled) {
                        engine.addHitParticle(shipLoc, ZERO, shipRadius * 10f, 0.75f, shipRadius / 50f, COLOR_WHITE1);
                        engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 7.5f, 0.25f, shipRadius / 35f, COLOR_WHITE2);
                        if (offscreen || ShaderLib.isOnScreen(shipLoc, shipRadius * 2f * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                            Color color = ShipColors.colorJitter(ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_BLACK1, 0.2f), 50f);
                            engine.spawnExplosion(shipLoc, shipVel, color, shipRadius * 2f, (shipRadius / 60f) * ((float) Math.random() * 0.25f + 1f));
                        }
                        if (ship.getHullSize() == HullSize.FIGHTER) {
                            engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 12f, 0.5f, 0.05f, COLOR_WHITE1);
                        } else if (ship.getHullSize() == HullSize.FRIGATE) {
                            engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 15f, 1f, 0.05f, COLOR_WHITE1);
                        } else if (ship.getHullSize() == HullSize.DESTROYER) {
                            engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 15f, 1f, 0.075f, COLOR_WHITE1);
                        } else if (ship.getHullSize() == HullSize.CRUISER) {
                            engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 15f, 1f, 0.1f, COLOR_WHITE1);
                        } else {
                            engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 15f, 1f, 0.125f, COLOR_WHITE1);
                        }
                        StandardLight light = new StandardLight(shipLoc, shipVel, ZERO, null);
                        light.setSize(shipRadius * EXPLOSION_SIZE_MOD.get(shipHullSize) * 4f);
                        light.setIntensity(LIGHT_INTENSITY.get(shipHullSize));
                        light.setColor(EXPLOSION_COLORS.get(style));
                        light.setLifetime(LIGHT_DURATION.get(shipHullSize));
                        light.setAutoFadeOutTime(0.5f + LIGHT_DURATION.get(shipHullSize));
                        LightShader.addLight(light);
                    }

                    if (trailsEnabled && !ship.isShuttlePod() && !ship.isDrone()) {
                        int count = (int) (shipRadius * EXPLOSION_SIZE_MOD.get(shipHullSize) / 4f);
                        float length = EXPLOSION_LENGTH.get(shipHullSize) * ((float) Math.random() * 0.5f + 0.75f);

                        ExplodingShip exploder = new ExplodingShip(ship, count, length / count);
                        explodingShips.add(exploder);
                    }
                }
            }
        }

        // If a ship is vaporized, we add some extra effects
        Iterator<ShipAPI> iter = deadShips.iterator();
        while (iter.hasNext()) {
            ShipAPI ship = iter.next();

            if (ship != null && !ships.contains(ship)) {
                Vector2f shipLoc = ship.getLocation();
                String shipHullId = ship.getHullSpec().getHullId();
                float shipRadius = ship.getCollisionRadius();
                HullSize shipHullSize = ship.getHullSize();
                if (explosionEnabled) {
                    String style = ship.getHullStyleId();
                    if (EXPLOSION_COLORS.get(style) == null) {
                        style = "MIDLINE";
                    }

                    if (engine.isInCampaign() || engine.isInCampaignSim() || engine.isSimulation() || engine.getPlayerShip() == null ||
                            !engine.getPlayerShip().getHullSpec().getHullId().contentEquals("ssp_superhyperion") || BOSS_SHIPS.containsKey(shipHullId)) {
                        engine.addHitParticle(shipLoc, ZERO, shipRadius * 15f, 0.75f, shipRadius / 15f, COLOR_WHITE1);
                        engine.addSmoothParticle(shipLoc, ZERO, shipRadius * 10f, 0.25f, shipRadius / 10f, COLOR_WHITE2);
                        if (offscreen || ShaderLib.isOnScreen(shipLoc, shipRadius * 3f * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                            Color color = ShipColors.colorJitter(ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_BLACK1, 0.2f), 50f);
                            engine.spawnExplosion(shipLoc, ZERO, color, shipRadius * 3f, (shipRadius / 20f) * ((float) Math.random() * 0.25f + 1f));
                        }
                        float intensity = FLARE_BRIGHTNESS.get(shipHullSize) / 10f;
                        float intensity2 = FLARE_THICKNESS.get(shipHullSize) / 3f;
                        AnamorphicFlare.createFlare(ship, new Vector2f(shipLoc), engine, intensity, intensity2, 0f, 15f, 1f, EXPLOSION_COLORS.get(
                                                    style), COLOR_WHITE1);
                    }

                    StandardLight light = new StandardLight(shipLoc, ZERO, ZERO, null);
                    light.setSize(shipRadius * EXPLOSION_SIZE_MOD.get(shipHullSize) * 3f);
                    light.setIntensity(LIGHT_INTENSITY.get(shipHullSize) * 0.75f);
                    light.setColor(EXPLOSION_COLORS.get(style));
                    light.setLifetime(LIGHT_DURATION.get(shipHullSize));
                    light.setAutoFadeOutTime(0.5f + LIGHT_DURATION.get(shipHullSize));
                    LightShader.addLight(light);

                    float sizeMod = EXPLOSION_SIZE_MOD.get(shipHullSize);
                    int particles = (int) (sizeMod * shipRadius / 2f * ((float) Math.random() * 0.5f + 0.75f));
                    int fire = (int) (sizeMod * shipRadius / 20f * ((float) Math.random() * 0.5f + 0.75f));
                    if (BOSS_SHIPS.containsKey(shipHullId)) {
                        sizeMod *= 2f;
                        particles *= 4f;
                        fire *= 4f;
                    }

                    float renderParticleRadius = 123.74f * (shipRadius / 5f) * (BOSS_SHIPS.containsKey(shipHullId) ? 5f : 1f) + 0.70711f * shipRadius;
                    if (offscreen || ShaderLib.isOnScreen(shipLoc, renderParticleRadius * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                        for (int i = 0; i < particles; i++) {
                            Vector2f point = new Vector2f(shipLoc);
                            Vector2f vel = new Vector2f();
                            vel.x += ((float) Math.random() + (float) Math.random() + (float) Math.random()) * 100f / 3f - 50f;
                            vel.y += ((float) Math.random() + (float) Math.random() + (float) Math.random()) * 100f / 3f - 50f;
                            if (BOSS_SHIPS.containsKey(shipHullId)) {
                                vel.scale(5f);
                            }
                            point.x += shipRadius * ((float) Math.random() * 1f - 0.5f);
                            point.y += shipRadius * ((float) Math.random() * 1f - 0.5f);

                            Color color = ShipColors.colorJitter(EXPLOSION_COLORS.get(style), 50f);
                            Color color2 = ShipColors.colorJitter(ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_BLACK1, 0.2f), 50f);

                            engine.addHitParticle(point, vel, (float) Math.random() * 5f + 5f, 1f, (shipRadius / 5f) * ((float) Math.random() * 1.5f + 0.25f),
                                                  color);
                            engine.addHitParticle(point, vel, (float) Math.random() * 15f + 30f, 0.2f, (shipRadius / 5f) *
                                                  ((float) Math.random() * 1.5f + 0.25f), color2);
                        }
                    }

                    float renderFireRadius = 2.2097f * (float) Math.sqrt(shipRadius) * (shipRadius / 25f) * (BOSS_SHIPS.containsKey(shipHullId) ? 3f : 1f) +
                          1.2728f * shipRadius;
                    if (offscreen || ShaderLib.isOnScreen(shipLoc, renderFireRadius * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                        for (int i = 0; i < fire; i++) {
                            Vector2f point = new Vector2f(shipLoc);
                            Vector2f vel = new Vector2f();
                            vel.x += ((float) Math.random() * 2.5f - 1.25f) * (float) Math.sqrt(shipRadius);
                            vel.y += ((float) Math.random() * 2.5f - 1.25f) * (float) Math.sqrt(shipRadius);
                            if (BOSS_SHIPS.containsKey(shipHullId)) {
                                vel.scale(3f);
                            }
                            point.x += shipRadius * ((float) Math.random() * 1.8f - 0.9f);
                            point.y += shipRadius * ((float) Math.random() * 1.8f - 0.9f);

                            Color color = ShipColors.colorJitter(ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_BLACK1, 0.2f), 50f);

                            float size = ((float) Math.random() * 30f + 100f) * sizeMod;

                            engine.spawnExplosion(point, vel, color, size, (shipRadius / 25f) * ((float) Math.random() * 0.5f + 0.75f));
                        }
                    }
                }

                /*
                 int debris = (int) ((Float)(explosionSizeMod.get(shipHullSize)) * shipRadius / 5f * ((float)Math.random() * 0.5f +
                 0.75f));

                 for (int i = 0; i < debris; i++) { Vector2f point = new Vector2f(shipLoc); Vector2f vel = new Vector2f(ship.getVelocity()); vel.x +=
                 (float)Math.random() * 20f - 10f; vel.y += (float)Math.random() * 20f - 10f; point.x += shipRadius * ((float)Math.random() *
                 1.5f - 0.75f); point.y += shipRadius * ((float)Math.random() * 1.5f - 0.75f); vel.x += (point.x - shipLoc.x) *
                 (float)Math.random(); vel.y += (point.y - shipLoc.y) * (float)Math.random();

                 CombatEntityAPI chunk = engine.spawnProjectile(engine.getPlayerShip(), null, "debris", point, (float)Math.random() * 360f, vel);
                 //chunk.setFacing((float)Math.atan2(vel.y, vel.x) * 180f / (float)Math.PI); }
                 */
                iter.remove();
            }
        }

        // Now for the juicy bit...
        Iterator<ExplodingShip> iter2 = explodingShips.iterator();
        while (iter2.hasNext()) {
            ExplodingShip exploder = iter2.next();

            if (exploder.ship == null) {
                exploder.flamePoints.clear();
                iter2.remove();
                continue;
            }

            // If the ship is vaporized, it gets removed from this graphics loop
            if (!engine.getShips().contains(exploder.ship)) {
                exploder.flamePoints.clear();
                iter2.remove();
                continue;
            }

            Vector2f shipLoc = exploder.ship.getLocation();
            String shipHullId = exploder.ship.getHullSpec().getHullId();
            float shipRadius = exploder.ship.getCollisionRadius();
            HullSize shipHullSize = exploder.ship.getHullSize();
            Vector2f shipVel = exploder.ship.getVelocity();

            String style = exploder.ship.getHullStyleId();
            if (EXPLOSION_COLORS.get(style) == null) {
                style = "MIDLINE";
            }

            // Draw fire contrails from our burning wrecks
            Iterator<FlamePoint> iter3 = exploder.flamePoints.iterator();
            while (iter3.hasNext()) {
                FlamePoint flamePoint = iter3.next();

                // Check if the flame contrail has expired yet
                if (!flamePoint.tick(amount)) {
                    // Don't want to generate too many flames...
                    if (interval.intervalElapsed()) {
                        if (offscreen || ShaderLib.isOnScreen(flamePoint.anchor.getLocation(), 113.14f * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                            Vector2f point = new Vector2f(flamePoint.anchor.getLocation());
                            Vector2f vel = new Vector2f();
                            Vector2f vel2 = new Vector2f();

                            Color color = ShipColors.colorJitter(ShipColors.colorBlend(
                                  ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_MAIN, 0.75f), COLOR_BLACK1, 0.2f), 50f);
                            Color color2 = ShipColors.colorJitter(ShipColors.colorBlend(ShipColors.colorBlend(EXPLOSION_COLORS.get(style),
                                                                                                              COLOR_MAIN, 0.75f), COLOR_BLACK1, 0.75f), 30f);
                            Color color3 = ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_WHITE1, 0.5f);

                            float sizeMod = EXPLOSION_SIZE_MOD.get(shipHullSize);
                            float size = ((float) Math.random() * 25f) * sizeMod * flamePoint.scale;
                            float size2 = ((float) Math.random() * 10f + 40f) * sizeMod * flamePoint.scale;
                            float size3 = ((float) Math.random() * 40f + 80f) * sizeMod * flamePoint.scale;
                            float size4 = ((float) Math.random() * 15f + 5f) * flamePoint.scale;

                            vel.x += (float) Math.random() * 40f - 20f;
                            vel.y += (float) Math.random() * 40f - 20f;
                            vel2.x += (float) Math.random() * 20f - 10f;
                            vel2.y += (float) Math.random() * 20f - 10f;

                            // Make fewer flames if the wreck is not moving as quickly
                            if ((float) Math.random() <= (flamePoint.anchor.getVelocity().length() + 50f) / 75f) {
                                engine.addHitParticle(point, flamePoint.anchor.getVelocity(), size4, 0.65f, (float) Math.random() * 0.15f + 0.3f, color3);
                                if ((float) Math.random() >= 0.5f) {
                                    engine.spawnExplosion(point, vel, color, size, (float) Math.random() * 1f + 0.5f);
                                    engine.addSmokeParticle(point, vel2, size2, (float) Math.random() * 0.025f + 0.1f, (float) Math.random() * 4f + 4f, color2);
                                } else {
                                    engine.addHitParticle(point, vel, size3, (float) Math.random() * 0.05f + 0.05f, (float) Math.random() * 0.5f + 0.25f, color);
                                }
                            }
                        }
                    }
                } else {
                    iter3.remove();
                }
            }

            // If the ship is an inert wreck, we flag it for removal from the loop
            if (exploder.flamePoints.isEmpty() && exploder.count <= 0) {
                iter2.remove();
                continue;
            }

            if (!exploder.tick(amount)) {
                continue;
            }

            if (exploder.count <= 0) {
                if (BOSS_SHIPS.containsKey(shipHullId)) {
                    engine.applyDamage(exploder.ship, shipLoc, 1000000f, DamageType.ENERGY, 0f, true, true, exploder.ship);
                }
                continue;
            }

            ShipAPI ship = exploder.ship;

            // And now we make it explode nicely...
            float renderExplosionRadius = 1.4142f * shipRadius + shipVel.length() * 2.4f * EXPLOSION_SIZE_MOD.get(shipHullSize);
            if (offscreen || ShaderLib.isOnScreen(shipLoc, renderExplosionRadius * OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                int bound = 100;
                while (bound > 0) {
                    bound--;
                    Vector2f point = new Vector2f(shipLoc);
                    point.x += shipRadius * ((float) Math.random() * 2f - 1f);
                    point.y += shipRadius * ((float) Math.random() * 2f - 1f);

                    if (CollisionUtils.isPointWithinBounds(point, ship)) {
                        Vector2f vel = new Vector2f(shipVel);
                        Vector2f vel2 = new Vector2f(shipVel);
                        float rand = (float) Math.random() * 0.5f + 0.5f;
                        float sizeMod = EXPLOSION_SIZE_MOD.get(shipHullSize);
                        float otherSize = (float) Math.sqrt(shipRadius);
                        float size = (30f + (float) Math.random() * 30f) * sizeMod;
                        Color color = ShipColors.colorJitter(
                              ShipColors.colorBlend(ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_MAIN, 0.75f), COLOR_BLACK1, 0.2f), 50f);
                        Color color2 = ShipColors.colorJitter(ShipColors.colorBlend(
                              ShipColors.colorBlend(EXPLOSION_COLORS.get(style), COLOR_MAIN, 0.75f), COLOR_BLACK2, 0.8f), 15f);
                        if (BOSS_SHIPS.containsKey(shipHullId)) {
                            size *= BOSS_SHIPS.get(shipHullId);
                        }

                        vel.x *= rand;
                        vel.y *= rand;
                        vel2.x *= rand * 0.5f;
                        vel2.y *= rand * 0.5f;

                        engine.spawnExplosion(point, vel, color, size, size / 25f);
                        engine.addSmokeParticle(point, vel2, size, 0.25f, size / 10f, color2);
                        if ((float) Math.random() >= 0.75 || (BOSS_SHIPS.containsKey(shipHullId) && (float) Math.random() >= 0.5)) {
                            Global.getSoundPlayer().playSound("explosion_from_damage", 0.95f + (float) Math.random() * 0.05f, sizeMod / 2f, point, vel);
                            Color color3 = ShipColors.colorJitter(ShipColors.colorBlend(ShipColors.colorBlend(EXPLOSION_COLORS.get(style),
                                                                                                              COLOR_MAIN, 0.75f), COLOR_WHITE1, 0.33f), 75f);

                            StandardLight light = new StandardLight(point, shipVel, ZERO, null);
                            light.setSize(size * 6f);
                            light.setIntensity(size / 60f);
                            light.setColor(color3);
                            light.fadeOut(size / 90f);
                            LightShader.addLight(light);

                            if (BOSS_SHIPS.containsKey(shipHullId)) {
                                float intensity = FLARE_BRIGHTNESS.get(shipHullSize) / 50f;
                                float intensity2 = FLARE_THICKNESS.get(shipHullSize) * 2f;
                                AnamorphicFlare.createFlare(ship, point, engine, intensity, intensity2, 0f, 15f, 1f, color, color3);

                                RippleDistortion ripple = new RippleDistortion(point, shipVel);
                                ripple.setSize(size * 1f);
                                ripple.setIntensity(size * 0.1f);
                                ripple.setFrameRate(60f / (size / 300f));
                                ripple.fadeInSize(size / 300f);
                                ripple.fadeOutIntensity(size / 300f);
                                DistortionShader.addDistortion(ripple);
                            } else {
                                engine.addHitParticle(point, new Vector2f(shipVel), size * 3f, 1f, size / 60f, COLOR_WHITE1);
                                engine.spawnExplosion(point, ZERO, color, otherSize * 15f * ((float) Math.random() * 0.3f + 0.85f), 0.15f * sizeMod);
                                engine.addSmoothParticle(point, new Vector2f(shipVel), otherSize * 30f * ((float) Math.random() * 0.3f + 0.85f), 0.075f,
                                                         0.15f, color);
                            }

                            float length = EXPLOSION_LENGTH.get(shipHullSize) * ((float) Math.random() * 5f + 2.5f);
                            exploder.flamePoints.add(new FlamePoint(new AnchoredEntity(ship, point), length, (float) Math.random() * 1.3f + 0.35f));
                        } else {
                            engine.addHitParticle(point, new Vector2f(shipVel), size, 0.5f, size / 60f, COLOR_WHITE1);
                        }
                        break;
                    }
                }
            }

            exploder.count -= 1;
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        Global.getCombatEngine().getCustomData().put(DATA_KEY, new LocalData());
        interval = new IntervalUtil(0.1f / trailScale, 0.1f / trailScale);
    }

    private static final class ExplodingShip {

        private final float interval;
        private float ticker;

        int count;
        List<FlamePoint> flamePoints;
        ShipAPI ship;

        private ExplodingShip(ShipAPI ship, int count, float interval) {
            this.ship = ship;
            this.count = (int) (count * trailScale);
            this.interval = interval / trailScale;
            this.flamePoints = new LinkedList<>();
            ticker = 0f;
        }

        private boolean tick(float amount) {
            ticker += amount;
            if (ticker >= interval) {
                ticker -= interval;
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class FlamePoint {

        private float ticker;
        private final float time;

        AnchoredEntity anchor;
        float scale;

        private FlamePoint(AnchoredEntity anchor, float time, float scale) {
            this.anchor = anchor;
            this.time = time;
            this.scale = scale;
            ticker = 0f;
        }

        private boolean tick(float amount) {
            ticker += amount;
            if (ticker >= time) {
                ticker -= time;
                return true;
            } else {
                return false;
            }
        }
    }

    private static final class LocalData {

        final Set<ShipAPI> deadShips = new LinkedHashSet<>(100);
        final List<ExplodingShip> explodingShips = new LinkedList<>();
    }
}
