//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.voxel;

import org.joml.Vector4f;

import xyz.chunkstories.api.content.Asset;
import xyz.chunkstories.api.voxel.textures.VoxelTexture;

public class VoxelTextureAtlased implements VoxelTexture {
	private final String name;
	private final Asset asset;

	private final int id;

	public int imageFileDimensions;
	private int textureScale = 1;

	private int atlasOffset;
	private int atlasT;
	private int atlasS;

	private Vector4f color;
	public int positionInColorIndex;

	public VoxelTextureAtlased(String name, Asset a, int id) throws Exception {
		this.name = name;
		this.id = id;
		this.asset = a;
		if (name == null)
			throw new Exception("Unnamed VoxelTextureAtlased exception");
	}

	@Override
	public String getName() {
		return name;
	}

	public Asset getAsset() {
		return asset;
	}

	public int getId() {
		return id;
	}

	@Override
	public int getTextureScale() {
		return textureScale;
	}

	public void setTextureScale(int textureScale) {
		this.textureScale = textureScale;
	}

	@Override
	public int getAtlasOffset() {
		return atlasOffset;
	}

	public void setAtlasOffset(int atlasOffset) {
		this.atlasOffset = atlasOffset;
	}

	@Override
	public int getAtlasT() {
		return atlasT;
	}

	public void setAtlasT(int atlasT) {
		this.atlasT = atlasT;
	}

	@Override
	public int getAtlasS() {
		return atlasS;
	}

	public void setAtlasS(int atlasS) {
		this.atlasS = atlasS;
	}

	@Override
	public Vector4f getColor() {
		return color;
	}

	public void setColor(Vector4f color) {
		this.color = color;
	}

	@Override
	public int getAnimationFrames() {
		// TODO implement animated textures
		return 1;
	}
}