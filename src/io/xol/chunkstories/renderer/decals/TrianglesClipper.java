package io.xol.chunkstories.renderer.decals;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

import java.nio.ByteBuffer;

import io.xol.engine.math.lalgb.Matrix4f;
import io.xol.engine.math.lalgb.vector.dp.Vector3dm;

import io.xol.engine.math.lalgb.vector.sp.Vector4fm;
import io.xol.engine.math.lalgb.vector.Vector3;
import io.xol.engine.math.lalgb.vector.sp.Vector3fm;

/**
 * Straight from the 6th gate of hell, forged in the shattered skulls of fresh babies, this code should not be messed with. Proceed at your own risk.
 */
public class TrianglesClipper
{
	private static Matrix4f toClipSpace;
	private static Matrix4f fromClipSpace;

	private static ByteBuffer out;

	public static synchronized int clipTriangles(ByteBuffer in, ByteBuffer out, Matrix4f rotationMatrix, Vector3<Double> originPosition, Vector3<Float> direction, Vector3<Double> size)
	{
		int actualCount = 0;

		toClipSpace = new Matrix4f(rotationMatrix);
		toClipSpace.translate(new Vector3fm(originPosition).negate());
		
		Matrix4f resize = new Matrix4f();
		resize.scale(new Vector3fm(1 / size.getX(), 1 / size.getY(), 1));
		Matrix4f.transpose(resize, resize);

		Matrix4f.mul(resize, toClipSpace, toClipSpace);

		Matrix4f decal = new Matrix4f();
		decal.translate(new Vector3fm(0.5f, 0.5f, 1.0f));

		Matrix4f.mul(decal, toClipSpace, toClipSpace);

		fromClipSpace = Matrix4f.invert(toClipSpace, fromClipSpace);

		TrianglesClipper.out = out;

		while (in.hasRemaining())
		{
			Vector3fm triVert1 = new Vector3fm(in.getFloat(), in.getFloat(), in.getFloat());

			//Skip backward-facing tris
			Vector3fm normal = new Vector3fm(in.getFloat(), in.getFloat(), in.getFloat());
			if (normal.dot(direction) >= 0)
			{
				for (int i = 0; i < 6; i++)
					in.getFloat();
				continue;
			}

			Vector3fm triVert2 = new Vector3fm(in.getFloat(), in.getFloat(), in.getFloat());
			//Etc
			for (int i = 0; i < 3; i++)
				in.getFloat();
			Vector3fm triVert3 = new Vector3fm(in.getFloat(), in.getFloat(), in.getFloat());
			//Etc
			for (int i = 0; i < 3; i++)
				in.getFloat();

			actualCount += 3 * cull(triVert1, triVert2, triVert3);
		}
		
		return actualCount;
	}

	private static int cull(Vector3fm vert1, Vector3fm vert2, Vector3fm vert3)
	{
		Vector4fm tv1 = Matrix4f.transform(toClipSpace, new Vector4fm(vert1.getX(), vert1.getY(), vert1.getZ(), 1.0f), null);
		Vector4fm tv2 = Matrix4f.transform(toClipSpace, new Vector4fm(vert2.getX(), vert2.getY(), vert2.getZ(), 1.0f), null);
		Vector4fm tv3 = Matrix4f.transform(toClipSpace, new Vector4fm(vert3.getX(), vert3.getY(), vert3.getZ(), 1.0f), null);

		if (tv1.getX() < 0.0 && tv2.getX() < 0.0 && tv3.getX() < 0.0)
			return 0;
		if (tv1.getY() < 0.0 && tv2.getY() < 0.0 && tv3.getY() < 0.0)
			return 0;
		if (tv1.getX() > 1.0 && tv2.getX() > 1.0 && tv3.getX() > 1.0)
			return 0;
		if (tv1.getY() > 1.0 && tv2.getY() > 1.0 && tv3.getY() > 1.0)
			return 0;
		return cullLeft(tv1, tv2, tv3);
	}

