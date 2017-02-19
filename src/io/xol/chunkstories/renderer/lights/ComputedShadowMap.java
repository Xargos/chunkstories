package io.xol.chunkstories.renderer.lights;

import io.xol.chunkstories.api.math.Matrix4f;
import io.xol.engine.graphics.textures.Texture2D;

public interface ComputedShadowMap
{
	public Texture2D getShadowMap();
	
	public Matrix4f getShadowTransformationMatrix();
}
