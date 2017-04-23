package org.dark.graphics.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.io.IOException;
import java.util.List;
import org.apache.log4j.Level;
import org.dark.shaders.util.ShaderLib;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

public class ArcEffectOnOverload extends BaseEveryFrameCombatPlugin {

    private static final float OFFSCREEN_GRACE_CONSTANT = 500f;
    private static final float OFFSCREEN_GRACE_FACTOR = 2f;

    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";

    private static boolean enabled = true;
    private static boolean offscreen = false;

    static {
        try {
            loadSettings();
        } catch (IOException | JSONException e) {
            Global.getLogger(ArcEffectOnOverload.class).log(Level.ERROR, "Failed to load performance settings: " +
                                                            e.getMessage());
            enabled = false;
        }
    }

    private static void loadSettings() throws IOException, JSONException {
        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        enabled = settings.getBoolean("enableOverloadArcs");
        offscreen = settings.getBoolean("drawOffscreenParticles");
    }

    private CombatEngineAPI engine;
    private final IntervalUtil interval = new IntervalUtil(0.25f, 0.5f);

    /* We're not going to bother with per-ship time manipulation applying to this.  An overloaded ship probably won't be warping time. */
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || !enabled) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }

        interval.advance(amount);
        if (interval.intervalElapsed()) {
            List<ShipAPI> ships = engine.getShips();
            int size = ships.size();
            for (int i = 0; i < size; i++) {
                ShipAPI ship = ships.get(i);
                if (ship.isHulk()) {
                    continue;
                }

                if (ship.getFluxTracker().isOverloaded()) {
                    if (offscreen || ShaderLib.isOnScreen(ship.getLocation(), ship.getCollisionRadius() *
                                                          OFFSCREEN_GRACE_FACTOR + OFFSCREEN_GRACE_CONSTANT)) {
                        int arcs = 1;
                        if (ship.getHullSize() == ShipAPI.HullSize.FIGHTER) {
                            arcs = 0;
                        } else if (ship.getHullSize() == ShipAPI.HullSize.FRIGATE || ship.getHullSize() ==
                                ShipAPI.HullSize.DEFAULT) {
                            arcs = 1;
                        } else if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER) {
                            arcs = 1;
                        } else if (ship.getHullSize() == ShipAPI.HullSize.CRUISER) {
                            arcs = 2;
                        } else if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {
                            arcs = 3;
                        }

                        ShipAPI empTarget = ship;
                        for (int a = 0; a < arcs; a++) {
                            Vector2f point = new Vector2f(ship.getLocation());

                            point.x += (ship.getCollisionRadius() / 3f) * (((float) Math.random() * 2f) - 1);
                            point.y += (ship.getCollisionRadius() / 3f) * (((float) Math.random() * 2f) - 1);

                            engine.spawnEmpArc(ship, point, empTarget, empTarget, DamageType.OTHER, 0f, 0f,
                                               ship.getCollisionRadius(), null, 12f,
                                               ship.getVentFringeColor(), ship.getVentCoreColor());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }
}