	private static int cullLeft(Vector4fm vert1, Vector4fm vert2, Vector4fm vert3)
	{
		//Sort
		Vector4fm v1, v2, v3;
		if (vert1.getX() > vert2.getX())
		{
			if (vert1.getX() > vert3.getX())
			{
				v3 = vert1;
				if (vert2.getX() > vert3.getX())
				{
					v2 = vert2;
					v1 = vert3;
				}
				else
				{
					v2 = vert3;
					v1 = vert2;
				}
			}
			else
			{
				v2 = vert1;
				if (vert2.getX() > vert3.getX())
				{
					v3 = vert2;
					v1 = vert3;
				}
				else
				{
					v3 = vert3;
					v1 = vert2;
				}
			}
		}
		else
		{
			if (vert2.getX() > vert3.getX())
			{
				v3 = vert2;
				if (vert1.getX() > vert3.getX())
				{
					v2 = vert1;
					v1 = vert3;
				}
				else
				{
					v2 = vert3;
					v1 = vert1;
				}
			}
			else
			{
				v2 = vert2;
				v3 = vert3;
				v1 = vert1;
			}
		}
		if (v1.getX() <= v2.getX() && v2.getX() <= v3.getX())
		{

		}
		else
		{
			System.out.println("fuck X");
			return 0;
		}

		//Actual culling here
		float border = 0.0f;
		//One point is clipping
		if (v1.getX() < border && v2.getX() > border && v3.getX() > border)
		{
			float d2to1 = v2.getX() - v1.getX();
			float d2tb = v2.getX();
			Vector4fm v2to1 = new Vector4fm(v1).sub(v2);
			v2to1.scale((d2tb) / d2to1);
			v2to1.add(v2);
			int t = cullRight(v2to1, v2, v3);
			
			float d3to1 = v3.getX() - v1.getX();
			float d3tb = v3.getX();
			Vector4fm v3to1 = new Vector4fm(v1).sub(v3);
			v3to1.scale((d3tb) / d3to1);
			v3to1.add(v3);
			return t + cullRight(v2to1, v3, v3to1);
		}
		//Two points are
		else if (v1.getX() < border && v2.getX() < border && v3.getX() > border)
		{
			float d3tb = v3.getX();
			
			float d3to1 = v3.getX() - v1.getX();
			Vector4fm v3to1 = new Vector4fm(v1).sub(v3);
			v3to1.scale((d3tb) / d3to1);
			v3to1.add(v3);
			
			float d3to2 = v3.getX() - v2.getX();
			Vector4fm v3to2 = new Vector4fm(v2).sub(v3);
			v3to2.scale((d3tb) / d3to2);
			v3to2.add(v3);
			
			//System.out.println("v3to1"+v3to1);
			
			return cullRight(v3to1, v3, v3to2);
		}
		//All are
		else if (v1.getX() < border && v2.getX() < border && v3.getX() < border)
		{
			//System.out.println("all out !");
			return 0;
		}
		//None are
		else
		{
			return cullRight(v1, v2, v3);
		}
	}

	private static int cullRight(Vector4fm v1, Vector4fm v2, Vector4fm v3)
	{
		float border = 1.0f;
		//One point is clipping
		if (v1.getX() < border && v2.getX() < border && v3.getX() > border)
		{
			//System.out.println("clipping...");
			//Continue the two segments up to the border
			float d2t3 = v3.getX() - v2.getX();
			float d2tb = border - v2.getX();
			Vector4fm v2to3 = new Vector4fm(v3).sub(v2);
			v2to3.scale((d2tb) / d2t3);
			v2to3.add(v2);
			//System.out.println(v2to3+" is in of clip ("+v2to3.x+")");
			//other one

			float d1t3 = v3.getX() - v1.getX();
			float d1tb = border - v1.getX();
			Vector4fm v1to3 = new Vector4fm(v3).sub(v1);
			v1to3.scale((d1tb) / d1t3);
			v1to3.add(v1);
			//System.out.println(v1to3+" is in of clip ("+v1to3.x+")");

			return cullTop(v1, v2, v1to3) + cullTop(v2to3, v2, v1to3);
		}
		//Two points are
		else if (v1.getX() < border && v2.getX() > border && v3.getX() > border)
		{
			float d1t3 = v3.getX() - v1.getX();
			float d1tb = border - v1.getX();
			Vector4fm v1to3 = new Vector4fm(v3).sub(v1);
			v1to3.scale((d1tb) / d1t3);
			v1to3.add(v1);
			//other one
			float d1t2 = v2.getX() - v1.getX();
			//float d1tb = border - v1.x;
			Vector4fm v1to2 = new Vector4fm(v2).sub(v1);
			v1to2.scale((d1tb) / d1t2);
			v1to2.add(v1);

			return cullTop(v1, v1to2, v1to3);
		}
		else if (v1.getX() > border && v2.getX() > border && v3.getX() > border)
		{
			//System.out.println("all out !");
			return 0;
		}
		else
		{
			return cullTop(v1, v2, v3);
		}
	}

