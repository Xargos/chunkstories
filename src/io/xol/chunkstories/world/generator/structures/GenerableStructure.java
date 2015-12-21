package io.xol.chunkstories.world.generator.structures;

import io.xol.chunkstories.world.CubicChunk;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public abstract class GenerableStructure
{
	// Trees, houses and small dungeons will be generated with this class
	// it basically is a container for a drawing method that has to apply to
	// multiple chunks
	int oriX, oriY, oriZ;

	public GenerableStructure(int x, int y, int z)
	{
		oriX = x;
		oriY = y;
		oriZ = z;
	}

	public abstract void draw(CubicChunk c);
	// Draws as much as it can iside the chunk

}
