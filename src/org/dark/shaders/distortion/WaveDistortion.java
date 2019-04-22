package org.dark.shaders.distortion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * A general-use wave-shaped distortion object. This type of distortion is suitable for many common situations where a
 * more precise effect is not needed. This distortion is a radial "pop-out" that does not skew textures.
 * <p>
 * @author DarkRevenant
 * @since Alpha 1.1
 */
public class WaveDistortion implements DistortionAPI {

    private static SpriteAPI sprite;

    static boolean pathsSet = false;

    private static void setPaths() {
        String path = "graphics/shaders/distortions/wave.png";
        sprite = Global.getSettings().getSprite(path);
    }

    private float arcAttenWidth = 0f;
    private float arcEnd = 0f;
    private float arcStart = 0f;
    private float autoFadeIntensityTime = 0f;
    private float autoFadeSizeTime = 0f;
    private float deltaIntensity = 0f;
    private float deltaSize = 0f;
    private boolean flipped = false;
    private float intensity = 20f;
    private float lifetime = -1f;
    private final Vector2f location;
    private float maxIntensity = 20f;
    private float maxSize = 100f;
    private float size = 100f;
    private final Vector2f velocity;

    public WaveDistortion() {
        if (!pathsSet) {
            setPaths();
            pathsSet = true;
        }
        this.location = new Vector2f();
        this.velocity = new Vector2f();
    }

    public WaveDistortion(Vector2f location, Vector2f velocity) {
        if (!pathsSet) {
            setPaths();
            pathsSet = true;
        }
        this.location = new Vector2f(location);
        this.velocity = new Vector2f(velocity);
    }

    /**
     * Runs once per frame.
     * <p>
     * @param amount Seconds since last frame.
     * <p>
     * @return True if the distortion object should be destroyed this frame, false if it should not be destroyed this
     * frame.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public boolean advance(float amount) {
        size += deltaSize * amount;
        intensity += deltaIntensity * amount;
        if ((size >= maxSize) && (deltaSize > 0f)) {
            size = maxSize;
            deltaSize = 0f;
        }
        if ((intensity >= maxIntensity) && (deltaIntensity > 0f)) {
            intensity = maxIntensity;
            deltaIntensity = 0f;
        }
        if ((size <= 0f) || (intensity <= 0f)) {
            return true;
        }

        location.translate(velocity.x * amount, velocity.y * amount);

        if ((Float.compare(deltaSize, 0f) == 0) && (Float.compare(deltaIntensity, 0f) == 0)) {
            if (lifetime >= 0f) {
                lifetime -= amount;

                if (lifetime <= 0f) {
                    if ((Float.compare(autoFadeSizeTime, 0f) == 0) || (Float.compare(autoFadeIntensityTime, 0f) == 0)) {
                        return true;
                    }

                    if (Float.compare(autoFadeSizeTime, 0f) != 0) {
                        fadeOutSize(autoFadeSizeTime);
                    }
                    if (Float.compare(autoFadeIntensityTime, 0f) != 0) {
                        fadeOutIntensity(autoFadeIntensityTime);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Sets the distortion's intensity to zero and fades in the distortion's intensity to its original value over a
     * period of time.
     * <p>
     * @param time The time over which to fade in the distortion's intensity.
     * <p>
     * @since Alpha 1.1
     */
    public void fadeInIntensity(float time) {
        deltaIntensity = intensity / time;
        maxIntensity = intensity;
        intensity = 0f;
    }

    /**
     * Sets the distortion's size to zero and fades in the distortion's size to its original value over a period of
     * time.
     * <p>
     * @param time The time over which to fade in the distortion's size.
     * <p>
     * @since Alpha 1.1
     */
    public void fadeInSize(float time) {
        deltaSize = size / time;
        maxSize = size;
        size = 0f;
    }

    /**
     * Fades out the distortion's intensity over a period of time. If the distortion hits zero intensity, it is
     * destroyed. Negative time values will cause the intensity to increase forever.
     * <p>
     * @param time The time over which to fade out the distortion's intensity.
     * <p>
     * @since Alpha 1.1
     */
    public void fadeOutIntensity(float time) {
        deltaIntensity = -intensity / time;
        maxIntensity = Float.MAX_VALUE;
    }

