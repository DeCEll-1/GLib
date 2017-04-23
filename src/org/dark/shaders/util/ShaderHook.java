package org.dark.shaders.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;

/**
 * Internal every frame hook that runs each shader in turn. Do not modify.
 * <p>
 * @author DarkRevenant
 */
public final class ShaderHook implements EveryFrameCombatPlugin {

    public static boolean enableShaders = true;
    private CombatEngineAPI engine;

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        // Stop shaders if they're not even set up!
        if (!ShaderLib.initialized) {
            return;
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

            /*
             if (event.isKeyDownEvent() && event.getEventValue() == 50) { // M ShaderLib.useMaterials = !ShaderLib.useMaterials; event.consume(); break; }

             if (event.isKeyDownEvent() && event.getEventValue() == 49) { // N ShaderLib.useNormals = !ShaderLib.useNormals; event.consume(); break; }

             if (event.isKeyDownEvent() && event.getEventValue() == 48) { // B ShaderLib.useSurfaces = !ShaderLib.useSurfaces; event.consume(); break; }
             */
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
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        final List<ShaderAPI> shaders = ShaderLib.getShaderAPIs();
        for (ShaderAPI shader : shaders) {
            shader.initCombat();
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
            shader.renderInScreenCoords(viewport);
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
            shader.renderInWorldCoords(viewport);
        }
    }
}
