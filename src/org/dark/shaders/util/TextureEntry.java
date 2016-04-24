package org.dark.shaders.util;

import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Entry format for texture data.
 * <p>
 * @author DarkRevenant
 * @since Alpha 1.5
 */
public class TextureEntry {

    public final float magnitude;
    public final SpriteAPI sprite;

    TextureEntry(SpriteAPI sprite, float magnitude) {
        this.sprite = sprite;
        this.magnitude = magnitude;
    }

    TextureEntry(TextureEntry entry) {
        sprite = entry.sprite;
        magnitude = entry.magnitude;
    }
}