    /**
     * Fades out the distortion's size over a period of time. If the distortion hits zero size, it is destroyed.
     * Negative time values will cause the size to increase forever.
     * <p>
     * @param time The time over which to fade out the distortion's size.
     * <p>
     * @since Alpha 1.1
     */
    public void fadeOutSize(float time) {
        deltaSize = -size / time;
        maxSize = Float.MAX_VALUE;
    }

    /**
     * Sets whether the distortion should be flipped in magnitude. This simulates the effect of performing a
     * horizontal-vertical inversion of the red and green color channels. For example, if the distortion normally looks
     * like a sphere, it will instead look like a spherical hole.
     * <p>
     * @param flipped Whether the magnitude should be flipped.
     * <p>
     * @since Alpha 1.1
     */
    public void flip(boolean flipped) {
        this.flipped = flipped;
    }

    /**
     * The width of the visible arc's edge attenuation, in degrees. Wider attenuation will make the transition smoother.
     * <p>
     * @return The width of the visible arc's edge attenuation, in degrees.
     * <p>
     * @since Beta 1.0
     */
    @Override
    public float getArcAttenuationWidth() {
        return arcAttenWidth;
    }

    /**
     * Sets the width of the visible arc's edge attenuation, in degrees. Wider attenuation will make the transition
     * smoother.
     * <p>
     * @param width The desired width of the visible arc's edge attenuation, in degrees.
     * <p>
     * @since Beta 1.0
     */
    public void setArcAttenuationWidth(float width) {
        arcAttenWidth = width;
    }

    /**
     * The end of the current visible arc of the distortion, in degrees. Note: the entire shader is visible if the arc
     * spans 0 degrees.
     * <p>
     * @return The current facing direction of the distortion.
     * <p>
     * @since Alpha 1.11
     */
    @Override
    public float getArcEnd() {
        return arcEnd;
    }

    /**
     * The start of the current visible arc of the distortion, in degrees. Note: the entire shader is visible if the arc
     * spans 0 degrees.
     * <p>
     * @return The start of the current visible arc of the distortion, in degrees.
     * <p>
     * @since Alpha 1.11
     */
    @Override
    public float getArcStart() {
        return arcStart;
    }

    /**
     * Returns the amount of time the distortion will take to fade out in intensity after its lifetime expires.
     * <p>
     * @return The amount of time the distortion will take to fade out in intensity after its lifetime expires.
     * <p>
     * @since Alpha 1.1
     */
    public float getAutoFadeIntensityTime() {
        return autoFadeIntensityTime;
    }

    /**
     * Sets the amount of time the distortion will take to fade out in intensity after its lifetime expires.
     * <p>
     * @param time The amount of time the distortion should take to fade out in intensity after its lifetime expires.
     * <p>
     * @since Alpha 1.1
     */
    public void setAutoFadeIntensityTime(float time) {
        this.autoFadeIntensityTime = time;
    }

    /**
     * Returns the amount of time the distortion will take to fade out in size after its lifetime expires.
     * <p>
     * @return The amount of time the distortion will take to fade out in size after its lifetime expires.
     * <p>
     * @since Alpha 1.1
     */
    public float getAutoFadeSizeTime() {
        return autoFadeSizeTime;
    }

    /**
     * Sets the amount of time the distortion will take to fade out in size after its lifetime expires.
     * <p>
     * @param time The amount of time the distortion should take to fade out in size after its lifetime expires.
     * <p>
     * @since Alpha 1.1
     */
    public void setAutoFadeSizeTime(float time) {
        this.autoFadeSizeTime = time;
    }

    /**
     * The current facing direction of the distortion, in degrees. Matches sprite's rotation.
     * <p>
     * @return The current facing direction of the distortion.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public float getFacing() {
        // Normally, you set the sprite to match the facing
        // In this case, the facing angle doesn't matter because it's radial
        return sprite.getAngle();
    }

    /**
     * Returns the world-space quantity, in units, to distort by at the maximum distortion level (blue channel at 255).
     * The distortion engine will handle the transformation for you. Note that this function refers to the intensity
     * scale of the distortion texture, not its size. Modify the texture's sprite directly to change its size directly.
     * <p>
     * @return The scaling factor to transform the blue channel by, in world space units.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public float getIntensity() {
        return intensity;
    }

    /**
     * Sets the world-space quantity, in units, to distort by at the maximum distortion level (blue channel at 255). The
     * distortion engine will handle the transformation for you. Note that this function refers to the intensity scale
     * of the distortion texture, not its size. Modify the texture's sprite directly to change its size directly.
     * <p>
     * @param intensity The scaling factor to transform the blue channel by, in world space units.
     * <p>
     * @since Alpha 1.1
     */
    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    /**
     * Sets the time the distortion has before it expires. Lifetime does not count down while the distortion is fading
     * (in or out). After reaching the end of its lifetime, the distortion may fade out before disappearing if it has
     * AutoFade set.
     * <p>
     * @param lifetime The time the distortion has before it expires.
     * <p>
     * @since Alpha 1.1
     */
    public void setLifetime(float lifetime) {
        this.lifetime = lifetime;
    }

