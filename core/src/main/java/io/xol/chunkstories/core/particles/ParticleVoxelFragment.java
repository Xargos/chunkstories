package io.xol.chunkstories.core.particles;

import io.xol.chunkstories.api.math.vector.dp.Vector3dm;
import io.xol.chunkstories.api.particles.ParticleDataWithTextureCoordinates;
import io.xol.chunkstories.api.particles.ParticleDataWithVelocity;
import io.xol.chunkstories.api.particles.ParticleType;
import io.xol.chunkstories.api.particles.ParticleTypeHandler;
import io.xol.chunkstories.api.particles.ParticlesRenderer;
import io.xol.chunkstories.api.rendering.RenderingInterface;
import io.xol.chunkstories.api.rendering.textures.Texture2D;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.api.voxel.VoxelSides;
import io.xol.chunkstories.api.voxel.textures.VoxelTexture;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.voxel.VoxelsStore;
import io.xol.chunkstories.world.WorldImplementation;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class ParticleVoxelFragment extends ParticleTypeHandler
{
	public ParticleVoxelFragment(ParticleType type) {
		super(type);
	}

	public class FragmentData extends ParticleData implements ParticleDataWithTextureCoordinates, ParticleDataWithVelocity {
		
		VoxelTexture tex;
		
		int timer = 1450; // 30s
		Vector3dm vel = new Vector3dm();
		
		float rightX, topY, leftX, bottomY;
		
		public FragmentData(float x, float y, float z, int data)
		{
			super(x, y, z);
			int id = VoxelFormat.id(data);
			
			tex = VoxelsStore.get().getVoxelById(id).getVoxelTexture(data, VoxelSides.LEFT, null);
			setData(data);
			//System.out.println("id+"+id + " "+ tex.atlasOffset / 32768f);
		}
		
		public void setVelocity(Vector3dm vel)
		{
			this.vel = vel;
			this.add(vel.castToSinglePrecision());
		}

		@Override
		public float getTextureCoordinateXTopLeft()
		{
			return leftX;
			//return tex.atlasS / 32768f;
		}

		@Override
		public float getTextureCoordinateXTopRight()
		{
			return rightX;
			//return (tex.atlasS + tex.atlasOffset) / 32768f;
		}

		@Override
		public float getTextureCoordinateXBottomLeft()
		{
			return leftX;
			//return tex.atlasS / 32768f;
		}

		@Override
		public float getTextureCoordinateXBottomRight()
		{
			return rightX;
			//return (tex.atlasS + tex.atlasOffset) / 32768f;
		}

		@Override
		public float getTextureCoordinateYTopLeft()
		{
			return topY;
		}

		@Override
		public float getTextureCoordinateYTopRight()
		{
			return topY;
		}

		@Override
		public float getTextureCoordinateYBottomLeft()
		{
			return bottomY;
		}

		@Override
		public float getTextureCoordinateYBottomRight()
		{
			return bottomY;
		}

		void setData(int data)
		{
			int id = VoxelFormat.id(data);
			VoxelTexture tex = VoxelsStore.get().getVoxelById(id).getVoxelTexture(data, VoxelSides.LEFT, null);
			
			int qx = (int) Math.floor(Math.random() * 4.0);
			int rx = qx + 1;
			int qy = (int) Math.floor(Math.random() * 4.0);
			int ry = qy + 1;
			
			//System.out.println("qx:"+qx+"rx:"+rx);
			
			//leftX = (tex.atlasS + tex.atlasOffset) / 32768f;
			leftX = (tex.getAtlasS()) / 32768f + tex.getAtlasOffset() / 32768f * (qx / 4.0f);
			rightX = (tex.getAtlasS()) / 32768f + tex.getAtlasOffset() / 32768f * (rx / 4.0f);
			

			topY = (tex.getAtlasT()) / 32768f + tex.getAtlasOffset() / 32768f * (qy / 4.0f);
			bottomY = (tex.getAtlasT()) / 32768f + tex.getAtlasOffset() / 32768f * (ry / 4.0f);
			
			//topY = (tex.atlasT + tex.atlasOffset) / 32768f;
			//bottomY = (tex.atlasT) / 32768f;
		}
	}

	@Override
	public ParticleData createNew(World world, float x, float y, float z)
	{
		return new FragmentData(x, y, z, world.getVoxelData((int)x, (int)y, (int)z));
	}

	@Override
	public void forEach_Physics(World world, ParticleData data)
	{
		FragmentData b = (FragmentData) data;
		
		b.timer--;
		b.setX((float) (b.getX() + b.vel.getX()));
		b.setY((float) (b.getY() + b.vel.getY()));
		b.setZ((float) (b.getZ() + b.vel.getZ()));
		
		if (!((WorldImplementation) world).checkCollisionPoint(b.getX(), b.getY() - 0.1, b.getZ()))
			b.vel.setY(b.vel.getY() + -0.89/60.0);
		else
			b.vel.set(0d, 0d, 0d);
		
		// 60th square of 0.5
		b.vel.scale(0.98581402);
		if(b.vel.length() < 0.1/60.0)
			b.vel.set(0d, 0d, 0d);
		
		if(b.timer < 0)
			b.destroy();
	}
	
	@Override
	public ParticleTypeRenderer getRenderer(ParticlesRenderer particlesRenderer) {
		return new ParticleTypeRenderer(particlesRenderer) {
			
			@Override
			public Texture2D getAlbedoTexture()
			{
				return particlesRenderer.getContent().voxels().textures().getDiffuseAtlasTexture();
			}
			
			@Override
			public void forEach_Rendering(RenderingInterface renderingContext, ParticleData data)
			{
				// TODO Auto-generated method stub
				
			}

			@Override
			public void destroy() {
				
			}
			
		};
	}
}