	private static int cullTop(Vector4fm vert1, Vector4fm vert2, Vector4fm vert3)
	{
		Vector4fm v1, v2, v3;
		if (vert1.getY() > vert2.getY())
		{
			if (vert1.getY() > vert3.getY())
			{
				v3 = vert1;
				if (vert2.getY() > vert3.getY())
				{
					v2 = vert2;
					v1 = vert3;
				}
				else
				{
					v2 = vert3;
					v1 = vert2;
				}
			}
			else
			{
				v2 = vert1;
				if (vert2.getY() > vert3.getY())
				{
					v3 = vert2;
					v1 = vert3;
				}
				else
				{
					v3 = vert3;
					v1 = vert2;
				}
			}
		}
		else
		{
			if (vert2.getY() > vert3.getY())
			{
				v3 = vert2;
				if (vert1.getY() > vert3.getY())
				{
					v2 = vert1;
					v1 = vert3;
				}
				else
				{
					v2 = vert3;
					v1 = vert1;
				}
			}
			else
			{
				v2 = vert2;
				v3 = vert3;
				v1 = vert1;
			}
		}
		if (v1.getY() <= v2.getY() && v2.getY() <= v3.getY())
		{

		}
		else
		{
			System.out.println("fuck Y" + v1 + v2 + v3);
			return 0;
		}
		//Actual culling here
		
		float border = 1.0f;
		//One point is clipping
		if (v1.getY() < border && v2.getY() < border && v3.getY() > border)
		{
			//System.out.println("clipping...");
			//Continue the two segments up to the border
			float d2t3 = v3.getY() - v2.getY();
			float d2tb = border - v2.getY();
			Vector4fm v2to3 = new Vector4fm(v3).sub(v2);
			v2to3.scale((d2tb) / d2t3);
			v2to3.add(v2);
			//System.out.println(v2to3+" is in of clip ("+v2to3.y+")");
			//other one

			float d1t3 = v3.getY() - v1.getY();
			float d1tb = border - v1.getY();
			Vector4fm v1to3 = new Vector4fm(v3).sub(v1);
			v1to3.scale((d1tb) / d1t3);
			v1to3.add(v1);
			//System.out.println(v1to3+" is in of clip ("+v1to3.y+")");

			return cullBot(v1, v2, v1to3) + cullBot(v2to3, v2, v1to3);
		}
		//Two points are
		else if (v1.getY() < border && v2.getY() > border && v3.getY() > border)
		{
			float d1t3 = v3.getY() - v1.getY();
			float d1tb = border - v1.getY();
			Vector4fm v1to3 = new Vector4fm(v3).sub(v1);
			v1to3.scale((d1tb) / d1t3);
			v1to3.add(v1);
			//other one
			float d1t2 = v2.getY() - v1.getY();
			//float d1tb = border - v1.y;
			Vector4fm v1to2 = new Vector4fm(v2).sub(v1);
			v1to2.scale((d1tb) / d1t2);
			v1to2.add(v1);

			return cullBot(v1, v1to2, v1to3);
		}
		else if (v1.getY() > border && v2.getY() > border && v3.getY() > border)
		{
			//System.out.println("all out !");
			return 0;
		}
		else
		{
			return cullBot(v1, v2, v3);
		}
	}

	private static int cullBot(Vector4fm v1, Vector4fm v2, Vector4fm v3)
	{
		float border = 0.0f;
		//One point is clipping
		if (v1.getY() < border && v2.getY() > border && v3.getY() > border)
		{
			float d2to1 = v2.getY() - v1.getY();
			float d2tb = v2.getY();
			Vector4fm v2to1 = new Vector4fm(v1).sub(v2);
			v2to1.scale((d2tb) / d2to1);
			v2to1.add(v2);
			int t = cullDone(v2to1, v2, v3);
			
			float d3to1 = v3.getY() - v1.getY();
			float d3tb = v3.getY();
			Vector4fm v3to1 = new Vector4fm(v1).sub(v3);
			v3to1.scale((d3tb) / d3to1);
			v3to1.add(v3);
			return t + cullDone(v2to1, v3, v3to1);
		}
		//Two points are
		else if (v1.getY() < border && v2.getY() < border && v3.getY() > border)
		{
			float d3tb = v3.getY();
			
			float d3to1 = v3.getY() - v1.getY();
			Vector4fm v3to1 = new Vector4fm(v1).sub(v3);
			v3to1.scale((d3tb) / d3to1);
			v3to1.add(v3);
			
			float d3to2 = v3.getY() - v2.getY();
			Vector4fm v3to2 = new Vector4fm(v2).sub(v3);
			v3to2.scale((d3tb) / d3to2);
			v3to2.add(v3);
			
			//System.out.println("v3to1"+v3to1);
			
			return cullDone(v3to1, v3, v3to2);
		}
		//All are
		else if (v1.getY() < border && v2.getY() < border && v3.getY() < border)
		{
			return 0;
			//System.out.println("all out !");
		}
		//None are
		else
		{
			return cullDone(v1, v2, v3);
		}
	}

	private static int cullDone(Vector4fm vert1, Vector4fm vert2, Vector4fm vert3)
	{
		out(vert1);
		out(vert2);
		out(vert3);
		
		return 1;
	}

	private static void out(Vector4fm tm)
	{
		//Wrap round out buffer
		if (out.position() == out.capacity())
			out.position(0);
		
		//out(vert, tm.x, tm.y);
		Vector4fm keke = Matrix4f.transform(fromClipSpace, tm, null);

		out.putFloat((float) keke.getX());
		out.putFloat((float) keke.getY());
		out.putFloat((float) keke.getZ());

		out.putFloat(tm.getX());
		out.putFloat(tm.getY());
	}
}
