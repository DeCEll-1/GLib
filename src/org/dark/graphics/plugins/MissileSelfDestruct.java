package org.dark.graphics.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.ShotBehaviorSpecAPI;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MissileSelfDestruct extends BaseEveryFrameCombatPlugin {

    private static final Set<String> EXCLUDED_MISSILES = new HashSet<>(20);

    private static final String SETTINGS_FILE = "GRAPHICS_OPTIONS.ini";
    private static final String SETTINGS_SPREADSHEET = "data/config/glib/no_self_destruct.csv";

    private static boolean enabled = true;

    public static void loadSettings() throws IOException, JSONException {
        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
            JSONArray rows;
            try {
                rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", SETTINGS_SPREADSHEET, mod.getId());
            } catch (RuntimeException e) {
                continue;
            }

            for (int i = 0; i < rows.length(); i++) {
                String id = rows.getJSONObject(i).getString("id");
                EXCLUDED_MISSILES.add(id);
            }
        }

        JSONObject settings = Global.getSettings().loadJSON(SETTINGS_FILE);

        enabled = settings.getBoolean("enableMissileSelfDestruct");
    }

    private CombatEngineAPI engine;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || !enabled) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }

        List<MissileAPI> missiles = engine.getMissiles();
        int size = missiles.size();
        for (int i = 0; i < size; i++) {
            MissileAPI missile = missiles.get(i);
            if (missile.isFading() && !missile.isFlare() && !missile.isMine() && (missile.getCollisionClass() != CollisionClass.NONE) && ((float) Math.random() > 0.75f)) {
                if ((missile.getProjectileSpecId() == null) || !EXCLUDED_MISSILES.contains(missile.getProjectileSpecId())) {
                    boolean isProx = false;
                    if ((missile.getSpec() != null) && (missile.getSpec().getBehaviorSpec() != null)) {
                        ShotBehaviorSpecAPI behaviorSpec = missile.getSpec().getBehaviorSpec();
                        if (behaviorSpec.getBehavorString().contentEquals("PROXIMITY_FUSE")) {
                            System.out.print("!!!\n");
                            isProx = true;
                        }
                    }
                    if (!isProx) {
                        engine.applyDamage(missile, missile.getLocation(), missile.getHitpoints() * 2f,
                                DamageType.FRAGMENTATION, 0f, false, false, missile, false);
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
