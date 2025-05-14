package org.dark.shaders.util;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Level;

/**
 * Internal every frame hook that runs each shader in turn. Do not modify.
 * <p>
 * @author DarkRevenant
 */
public final class ShaderHook implements EveryFrameCombatPlugin {

    public static final String TEXTURE_PRELOAD_KEY = "graphicslib_preload";

    public static boolean enableShaders = true;

    private CombatEngineAPI engine;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        // Stop shaders if they're not even set up!
        if (!ShaderLib.initialized) {
            return;
        }

        final CombatEngineAPI preCombatEngine = Global.getCombatEngine();
        if ((preCombatEngine != null) && !preCombatEngine.getCustomData().containsKey(TEXTURE_PRELOAD_KEY)) {
            preCombatEngine.getCustomData().put(TEXTURE_PRELOAD_KEY, true);

            List<CampaignFleetAPI> fleets = new ArrayList<>(3);
            /* Don't keep *anything* in memory for the mission sim... */
            if (!preCombatEngine.isInMissionSim() && (Global.getCurrentState() != GameState.TITLE)) {
                if (Global.getSector() != null) {
                    CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                    if (playerFleet != null) {
                        fleets.add(playerFleet);
                    }
                }
            }

            /* Skip the sim fleets in simulation; only keep the campaign player fleet in memory */
            if (!preCombatEngine.isInCampaignSim() && !preCombatEngine.isInMissionSim() && !preCombatEngine.isSimulation() && (Global.getCurrentState() != GameState.TITLE)) {
                BattleCreationContext context = preCombatEngine.getContext();
                if (context.getPlayerFleet() != null) {
                    fleets.add(context.getPlayerFleet());
                }
                if (context.getOtherFleet() != null) {
                    fleets.add(context.getOtherFleet());
                }
            }

            Set<FleetMemberAPI> members = new LinkedHashSet<>();
            for (CampaignFleetAPI fleet : fleets) {
                if (fleet.getFleetData() != null) {
                    List<FleetMemberAPI> list = fleet.getFleetData().getMembersListCopy();
                    if (list != null) {
                        members.addAll(list);
                    }
                }
            }

            /* Skip the sim fleets in simulation; only keep the campaign player fleet in memory */
            if (!preCombatEngine.isInCampaignSim() && !preCombatEngine.isInMissionSim() && !preCombatEngine.isSimulation() && (Global.getCurrentState() != GameState.TITLE)) {
                List<CombatFleetManagerAPI> managers = new ArrayList<>(2);
                managers.add(preCombatEngine.getFleetManager(FleetSide.PLAYER));
                managers.add(preCombatEngine.getFleetManager(FleetSide.ENEMY));
                for (CombatFleetManagerAPI manager : managers) {
                    List<FleetMemberAPI> mList = manager.getDeployedCopy();
                    if (mList != null) {
                        members.addAll(mList);
                    }
                    mList = manager.getReservesCopy();
                    if (mList != null) {
                        members.addAll(mList);
                    }
                    mList = manager.getDisabledCopy();
                    if (mList != null) {
                        members.addAll(mList);
                    }
                    mList = manager.getDestroyedCopy();
                    if (mList != null) {
                        members.addAll(mList);
                    }
                    mList = manager.getRetreatedCopy();
                    if (mList != null) {
                        members.addAll(mList);
                    }
                    List<DeployedFleetMemberAPI> dfmList = manager.getStations();
                    for (DeployedFleetMemberAPI dfm : dfmList) {
                        FleetMemberAPI member = dfm.getMember();
                        if (member != null) {
                            members.add(member);
                        }
                    }
                }
            }

            /* Unload unneeded textures at the start of combat */
            TextureData.unloadAndPreloadTextures(members);
        }

        if (engine == null) {
            return;
        }

        if (!ShaderLib.enabled) {
            return;
        }

        for (InputEventAPI event : events) {
            if (event.isConsumed()) {
                continue;
            }

//            if (event.isKeyDownEvent() && event.getEventValue() == 50) { // M
//                ShaderLib.useMaterials = !ShaderLib.useMaterials;
//                event.consume();
//                break;
//            }
//            if (event.isKeyDownEvent() && event.getEventValue() == 49) { // N
//                ShaderLib.useNormals = !ShaderLib.useNormals;
//                event.consume();
//                break;
//            }
//            if (event.isKeyDownEvent() && event.getEventValue() == 48) { // B
//                ShaderLib.useSurfaces = !ShaderLib.useSurfaces;
//                event.consume();
//                break;
//            }
            if (event.isKeyDownEvent() && event.getEventValue() == ShaderLib.toggleKey) {
                enableShaders = !enableShaders;
                event.consume();
                break;
            }

            if (event.isKeyDownEvent() && event.getEventValue() == ShaderLib.reloadKey) {
                final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
                final List<ShaderAPI> newShaders = new ArrayList<>(shaders.size());

                for (ShaderAPI shader : shaders) {
                    shader.destroy();
                    try {
                        @SuppressWarnings("deprecation")
                        final ShaderAPI sdr = (ShaderAPI) Global.getSettings().getScriptClassLoader().loadClass(
                                shader.getClass().getName()).newInstance();
                        newShaders.add(sdr);
                        sdr.initCombat();
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                        Global.getLogger(ShaderLib.class).log(Level.ERROR, "Reload Error! " + ex);
                    }
                }

                shaders.clear();
                for (ShaderAPI shader : newShaders) {
                    shaders.add(shader);
                }

                event.consume();
                return;
            }
        }

        ShaderLib.unsetForegroundRendered();

        final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
        for (ShaderAPI shader : shaders) {
            shader.advance(amount, events);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
        for (ShaderAPI shader : shaders) {
            shader.initCombat();
        }
        if (engine != null) {
            engine.addLayeredRenderingPlugin(new ShaderCombatLayerHook());
        }
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        // Stop shaders if they're not even set up!
        if (!ShaderLib.initialized) {
            return;
        }

        if (engine == null) {
            return;
        }

        if (!ShaderLib.enabled || !enableShaders) {
            return;
        }

        final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
        for (ShaderAPI shader : shaders) {
            if (!shader.isEnabled()) {
                continue;
            }
            if (!shader.isCombat()) {
                shader.renderInScreenCoords(viewport);
            }
        }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        // Stop shaders if they're not even set up!
        if (!ShaderLib.initialized) {
            return;
        }

        if (engine == null) {
            return;
        }

        if (!ShaderLib.enabled || !enableShaders) {
            return;
        }

        final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
        for (ShaderAPI shader : shaders) {
            if (!shader.isEnabled()) {
                continue;
            }
            if (!shader.isCombat()) {
                shader.renderInWorldCoords(viewport);
            }
        }
    }

    @Override
    public void processInputPreCoreControls(float f, List<InputEventAPI> list) {
    }

    static class ShaderCombatLayerHook implements CombatLayeredRenderingPlugin {

        @Override
        public void init(CombatEntityAPI entity) {
        }

        @Override
        public void cleanup() {
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return EnumSet.allOf(CombatEngineLayers.class);
        }

        @Override
        public float getRenderRadius() {
            return 99999999f;
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            // Stop shaders if they're not even set up!
            if (!ShaderLib.initialized) {
                return;
            }

            if (!ShaderLib.enabled || !enableShaders) {
                return;
            }

            final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
            for (ShaderAPI shader : shaders) {
                if (!shader.isEnabled()) {
                    continue;
                }
                if (shader.isCombat() && (shader.getCombatLayer().equals(layer))) {
                    shader.renderInWorldCoords(viewport);
                }
            }
        }
    }
}
