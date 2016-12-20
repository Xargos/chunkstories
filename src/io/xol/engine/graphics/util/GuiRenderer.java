package io.xol.engine.graphics.util;

import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.math.lalgb.vector.sp.Vector4fm;

public interface GuiRenderer
{

	void drawBoxWindowsSpace(float startX, float startY, float endX, float endY, float textureStartX, float textureStartY, float textureEndX, float textureEndY, Texture2D texture, boolean alpha, boolean textured, Vector4fm color);

	void drawBoxWindowsSpaceWithSize(float startX, float startY, float width, float height, float textureStartX, float textureStartY, float textureEndX, float textureEndY, Texture2D texture, boolean alpha, boolean textured, Vector4fm color);

	void drawBox(float startX, float startY, float endX, float endY, float textureStartX, float textureStartY, float textureEndX, float textureEndY, Texture2D texture, boolean alpha, boolean textured, Vector4fm color);

}