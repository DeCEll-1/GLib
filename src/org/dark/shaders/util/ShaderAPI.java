package org.dark.shaders.util;

import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.List;

/**
 * An interface designed to allow shaders to be instanced and managed.
 * <p>
 * Implementations of ShaderAPI persist between combat, so make sure you consider local variables to be effectively static for the purposes of memory leaks.
 * <p>
 * @author DarkRevenant
 */
public interface ShaderAPI {

    /**
     * This method is called before advance() and before the UI is drawn.
     * <p>
     * @param viewport The world-based coordinates to draw with.
     */
    void renderInWorldCoords(ViewportAPI viewport);

    /**
     * This method is called before advance() and before the UI is drawn.
     * <p>
     * @param viewport The screen-based coordinates to draw with.
     */
    void renderInScreenCoords(ViewportAPI viewport);

    /**
     * This method will be called for multiple shaders in a particular order, dictated by getRenderOrder(). Make sure that data updates and bookkeeping are done
     * within an advance() method.
     * <p>
     * @param amount The amount of time since the last frame.
     * @param events A list of general input events.
     */
    void advance(float amount, List<InputEventAPI> events);

    /**
     * This method is called at the beginning of each combat instance.
     */
    void initCombat();

    /**
     * This method is called when the current instance is destroyed, such as when the settings are reloaded.
     * <p>
     * @since Alpha 1.6
     */
    void destroy();

    /**
     * This method returns whether this shader is enabled or not.
     * <p>
     * @return Whether this shader is enabled or not.
     * <p>
     * @since Alpha 1.02
     */
    public boolean isEnabled();

    /**
     * This method determines the order in which the shader is inserted in the rendering queue. This method is read every frame.
     * <p>
     * @return The order by which the shader will render.
     */
    RenderOrder getRenderOrder();

    /**
     * Render order: OBJECT_SPACE, WORLD_SPACE, DISTORTED_SPACE, then SCREEN_SPACE
     */
    public static enum RenderOrder {

        OBJECT_SPACE, WORLD_SPACE, DISTORTED_SPACE, SCREEN_SPACE
    }
}