    /**
     * Returns the current location of the distortion.
     * <p>
     * @return The current location of the distortion.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public Vector2f getLocation() {
        return location;
    }

    /**
     * Sets the desired location of the distortion.
     * <p>
     * @param location The desired location of the distortion.
     * <p>
     * @since Alpha 1.1
     */
    public void setLocation(Vector2f location) {
        this.location.set(location);
    }

    /**
     * Returns the remaining time the distortion has left before it expires. Lifetime does not count down while the
     * distortion is fading (in or out). After reaching the end of its lifetime, the distortion may fade out before
     * disappearing if it has AutoFade set.
     * <p>
     * @return The remaining time the distortion has left before it expires.
     * <p>
     * @since Alpha 1.1
     */
    public float getRemainingLifetime() {
        return lifetime;
    }

    /**
     * Returns the apparent size of the distortion, in world-space units.
     * <p>
     * @return The apparent size of the distortion, in world-space units.
     * <p>
     * @since Alpha 1.1
     */
    public float getSize() {
        return size;
    }

    /**
     * Sets the apparent size of the distortion, in world-space units.
     * <p>
     * @param size The desired size of the distortion, in world-space units.
     * <p>
     * @since Alpha 1.1
     */
    public void setSize(float size) {
        this.size = size;
    }

    /**
     * The sprite used to draw the distortion texture. The red channel corresponds to horizontal distortion vector,
     * while the green channel corresponds to vertical distortion vector. The engine will normalize these values for
     * you. The blue channel corresponds to distortion magnitude. For example, a pure white square as a distortion
     * texture will copy a square of pixels somewhere to the top-right of the screen.
     * <p>
     * @return The sprite used to draw the distortion texture.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public SpriteAPI getSprite() {
        sprite.setSize(2f * size, 2f * size);
        return sprite;
    }

    /**
     * Gets the current velocity of the distortion.
     * <p>
     * @return The current velocity of the distortion.
     * <p>
     * @since Alpha 1.2
     */
    public Vector2f getVelocity() {
        return velocity;
    }

    /**
     * Sets the velocity for the distortion.
     * <p>
     * @param velocity The velocity to apply to the distortion.
     * <p>
     * @since Alpha 1.2
     */
    public void setVelocity(Vector2f velocity) {
        this.velocity.set(velocity);
    }

    /**
     * Returns whether the distortion is currently fading or not, regardless of how it is fading.
     * <p>
     * @return Whether the distortion is currently fading or not.
     * <p>
     * @since Alpha 1.1
     */
    public boolean isFading() {
        return Float.compare(deltaSize, 0f) != 0 || Float.compare(deltaIntensity, 0f) != 0;
    }

    /**
     * Whether the distortion should be flipped in magnitude. This simulates the effect of performing a
     * horizontal-vertical inversion of the red and green color channels. For example, if the distortion normally looks
     * like a sphere, it will instead look like a spherical hole.
     * <p>
     * @return Whether the magnitude should be flipped.
     * <p>
     * @since Alpha 1.1
     */
    @Override
    public boolean isFlipped() {
        return flipped;
    }

    /**
     * Sets the visible arc of the distortion, in degrees. Note: the entire shader is visible if the arc spans 0
     * degrees.
     * <p>
     * @param start The start of the distortion's visible arc, in degrees.
     * @param end The end of the distortion's visible arc, in degrees.
     * <p>
     * @since Alpha 1.11
     */
    public void setArc(float start, float end) {
        arcStart = start;
        arcEnd = end;
        if ((arcStart < -360f) || (arcStart > 360f)) {
            arcStart %= 360f;
        }
        if (arcStart < 0f) {
            arcStart += 360f;
        }
        if ((arcEnd < -360f) || (arcEnd > 360f)) {
            arcEnd %= 360f;
        }
        if (arcEnd < 0f) {
            arcEnd += 360f;
        }
    }
}
