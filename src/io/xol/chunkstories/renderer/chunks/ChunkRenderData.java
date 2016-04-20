package io.xol.chunkstories.renderer.chunks;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.xol.chunkstories.renderer.debug.OverlayRenderer.GL_LINES;
import static io.xol.chunkstories.renderer.debug.OverlayRenderer.GL_TEXTURE_2D;
import static io.xol.chunkstories.renderer.debug.OverlayRenderer.glBegin;
import static io.xol.chunkstories.renderer.debug.OverlayRenderer.glColor4f;
import static io.xol.chunkstories.renderer.debug.OverlayRenderer.glEnable;
import static io.xol.chunkstories.renderer.debug.OverlayRenderer.glEnd;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL15.*;
import io.xol.chunkstories.renderer.SelectionRenderer;
import io.xol.chunkstories.renderer.buffers.ByteBufferPool;
import io.xol.chunkstories.world.chunk.CubicChunk;
import io.xol.engine.model.RenderingContext;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

/**
 * Responsible of holding all rendering information about one chunk
 * ie : VBO creation, uploading and deletion, as well as decals
 * @author Hugo
 *
 */
public class ChunkRenderData
{
	public CubicChunk chunk;
	public int vboId = -1;
	
	public int vboSizeFullBlocks;
	public int vboSizeWaterBlocks;
	public int vboSizeCustomBlocks;
	
	public ByteBufferPool pool;
	public int byteBufferPoolId = -1;
	
	public boolean isUploaded = false;
	
	public ChunkRenderData(ByteBufferPool pool, CubicChunk chunk)
	{
		this.pool = pool;
		this.chunk = chunk;
	}
	
	public boolean isUploaded()
	{
		return isUploaded;
	}
	
	/**
	 * Uploads the ByteBuffer contents and frees it
	 */
	public void upload()
	{
		//Check VBO exists
		if (vboId == -1)
			vboId = glGenBuffers();
		
		//Upload data
		glBindBuffer(GL_ARRAY_BUFFER, vboId);
		glBufferData(GL_ARRAY_BUFFER, pool.accessByteBuffer(byteBufferPoolId), GL_STATIC_DRAW);

		//Release BB
		pool.releaseByteBuffer(byteBufferPoolId);
		byteBufferPoolId = -1;
		
		isUploaded = true;
	}
	
	/**
	 * Frees the ressources allocated to this ChunkRenderData
	 */
	public void free()
	{
		//Make sure we freed the byteBuffer
		if(byteBufferPoolId != -1)
			pool.releaseByteBuffer(byteBufferPoolId);
		byteBufferPoolId = -1;
		//Deallocate the VBO
		if(vboId != -1)
			glDeleteBuffers(vboId);
	}
	
	/**
	 * Thread-safe way to free the ressources
	 */
	public void markForDeletion()
	{
		addToDeletionQueue(this);
	}

	/**
	 * Get the VRAM usage of this chunk in bytes
	 * @return
	 */
	public long getVramUsage()
	{
		return vboSizeFullBlocks * 16 + vboSizeWaterBlocks * 24 + vboSizeCustomBlocks * 24;
	}
	
	// End class instance code, begin static de-allocation functions
	
	public static Set<ChunkRenderData> uselessChunkRenderDatas = ConcurrentHashMap.newKeySet();
	
	public static void deleteUselessVBOs()
	{
		Iterator<ChunkRenderData> i = uselessChunkRenderDatas.iterator();
		while(i.hasNext())
		{
			ChunkRenderData crd = i.next();
			crd.free();
			i.remove();
		}
	}
	
	public static void addToDeletionQueue(ChunkRenderData crd)
	{
		uselessChunkRenderDatas.add(crd);
	}

	public int renderCubeSolidBlocks(RenderingContext renderingContext)
	{
		if (this.vboSizeFullBlocks > 0)
		{
			// We're going back to interlaced format
			// Raw blocks ( integer faces ) alignment :
			// Vertex data : [VERTEX_POS(4b)][TEXCOORD(4b)][COLORS(4b)][NORMALS(4b)] Stride 16 bits
			renderingContext.setVertexAttributePointer("vertexIn", 4, GL_UNSIGNED_BYTE, false, 16, 0);
			renderingContext.setVertexAttributePointer("texCoordIn", 2, GL_UNSIGNED_SHORT, false, 16, 4);
			renderingContext.setVertexAttributePointer("colorIn", 4, GL_UNSIGNED_BYTE, true, 16, 8);
			renderingContext.setVertexAttributePointer("normalIn", 4, GL_UNSIGNED_INT_2_10_10_10_REV, true, 16, 12);
			glDrawArrays(GL_TRIANGLES, 0, vboSizeFullBlocks);
			return vboSizeFullBlocks;
		}
		return 0;
	}
	
	public int renderCustomSolidBlocks(RenderingContext renderingContext)
	{
		if (this.vboSizeCustomBlocks > 0)
		{
			int dekal = this.vboSizeFullBlocks * 16 + this.vboSizeWaterBlocks * 24;
			// We're going back to interlaced format
			// Complex blocks ( integer faces ) alignment :
			// Vertex data : [VERTEX_POS(12b)][TEXCOORD(4b)][COLORS(4b)][NORMALS(4b)] Stride 24 bits
			renderingContext.setVertexAttributePointer("vertexIn", 3, GL_FLOAT, false, 24, dekal + 0);
			renderingContext.setVertexAttributePointer("texCoordIn", 2, GL_UNSIGNED_SHORT, false, 24, dekal + 12);
			renderingContext.setVertexAttributePointer("colorIn", 4, GL_UNSIGNED_BYTE, true, 24, dekal + 16);
			renderingContext.setVertexAttributePointer("normalIn", 4, GL_UNSIGNED_INT_2_10_10_10_REV, true, 24, dekal + 20);
			glDrawArrays(GL_TRIANGLES, 0, vboSizeCustomBlocks);
			return vboSizeCustomBlocks;
		}
		return 0;
	}
	
	public int renderWaterBlocks(RenderingContext renderingContext)
	{
		if (this.vboSizeWaterBlocks > 0)
		{
			int dekal = this.vboSizeFullBlocks * 16;
			// We're going back to interlaced format
			// Complex blocks ( integer faces ) alignment :
			// Vertex data : [VERTEX_POS(12b)][TEXCOORD(4b)][COLORS(4b)][NORMALS(4b)] Stride 24 bits
			renderingContext.setVertexAttributePointer("vertexIn", 3, GL_FLOAT, false, 24, dekal + 0);
			renderingContext.setVertexAttributePointer("texCoordIn", 2, GL_UNSIGNED_SHORT, false, 24, dekal + 12);
			renderingContext.setVertexAttributePointer("colorIn", 4, GL_UNSIGNED_BYTE, true, 24, dekal + 16);
			renderingContext.setVertexAttributePointer("normalIn", 4, GL_UNSIGNED_INT_2_10_10_10_REV, true, 24, dekal + 20);
			glDrawArrays(GL_TRIANGLES, 0, vboSizeWaterBlocks);
			return vboSizeWaterBlocks;
		}
		return 0;
	}
	
	public void renderChunkBounds(RenderingContext renderingContext)
	{
		SelectionRenderer.cubeVertices(chunk.chunkX * 32 + 16, chunk.chunkY * 32, chunk.chunkZ * 32 + 16, 32, 32, 32);
	}
}