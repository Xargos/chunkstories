package io.xol.engine.math;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import io.xol.chunkstories.api.rendering.CameraInterface;
import io.xol.engine.math.lalgb.Vector3f;

public class GeometryHelper
{
	//TODO : This is ancient and hidious. Remake it using Camera class and it's vectors, we don't use fixed pipeline anymore it's 2016 !
	
	// CREDIT: teletubo on java-gaming.org for original code
	// Cleaned it up and added some code to get a normalized vector

	static IntBuffer viewport = BufferUtils.createIntBuffer(16);
	static FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
	static FloatBuffer projection = BufferUtils.createFloatBuffer(16);
	static FloatBuffer winZ = BufferUtils.createFloatBuffer(20);
	static FloatBuffer position = BufferUtils.createFloatBuffer(3);

	static public Vector3f getMousePositionIn3dCoords(int mouseX, int mouseY)
	{
		/*viewport.clear();
		modelview.clear();
		projection.clear();
		winZ.clear();
		position.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
		GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
		float winX = mouseX;
		float winY = mouseY;
		GL11.glReadPixels(mouseX, (int) winY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, winZ);
		float zz = winZ.get();
		GLU.gluUnProject(winX, winY, zz, modelview, projection, viewport, position);
		Vector3f v = new Vector3f(position.get(0), position.get(1), position.get(2));
		return v;*/
		return null;
	}

	static public Vector3f getVectorMouseIn3d(int mx, int my, CameraInterface cam)
	{
		/*Vector3f position = getMousePositionIn3dCoords(mx, my);
		Vector3f camera = cam.pos.castToSP();
		camera.negate();//new Vector3f((float) -cam.camPosX, (float) -cam.camPosY, (float) -cam.camPosZ);
		Vector3f v = new Vector3f(0, 0, 0);
		Vector3f.sub(position, camera, v);
		v.normalise();
		return v;*/
		return null;
	}

}
