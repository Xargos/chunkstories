package io.xol.engine.graphics.geometry;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.lwjgl.BufferUtils;

import io.xol.engine.graphics.RenderingContext;
import io.xol.engine.graphics.fonts.TrueTypeFont;
import io.xol.engine.graphics.textures.Texture2D;
import io.xol.engine.graphics.util.TrueTypeFontRenderer;
import io.xol.engine.math.lalgb.Vector4f;

import static org.lwjgl.opengl.GL11.*;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class TextMeshObject
{
	private boolean done = false;
	private List<VerticesObject> verticesObjects = new LinkedList<VerticesObject>();

	public TextMeshObject(String text)
	{
		TrueTypeFontRenderer.get().drawStringIngame(TrueTypeFont.arial11px, 0, 0, text, 2.0f, 256, this);
	}

	public List<VerticesObject> getVerticesObjects()
	{
		return verticesObjects;
	}

	private ByteBuffer tempBuffer = BufferUtils.createByteBuffer(4 * (3 + 2 + 4) * 6 * 128);
	private int temp = 0;

	private Texture2D currentTexture;
	private Vector4f currentColor;

	public void setState(Texture2D texture, Vector4f color)
	{
		//If we changed texture, render the temp stuff
		if (currentTexture != null && !texture.equals(currentTexture))
			finalizeTemp();
		else if (currentColor != null && !color.equals(currentColor))
			finalizeTemp();

		currentTexture = texture;
		currentColor = color;
	}

	private void finalizeTemp()
	{
		if (done)
			return;
		if (temp <= 0)
			return;

		VerticesObject verticesObject = new VerticesObject();
		tempBuffer.flip();
		//System.out.println("Cucking"+temp);
		verticesObject.uploadData(tempBuffer);

		//System.out.println("Added " + verticesObject.getVramUsage() + " bytes worth of verticesObject");
		verticesObjects.add(verticesObject);

		tempBuffer = BufferUtils.createByteBuffer(4 * (3 + 2 + 4) * 6 * 128);
		temp = 0;
	}

	public void drawQuad(float startX, float startY, float width, float height, float textureStartX, float textureStartY, float textureEndX, float textureEndY)
	{
		if (tempBuffer.position() == tempBuffer.capacity())
			finalizeTemp();

		float endX = startX + width;
		float endY = startY + height;

		addVertice(startX, startY, textureStartX, textureStartY);
		addVertice(startX, endY, textureStartX, textureEndY);
		addVertice(endX, endY, textureEndX, textureEndY);

		addVertice(startX, startY, textureStartX, textureStartY);
		addVertice(endX, startY, textureEndX, textureStartY);
		addVertice(endX, endY, textureEndX, textureEndY);

		temp += 6;
	}

	private void addVertice(float startX, float startY, float textureStartX, float textureStartY)
	{
		//Vertex
		tempBuffer.putFloat(startX / 256f - 0.5f);
		tempBuffer.putFloat(startY / 256f - 0.5f);
		tempBuffer.putFloat(0f);
		//Texcoord
		tempBuffer.putFloat(textureStartX);
		tempBuffer.putFloat(textureStartY);
		//Color
		tempBuffer.putFloat(currentColor.x);
		tempBuffer.putFloat(currentColor.y);
		tempBuffer.putFloat(currentColor.z);
		tempBuffer.putFloat(currentColor.w);
	}

	public void done()
	{
		finalizeTemp();
		done = true;
	}

	public void render(RenderingContext renderingContext)
	{
		renderingContext.bindAlbedoTexture(TrueTypeFont.arial11px.glTextures[0]);
		
		glDisable(GL_CULL_FACE);
		renderingContext.currentShader().setUniformFloat("useColorIn", 1.0f);
		renderingContext.currentShader().setUniformFloat("useNormalIn", 0.0f);

		renderingContext.enableVertexAttribute("vertexIn");
		renderingContext.enableVertexAttribute("texCoordIn");
		renderingContext.enableVertexAttribute("colorIn");
		//renderingContext.disableVertexAttribute("normalIn");
		for (VerticesObject verticesObject : verticesObjects)
		{
			verticesObject.bind();

			renderingContext.setVertexAttributePointerLocation("vertexIn", 3, GL_FLOAT, false, 4 * (3 + 2 + 4), 0);
			renderingContext.setVertexAttributePointerLocation("texCoordIn", 2, GL_FLOAT, false, 4 * (3 + 2 + 4), 4 * 3);
			renderingContext.setVertexAttributePointerLocation("colorIn", 4, GL_FLOAT, false, 4 * (3 + 2 + 4), 4 * (3 + 2));
			
			//System.out.println(verticesObject.getVramUsage() / 4 * (3 + 2 + 4));
			
			verticesObject.drawElementsTriangles((int) (verticesObject.getVramUsage() / (4 * (3 + 2 + 4))));
			//renderingContext.enableVertexAttribute("colorIn");
		}
		renderingContext.enableVertexAttribute("colorIn");
		renderingContext.enableVertexAttribute("normalIn");
		renderingContext.currentShader().setUniformFloat("useColorIn", 0.0f);
		renderingContext.currentShader().setUniformFloat("useNormalIn", 1.0f);
	}
}
