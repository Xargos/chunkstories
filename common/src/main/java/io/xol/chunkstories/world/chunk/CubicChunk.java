package io.xol.chunkstories.world.chunk;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.events.voxel.WorldModificationCause;
import io.xol.chunkstories.api.exceptions.world.WorldException;
import io.xol.chunkstories.api.math.LoopingMathHelper;
import io.xol.chunkstories.api.net.packets.PacketVoxelUpdate;
import io.xol.chunkstories.api.player.Player;

import org.joml.Vector3dc;

import com.carrotsearch.hppc.IntArrayDeque;
import com.carrotsearch.hppc.IntDeque;

import io.xol.chunkstories.api.rendering.world.ChunkRenderable;
import io.xol.chunkstories.api.server.RemotePlayer;
import io.xol.chunkstories.api.util.IterableIterator;
import io.xol.chunkstories.api.util.IterableIteratorWrapper;
import io.xol.chunkstories.api.voxel.Voxel;
import io.xol.chunkstories.api.voxel.VoxelFormat;
import io.xol.chunkstories.api.voxel.VoxelLogic;
import io.xol.chunkstories.api.voxel.VoxelSides;
import io.xol.chunkstories.api.voxel.components.VoxelComponent;
import io.xol.chunkstories.api.voxel.components.VoxelComponents;
import io.xol.chunkstories.api.world.EditableVoxelContext;
import io.xol.chunkstories.api.world.World;
import io.xol.chunkstories.api.world.WorldClient;
import io.xol.chunkstories.api.world.WorldMaster;
import io.xol.chunkstories.api.world.chunk.Chunk;
import io.xol.chunkstories.api.world.chunk.ChunkLightUpdater;
import io.xol.chunkstories.api.world.chunk.Region;
import io.xol.chunkstories.api.world.chunk.WorldUser;
import io.xol.chunkstories.entity.EntitySerializer;
import io.xol.chunkstories.renderer.chunks.ClientChunkLightBaker;
import io.xol.chunkstories.tools.WorldTool;
import io.xol.chunkstories.voxel.VoxelsStore;
import io.xol.chunkstories.voxel.components.VoxelComponentsHolder;
import io.xol.chunkstories.world.WorldImplementation;
import io.xol.chunkstories.world.region.RegionImplementation;
import io.xol.engine.concurrency.SimpleLock;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

//(c) 2015-2017 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

/**
 * Essential class that holds actual chunk voxel data, entities and voxel component !
 */
//TODO Move ALL the lightning crap away !!!
public class CubicChunk implements Chunk
{
	final protected WorldImplementation world;
	final protected RegionImplementation holdingRegion;
	final protected ChunkHolderImplementation chunkHolder;
	
	final protected int chunkX;
	final protected int chunkY;
	final protected int chunkZ;
	final protected int uuid;

	//Actual data holding here
	public int[] chunkVoxelData = null;
	
	//Count unsaved edits atomically, fancy :]
	public final AtomicInteger compr_uncomittedBlockModifications = new AtomicInteger();
	
	public final ChunkOcclusionUpdater occlusion = new ChunkOcclusionUpdater(this);
	//public final AtomicInteger occl_compr_uncomittedBlockModifications = new AtomicInteger();
	
	public final ClientChunkLightBaker lightBakingStatus = new ClientChunkLightBaker(this);
	
	protected final Map<Integer, VoxelComponentsHolder> voxelComponents = new HashMap<Integer, VoxelComponentsHolder>();
	protected final Set<Entity> localEntities = ConcurrentHashMap.newKeySet();

	protected final SimpleLock componentsLock = new SimpleLock();
	protected final SimpleLock entitiesLock = new SimpleLock();

	static final int sunlightMask = 0x000F0000;
	static final int blocklightMask = 0x00F00000;
	
	static final int sunAntiMask = 0xFFF0FFFF;
	static final int blockAntiMask = 0xFF0FFFFF;

	static final int sunBitshift = 0x10;
	static final int blockBitshift = 0x14;
	
	private Semaphore chunkDataArrayCreation = new Semaphore(1);
	
	//These wonderfull things does magic for us, they are unique per-thread so they won't ever clog memory neither will they have contigency issues
	//Seriously awesome
	static ThreadLocal<IntDeque> blockSources = new ThreadLocal<IntDeque>()
	{
		@Override
		protected IntDeque initialValue()
		{
			return new IntArrayDeque();
		}
	};
	static ThreadLocal<IntDeque> sunSources = new ThreadLocal<IntDeque>()
	{
		@Override
		protected IntDeque initialValue()
		{
			return new IntArrayDeque();
		}
	};
	static ThreadLocal<IntDeque> blockSourcesRemoval = new ThreadLocal<IntDeque>()
	{
		@Override
		protected IntDeque initialValue()
		{
			return new IntArrayDeque();
		}
	};
	static ThreadLocal<IntDeque> sunSourcesRemoval = new ThreadLocal<IntDeque>()
	{
		@Override
		protected IntDeque initialValue()
		{
			return new IntArrayDeque();
		}
	};

	public CubicChunk(ChunkHolderImplementation holder, int chunkX, int chunkY, int chunkZ)
	{
		this(holder, chunkX, chunkY, chunkZ, null);
	}

	public CubicChunk(ChunkHolderImplementation holder, int chunkX, int chunkY, int chunkZ, CompressedData data)
	{
		this.chunkHolder = holder;
		
		this.holdingRegion = holder.getRegion();
		this.world = holdingRegion.getWorld();
		
		this.chunkX = chunkX;
		this.chunkY = chunkY;
		this.chunkZ = chunkZ;
		
		this.uuid = ((chunkX << world.getWorldInfo().getSize().bitlengthOfVerticalChunksCoordinates) | chunkY ) << world.getWorldInfo().getSize().bitlengthOfHorizontalChunksCoordinates | chunkZ;

		if(data != null ) {
			try {
				this.chunkVoxelData = data.getVoxelData();
				
				if(data.voxelComponentsCompressedData != null) {
					ByteArrayInputStream bais = new ByteArrayInputStream(data.voxelComponentsCompressedData);
					DataInputStream dis = new DataInputStream(bais);
	
					byte[] smallArray = new byte[4096];
					ByteArrayInputStream bias = new ByteArrayInputStream(smallArray);
					DataInputStream dias = new DataInputStream(bias);
					
					byte keepGoing = dis.readByte();
					while(keepGoing != 0x00) {
						int index = dis.readInt();
						VoxelComponentsHolder components = new VoxelComponentsHolder(this, index);
						voxelComponents.put(index, components);
						
						//System.out.println("at index: " + index + " coords: (" + components.getX() + ", " + components.getY() + ", " + components.getZ() + ")");
						
						String componentName = dis.readUTF();
						while(!componentName.equals("\n")) {
							//System.out.println("componentName: "+componentName);
							
							//Read however many bytes this component wrote
							int bytes = dis.readShort();
							dis.readFully(smallArray, 0, bytes);
							
							//Call the block's onPlace method as to make it spawn the necessary components
							ChunkVoxelContext peek = peek(components.getX(), components.getY(), components.getZ());
							if(peek.getVoxel() instanceof VoxelLogic) {
								((VoxelLogic)peek.getVoxel()).onPlace(peek, peek.getData(), null);
								
								VoxelComponent component = components.get(componentName);
								if(component == null) {
									System.out.println("Error, a component named " + componentName + " was saved, but it was not recreated by the voxel onPlace() method.");
								}
								else {
									//Hope for the best
									component.pull(holder.getRegion().handler, dias);
								}
							}
							
							dias.reset();
							
							componentName = dis.readUTF();
						}
						keepGoing = dis.readByte();
					}
				}
				
				if(data.entitiesCompressedData != null) {
					ByteArrayInputStream bais = new ByteArrayInputStream(data.entitiesCompressedData);
					DataInputStream dis = new DataInputStream(bais);
	
					//Read entities until we hit -1
					Entity entity = null;
					do
					{
						entity = EntitySerializer.readEntityFromStream(dis, holder.getRegion().handler, world);
						if (entity != null) {
							this.addEntity(entity);
							world.addEntity(entity);
						}
					}
					while (entity != null);
				}
			} catch (UnloadableChunkDataException | IOException | WorldException e) {
		
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
		
		// Send chunk to whoever already subscribed
		if(data == null) 
			data = new CompressedData(null, null, null);
	}
	
	public int getChunkX()
	{
		return chunkX;
	}

	public int getChunkY()
	{
		return chunkY;
	}

	public int getChunkZ()
	{
		return chunkZ;
	}

	private int sanitizeCoordinate(int a)
	{
		return a & 0x1F;
	}

	@Override
	public int peekSimple(int x, int y, int z)
	{
		if (chunkVoxelData == null)
			return 0;
		else
		{
			x = sanitizeCoordinate(x);
			y = sanitizeCoordinate(y);
			z = sanitizeCoordinate(z);
			return chunkVoxelData[x * 32 * 32 + y * 32 + z];
		}
	}
	
	@Override
	public ActualChunkVoxelContext peek(Vector3dc location)
	{
		return peek((int)(double)location.x(), (int)(double)location.y(), (int)(double)location.z());
	}

	@Override
	public ActualChunkVoxelContext peek(int x, int y, int z)
	{
		return new ActualChunkVoxelContext(x, y, z, peekSimple(x, y, z));
	}

	@Override
	public ChunkVoxelContext poke(int x, int y, int z, int newVoxelData, WorldModificationCause cause)
			throws WorldException {
		return pokeInternal(x, y, z, newVoxelData, true, true, cause);
	}

	@Override
	public ChunkVoxelContext pokeSilently(int x, int y, int z, int newVoxelData) throws WorldException {
		return pokeInternal(x, y, z, newVoxelData, false, true, null);
	}

	@Override
	public void pokeSimple(int x, int y, int z, int newVoxelData) {
		pokeInternal(x, y, z, newVoxelData, true, false, null);
	}

	@Override
	public void pokeSimpleSilently(int x, int y, int z, int newVoxelData) {
		pokeInternal(x, y, z, newVoxelData, false, false, null);
	}

	@Override
	public VoxelComponentsHolder components(int worldX, int worldY, int worldZ) {
		int index = worldX * 1024 + worldY * 32 + worldZ;
		
		VoxelComponentsHolder components = voxelComponents.get(index);
		if(components == null) {
			components = new VoxelComponentsHolder(this, index);
			voxelComponents.put(index, components);
		}
		return components;
	}

	public void removeComponents(int index) {
		voxelComponents.remove(index);
	}
	
	/** 
	 * The 'core' of the core, this private function is responsible for placing and keeping everyone up to snuff on block modifications.
	 *  It all comes back to this really. 
	 */
	private ActualChunkVoxelContext pokeInternal(int worldX, int worldY, int worldZ, int newData, boolean update, boolean returnContext, WorldModificationCause cause)
	{
		int x = sanitizeCoordinate(worldX);
		int y = sanitizeCoordinate(worldY);
		int z = sanitizeCoordinate(worldZ);
		
		ActualChunkVoxelContext peek = peek(x, y, z);
		Voxel formerVoxel = peek.getVoxel();
		Voxel newVoxel = VoxelsStore.get().getVoxelById(newData);

		try
		{
			//If we're merely changing the voxel meta 
			if (formerVoxel != null && newVoxel != null && formerVoxel.equals(newVoxel))
			{
				//Optionally runs whatever the voxel requires to run when modified
				if (formerVoxel instanceof VoxelLogic)
					newData = ((VoxelLogic) formerVoxel).onModification(peek, newData, cause);
			}
			else
			{
				//Optionally runs whatever the voxel requires to run when removed
				if (formerVoxel instanceof VoxelLogic)
					((VoxelLogic) formerVoxel).onRemove(peek, cause);

				//Optionally runs whatever the voxel requires to run when placed
				if (newVoxel instanceof VoxelLogic)
					newData = ((VoxelLogic) newVoxel).onPlace(peek, newData, cause);
			}
			
			//Allocate if it makes sense
			if (chunkVoxelData == null)
				chunkVoxelData = atomicalyCreateInternalData();
	
			int dataBefore = chunkVoxelData[x * 32 * 32 + y * 32 + z];
			chunkVoxelData[x * 32 * 32 + y * 32 + z] = newData;
			
			//Update lightning
			if(update)
				computeLightSpread(x, y, z, dataBefore, newData);
			
			//Increment the modifications counter
			compr_uncomittedBlockModifications.incrementAndGet();
			
			//Don't spam the thread creation spawn
			occlusion.unbakedUpdates.incrementAndGet();
			//occl_compr_uncomittedBlockModifications.incrementAndGet();
			
			//Update related summary
			if(update)
				world.getRegionsSummariesHolder().updateOnBlockPlaced(x, y, z, newData);
	
			//Mark the nearby chunks to be re-rendered
			if (update && dataBefore != newData) {
				int sx = chunkX; int ex = sx;
				int sy = chunkY; int ey = sy;
				int sz = chunkZ; int ez = sz;
				
				if(x == 0)
					sx--;
				else if(x == 31)
					ex++;
				
				if(y == 0)
					sy--;
				else if(y == 31)
					ey++;
				
				if(z == 0)
					sz--;
				else if(z == 31)
					ez++;
				
				for(int ix = sx; ix <= ex; ix++)
					for(int iy = sy; iy <= ey; iy++)
						for(int iz = sz; iz <= ez; iz++)
						{
							Chunk chunk = world.getChunk(ix, iy, iz);
							if(chunk != null && chunk instanceof ChunkRenderable)
								((ChunkRenderable) chunk).meshUpdater().requestMeshUpdate();
						}
			}
			
			// If this is a 'master' world, notify remote users of the change !
			if(update && world instanceof WorldMaster && !(world instanceof WorldTool))
			{
				PacketVoxelUpdate packet = new PacketVoxelUpdate(new ActualChunkVoxelContext(chunkX * 32 + x, chunkY * 32 + y, chunkZ * 32 + z, newData));
				
				Iterator<WorldUser> pi = this.chunkHolder.users.iterator();
				while (pi.hasNext())
				{
					WorldUser user = pi.next();
					if(!(user instanceof RemotePlayer))
						continue;
					
					RemotePlayer player = (RemotePlayer)user;

					Entity clientEntity = player.getControlledEntity();
					if (clientEntity == null)
						continue;
					
					player.pushPacket(packet);

				}
			}

		}
		//If it is stopped, don't try to go further
		catch (WorldException illegal)
		{
			if(returnContext)
				return peek;
		}
		
		if(returnContext)
			return new ActualChunkVoxelContext(chunkX * 32 + x, chunkY * 32 + y, chunkZ * 32 + z, newData);
		else
			return null;
	}

	private int[] atomicalyCreateInternalData() {
		chunkDataArrayCreation.acquireUninterruptibly();

		//If it's STILL null
		if (chunkVoxelData == null)
			chunkVoxelData = new int[32 * 32 * 32];
		
		chunkDataArrayCreation.release();
		
		return chunkVoxelData;
	}

	@Override
	public String toString()
	{
		return "[CubicChunk x:" + this.chunkX + " y:" + this.chunkY + " z:" + this.chunkZ + " air:" + isAirChunk() + " lS:" + this.lightBakingStatus + "]";
	}
	
	public int computeVoxelLightningInternal(boolean adjacent)
	{
		// Checks first if chunk contains blocks
		if (chunkVoxelData == null)
			return 0; // Nothing to do

		//Lock the chunk & grab 2 queues
		IntDeque blockSources = CubicChunk.blockSources.get();
		IntDeque sunSources = CubicChunk.sunSources.get();

		// Reset any remnant data
		blockSources.clear();
		sunSources.clear();

		// Find our own light sources, add them
		this.addChunkLightSources(blockSources, sunSources);

		int mods = 0;
		
		// Load nearby chunks and check if they contain bright spots we haven't accounted for yet
		if (adjacent)
			mods += addAdjacentChunksLightSources(blockSources, sunSources);

		//Propagates the light
		mods += propagateLightning(blockSources, sunSources);

		return mods;
	}

	// Now entering lightning code part, brace yourselves
	private int propagateLightning(IntDeque blockSources, IntDeque sunSources)
	{
		int modifiedBlocks = 0;

		//Checks if the adjacent chunks are done loading
		Chunk adjacentChunkTop = world.getChunk(chunkX, chunkY + 1, chunkZ);
		Chunk adjacentChunkBottom = world.getChunk(chunkX, chunkY - 1, chunkZ);
		Chunk adjacentChunkFront = world.getChunk(chunkX, chunkY, chunkZ + 1);
		Chunk adjacentChunkBack = world.getChunk(chunkX, chunkY, chunkZ - 1);
		Chunk adjacentChunkLeft = world.getChunk(chunkX - 1, chunkY, chunkZ);
		Chunk adjacentChunkRight = world.getChunk(chunkX + 1, chunkY, chunkZ);
		
		//Don't spam the requeue requests
		boolean checkTopBleeding = (adjacentChunkTop != null);// && !adjacentChunkTop.needsLightningUpdates();
		boolean checkBottomBleeding = (adjacentChunkBottom != null);// && !adjacentChunkBottom.needsLightningUpdates();
		boolean checkFrontBleeding = (adjacentChunkFront != null);// && !adjacentChunkFront.needsLightningUpdates();
		boolean checkBackBleeding = (adjacentChunkBack != null);// && !adjacentChunkBack.needsLightningUpdates();
		boolean checkLeftBleeding = (adjacentChunkLeft != null);// && !adjacentChunkLeft.needsLightningUpdates();
		boolean checkRightBleeding = (adjacentChunkRight != null);// && !adjacentChunkRight.needsLightningUpdates();
		
		boolean requestTop = false;
		boolean requestBot = false;
		boolean requestFront = false;
		boolean requestBack = false;
		boolean requestLeft = false;
		boolean requestRight = false;
		
		Voxel voxel;
		while (blockSources.size() > 0)
		{
			int y = blockSources.removeLast();
			int z = blockSources.removeLast();
			int x = blockSources.removeLast();
			
			int voxelData = chunkVoxelData[x * 1024 + y * 32 + z];
			int cellLightLevel = (voxelData & blocklightMask) >> blockBitshift;
			int cId = VoxelFormat.id(voxelData);
			voxel = VoxelsStore.get().getVoxelById(cId);

			if (VoxelsStore.get().getVoxelById(cId).getType().isOpaque())
				cellLightLevel = voxel.getLightLevel(voxelData);
			if (cellLightLevel > 1)
			{
				if (x < 31)
				{
					int rightData = chunkVoxelData[(x + 1) * 1024 + y * 32 + z];
					int llRight = cellLightLevel - voxel.getLightLevelModifier(voxelData, rightData, VoxelSides.RIGHT);
					if (!VoxelsStore.get().getVoxelById((rightData & 0xFFFF)).getType().isOpaque() && ((rightData & blocklightMask) >> blockBitshift) < llRight - 1)
					{
						chunkVoxelData[(x + 1) * 1024 + y * 32 + z] = rightData & blockAntiMask | (llRight - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x + 1);
						blockSources.addLast(z);
						blockSources.addLast(y);
					}
				}
				else if (checkRightBleeding)
				{
					int rightData = adjacentChunkRight.peekSimple(0, y, z);
					int llRight = cellLightLevel - voxel.getLightLevelModifier(voxelData, rightData, VoxelSides.RIGHT);
					if (((rightData & blocklightMask) >> blockBitshift) < llRight - 1)
					{
						requestRight = true;
						checkRightBleeding = false;
					}
				}
				if (x > 0)
				{
					int leftData = chunkVoxelData[(x - 1) * 1024 + y * 32 + z];
					int llLeft = cellLightLevel - voxel.getLightLevelModifier(voxelData, leftData, VoxelSides.LEFT);
					if (!VoxelsStore.get().getVoxelById((leftData & 0xFFFF)).getType().isOpaque() && ((leftData & blocklightMask) >> blockBitshift) < llLeft - 1)
					{
						chunkVoxelData[(x - 1) * 1024 + y * 32 + z] = leftData & blockAntiMask | (llLeft - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x - 1);
						blockSources.addLast(z);
						blockSources.addLast(y);
					}
				}
				else if (checkLeftBleeding)
				{
					int leftData = adjacentChunkLeft.peekSimple(31, y, z);
					int llLeft = cellLightLevel - voxel.getLightLevelModifier(voxelData, leftData, VoxelSides.LEFT);
					if (((leftData & blocklightMask) >> blockBitshift) < llLeft - 1)
					{
						requestLeft = true;
						checkLeftBleeding = false;
					}
				}

				if (z < 31)
				{
					int frontData = chunkVoxelData[x * 1024 + y * 32 + z + 1];
					int llFront = cellLightLevel - voxel.getLightLevelModifier(voxelData, frontData, VoxelSides.FRONT);
					if (!VoxelsStore.get().getVoxelById((frontData & 0xFFFF)).getType().isOpaque() && ((frontData & blocklightMask) >> blockBitshift) < llFront - 1)
					{
						chunkVoxelData[x * 1024 + y * 32 + z + 1] = frontData & blockAntiMask | (llFront - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z + 1);
						blockSources.addLast(y);
					}
				}
				else if (checkFrontBleeding)
				{
					int frontData = adjacentChunkFront.peekSimple(x, y, 0);
					int llFront = cellLightLevel - voxel.getLightLevelModifier(voxelData, frontData, VoxelSides.FRONT);
					if (((frontData & blocklightMask) >> blockBitshift) < llFront - 1)
					{
						requestFront = true;
						checkFrontBleeding = false;
					}
				}
				if (z > 0)
				{
					int backData = chunkVoxelData[x * 1024 + y * 32 + z - 1];
					int llBack = cellLightLevel - voxel.getLightLevelModifier(voxelData, backData, VoxelSides.BACK);
					if (!VoxelsStore.get().getVoxelById((backData & 0xFFFF)).getType().isOpaque() && ((backData & blocklightMask) >> blockBitshift) < llBack - 1)
					{
						chunkVoxelData[x * 1024 + y * 32 + z - 1] = backData & blockAntiMask | (llBack - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z - 1);
						blockSources.addLast(y);
					}
				}
				else if (checkBackBleeding)
				{
					int backData = adjacentChunkBack.peekSimple(x, y, 31);
					int llBack = cellLightLevel - voxel.getLightLevelModifier(voxelData, backData, VoxelSides.BACK);
					if (((backData & blocklightMask) >> blockBitshift) < llBack - 1)
					{
						requestBack = true;
						checkBackBleeding = false;
					}
				}

				if (y < 31)
				{
					int topData = chunkVoxelData[x * 1024 + (y + 1) * 32 + z];
					int llTop = cellLightLevel - voxel.getLightLevelModifier(voxelData, topData, VoxelSides.TOP);
					if (!VoxelsStore.get().getVoxelById((topData & 0xFFFF)).getType().isOpaque() && ((topData & blocklightMask) >> blockBitshift) < llTop - 1)
					{
						chunkVoxelData[x * 1024 + (y + 1) * 32 + z] = topData & blockAntiMask | (llTop - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z);
						blockSources.addLast(y + 1);
					}
				}
				else if (checkTopBleeding)
				{
					int topData = adjacentChunkTop.peekSimple(x, 0, z);
					int llTop = cellLightLevel - voxel.getLightLevelModifier(voxelData, topData, VoxelSides.TOP);
					if (((topData & blocklightMask) >> blockBitshift) < llTop - 1)
					{
						requestTop = true;
						checkTopBleeding = false;
					}
				}
				
				if (y > 0)
				{
					int belowData = chunkVoxelData[x * 1024 + (y - 1) * 32 + z];
					int llTop = cellLightLevel - voxel.getLightLevelModifier(voxelData, belowData, VoxelSides.BOTTOM);
					
					if (!VoxelsStore.get().getVoxelById((belowData & 0xFFFF)).getType().isOpaque() && ((belowData & blocklightMask) >> blockBitshift) < llTop - 1)
					//if (!VoxelsStore.get().getVoxelById((belowData & 0xFFFF)).getType().isOpaque() && ((belowData & blocklightMask) >> blockBitshift) < cellLightLevel - 1)
					{
						chunkVoxelData[x * 1024 + (y - 1) * 32 + z] = belowData & blockAntiMask | (llTop - 1) << blockBitshift;
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z);
						blockSources.addLast(y - 1);
					}
				}
				else if (checkBottomBleeding)
				{
					int belowData = adjacentChunkBottom.peekSimple(x, 31, z);
					int llBottom = cellLightLevel - voxel.getLightLevelModifier(voxelData, belowData, VoxelSides.BOTTOM);
					
					if(((belowData & blocklightMask) >> blockBitshift) < llBottom - 1)
					//int adjacentBlocklight = ( & blockAntiMask) << blockBitshift;
					//if (cellLightLevel - 1 > adjacentBlocklight)
					{
						requestBot = true;
						checkBottomBleeding = false;
					}
				}
			}
		}
		
		// Sunlight propagation
		while (sunSources.size() > 0)
		{
			int y = sunSources.removeLast();
			int z = sunSources.removeLast();
			int x = sunSources.removeLast();

			int voxelData = chunkVoxelData[x * 1024 + y * 32 + z];
			int cellLightLevel = (voxelData & sunlightMask) >> sunBitshift;
			int cId = VoxelFormat.id(voxelData);
			voxel = VoxelsStore.get().getVoxelById(cId);

			if (voxel.getType().isOpaque())
				cellLightLevel = 0;
			if (cellLightLevel > 1)
			{
				// X-propagation
				if (x < 31)
				{
					int rightData = chunkVoxelData[(x + 1) * 1024 + y * 32 + z];
					int llRight = cellLightLevel - voxel.getLightLevelModifier(voxelData, rightData, VoxelSides.RIGHT);
					if (!VoxelsStore.get().getVoxelById((rightData & 0xFFFF)).getType().isOpaque() && ((rightData & sunlightMask) >> sunBitshift) < llRight - 1)
					{
						chunkVoxelData[(x + 1) * 1024 + y * 32 + z] = rightData & sunAntiMask | (llRight - 1) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x + 1);
						sunSources.addLast(z);
						sunSources.addLast(y);
					}
				}
				else if (checkRightBleeding)
				{
					int rightData = adjacentChunkRight.peekSimple(0, y, z);
					int llRight = cellLightLevel - voxel.getLightLevelModifier(voxelData, rightData, VoxelSides.RIGHT);
					if (((rightData & sunlightMask) >> sunBitshift) < llRight - 1)
					{
						requestRight = true;
						checkRightBleeding = false;
					}
				}
				if (x > 0)
				{
					int leftData = chunkVoxelData[(x - 1) * 1024 + y * 32 + z];
					int llLeft = cellLightLevel - voxel.getLightLevelModifier(voxelData, leftData, VoxelSides.LEFT);
					if (!VoxelsStore.get().getVoxelById((leftData & 0xFFFF)).getType().isOpaque() && ((leftData & sunlightMask) >> sunBitshift) < llLeft - 1)
					{
						chunkVoxelData[(x - 1) * 1024 + y * 32 + z] = leftData & sunAntiMask | (llLeft - 1) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x - 1);
						sunSources.addLast(z);
						sunSources.addLast(y);
					}
				}
				else if (checkLeftBleeding)
				{
					int leftData = adjacentChunkLeft.peekSimple(31, y, z);
					int llLeft = cellLightLevel - voxel.getLightLevelModifier(voxelData, leftData, VoxelSides.LEFT);
					if (((leftData & sunlightMask) >> sunBitshift) < llLeft - 1)
					{
						requestLeft = true;
						checkLeftBleeding = false;
					}
				}
				
				if (z < 31)
				{
					int frontData = chunkVoxelData[x * 1024 + y * 32 + z + 1];
					int llFront = cellLightLevel - voxel.getLightLevelModifier(voxelData, frontData, VoxelSides.FRONT);
					if (!VoxelsStore.get().getVoxelById((frontData & 0xFFFF)).getType().isOpaque() && ((frontData & sunlightMask) >> sunBitshift) < llFront - 1)
					{
						chunkVoxelData[x * 1024 + y * 32 + z + 1] = frontData & sunAntiMask | (llFront - 1) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z + 1);
						sunSources.addLast(y);
					}
				}
				else if (checkFrontBleeding)
				{
					int frontData = adjacentChunkFront.peekSimple(x, y, 0);
					int llFront = cellLightLevel - voxel.getLightLevelModifier(voxelData, frontData, VoxelSides.FRONT);
					if (((frontData & sunlightMask) >> sunBitshift) < llFront - 1)
					{
						requestFront = true;
						checkFrontBleeding = false;
					}
				}
				if (z > 0)
				{
					int backData = chunkVoxelData[x * 1024 + y * 32 + z - 1];
					int llBack = cellLightLevel - voxel.getLightLevelModifier(voxelData, backData, VoxelSides.BACK);
					if (!VoxelsStore.get().getVoxelById((backData & 0xFFFF)).getType().isOpaque() && ((backData & sunlightMask) >> sunBitshift) < llBack - 1)
					{
						chunkVoxelData[x * 1024 + y * 32 + z - 1] = backData & sunAntiMask | (llBack - 1) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z - 1);
						sunSources.addLast(y);
					}
				}
				else if (checkBackBleeding)
				{
					int backData = adjacentChunkBack.peekSimple(x, y, 31);
					int llBack = cellLightLevel - voxel.getLightLevelModifier(voxelData, backData, VoxelSides.BACK);
					if (((backData & sunlightMask) >> sunBitshift) < llBack - 1)
					{
						requestBack = true;
						checkBackBleeding = false;
					}
				}
				
				if (y < 31)
				{
					int topData = chunkVoxelData[x * 1024 + (y + 1) * 32 + z];
					int llTop = cellLightLevel - voxel.getLightLevelModifier(voxelData, topData, VoxelSides.TOP);
					if (!VoxelsStore.get().getVoxelById((topData & 0xFFFF)).getType().isOpaque() && ((topData & sunlightMask) >> sunBitshift) < llTop - 1)
					{
						chunkVoxelData[x * 1024 + (y + 1) * 32 + z] = topData & sunAntiMask | (llTop - 1) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z);
						sunSources.addLast(y + 1);
					}
				}
				else if (checkTopBleeding)
				{
					int topData = adjacentChunkTop.peekSimple(x, 0, z);
					int llTop = cellLightLevel - voxel.getLightLevelModifier(voxelData, topData, VoxelSides.TOP);
					if (((topData & sunlightMask) >> sunBitshift) < llTop - 1)
					{
						requestTop = true;
						checkTopBleeding = false;
					}
				}
				
				//Special case! This is the bottom computation for light spread, and thus we don't decrement automatially the light by one for each step !
				if (y > 0)
				{
					int belowData = chunkVoxelData[x * 1024 + (y - 1) * 32 + z];
					int llBottom = cellLightLevel - voxel.getLightLevelModifier(voxelData, belowData, VoxelSides.BOTTOM);
					if (!VoxelsStore.get().getVoxelById(belowData).getType().isOpaque() && ((belowData & sunlightMask) >> sunBitshift) < llBottom)
					{
						chunkVoxelData[x * 1024 + (y - 1) * 32 + z] = belowData & sunAntiMask | (llBottom) << sunBitshift;
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z);
						sunSources.addLast(y - 1);
					}
				}
				else if (checkBottomBleeding)
				{
					int belowData = adjacentChunkBottom.peekSimple(x, 31, z);
					int llBottom = cellLightLevel - voxel.getLightLevelModifier(voxelData, belowData, VoxelSides.BOTTOM);
					if (((belowData & sunlightMask) >> sunBitshift) < llBottom)
					{
						requestBot = true;
						checkBottomBleeding = false;
					}
				}
			}
		}
		
		if(requestTop)
			adjacentChunkTop.lightBaker().requestLightningUpdate();
		if(requestBot)
			adjacentChunkBottom.lightBaker().requestLightningUpdate();
		if(requestLeft)
			adjacentChunkLeft.lightBaker().requestLightningUpdate();
		if(requestRight)
			adjacentChunkRight.lightBaker().requestLightningUpdate();
		if(requestBack)
			adjacentChunkBack.lightBaker().requestLightningUpdate();
		if(requestFront)
			adjacentChunkFront.lightBaker().requestLightningUpdate();

		return modifiedBlocks;
	}

	private void addChunkLightSources(IntDeque blockSources, IntDeque sunSources)
	{
		for (int a = 0; a < 32; a++)
			for (int b = 0; b < 32; b++)
			{
				int z = 31; // This is basically wrong since we work with cubic chunks
				boolean hit = false;
				int csh = world.getRegionsSummariesHolder().getHeightAtWorldCoordinates(chunkX * 32 + a, chunkZ * 32 + b) + 1;
				while (z >= 0)
				{
					int block = chunkVoxelData[a * 1024 + z * 32 + b];
					int id = VoxelFormat.id(block);
					short ll = VoxelsStore.get().getVoxelById(id).getLightLevel(block);
					if (ll > 0)
					{
						chunkVoxelData[a * 1024 + z * 32 + b] = chunkVoxelData[a * 1024 + z * 32 + b] & blockAntiMask | ((ll & 0xF) << blockBitshift);
						//blockSources.addLast(a << 16 | b << 8 | z);
						blockSources.addLast(a);
						blockSources.addLast(b);
						blockSources.addLast(z);
					}
					if (!hit)
					{
						if (chunkY * 32 + z >= csh)
						{
							chunkVoxelData[a * 1024 + (z) * 32 + b] = chunkVoxelData[a * 1024 + (z) * 32 + b] & sunAntiMask | (15 << sunBitshift);
							//sunSources.addLast(a << 16 | b << 8 | z);
							sunSources.addLast(a);
							sunSources.addLast(b);
							sunSources.addLast(z);
							if (chunkY * 32 + z < csh || VoxelsStore.get().getVoxelById(VoxelFormat.id(chunkVoxelData[a * 1024 + (z) * 32 + b])).getId() != 0)
							{
								hit = true;
							}
							//check_em++;
						}
					}
					z--;
				}
			}
	}

	private int addAdjacentChunksLightSources(IntDeque blockSources, IntDeque sunSources)
	{
		int mods = 0;
		if (world != null)
		{
			Chunk cc;
			cc = world.getChunk(chunkX + 1, chunkY, chunkZ);
			if (cc != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(0, c, b);
						int current_data = peekSimple(31, c, b);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(31, c, b, ndata);
							mods++;
							blockSources.addLast(31);
							blockSources.addLast(b);
							blockSources.addLast(c);
							//blockSources.addLast(31 << 16 | b << 8 | c);
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(31, c, b, ndata);
							mods++;
							sunSources.addLast(31);
							sunSources.addLast(b);
							sunSources.addLast(c);
							//sunSources.addLast(31 << 16 | b << 8 | c);
						}
					}
			}
			cc = world.getChunk(chunkX - 1, chunkY, chunkZ);
			if (cc != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(31, c, b);
						int current_data = peekSimple(0, c, b);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(0, c, b, ndata);
							mods++;
							blockSources.addLast(0);
							blockSources.addLast(b);
							blockSources.addLast(c);
							//blockSources.addLast(0 << 16 | b << 8 | c);
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(0, c, b, ndata);
							mods++;
							sunSources.addLast(0);
							sunSources.addLast(b);
							sunSources.addLast(c);
							//sunSources.addLast(0 << 16 | b << 8 | c);
						}
					}
			}
			// Top chunk
			cc = world.getChunk(chunkX, chunkY + 1, chunkZ);
			if (cc != null && !cc.isAirChunk())// && chunkVoxelData != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(c, 0, b);
						int current_data = peekSimple(c, 31, b);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(c, 31, b, ndata);
							mods++;
							if (adjacent_blo > 2)
							{
								blockSources.addLast(c);
								blockSources.addLast(b);
								blockSources.addLast(31);
								//blockSources.addLast(c << 16 | b << 8 | 31);
							}
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(c, 31, b, ndata);
							mods++;
							//System.out.println(cc + " : "+adjacent_sun);
							if (adjacent_sun > 2)
							{
								sunSources.addLast(c);
								sunSources.addLast(b);
								sunSources.addLast(31);
								//sunSources.addLast(c << 16 | b << 8 | 31);
							}
						}
					}
			}
			else
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int heightInSummary = world.getRegionsSummariesHolder().getHeightAtWorldCoordinates(chunkX * 32 + b, chunkZ * 32 + c);
						
						//If the top chunk is air
						if(heightInSummary <= this.chunkY * 32 + 32)
						{
							//int adjacent_data = cc.peekSimple(c, 0, b);
							int current_data = peekSimple(c, 31, b);
	
							int adjacent_blo = 0;//((adjacent_data & blocklightMask) >>> blockBitshift);
							int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
							int adjacent_sun = 15;//((adjacent_data & sunlightMask) >>> sunBitshift);
							int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
							if (adjacent_blo > 1 && adjacent_blo > current_blo)
							{
								int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
								pokeSimpleSilently(c, 31, b, ndata);
								mods++;
								if (adjacent_blo > 2)
								{
									blockSources.addLast(c);
									blockSources.addLast(b);
									blockSources.addLast(31);
									//blockSources.addLast(c << 16 | b << 8 | 31);
								}
							}
							if (adjacent_sun > 1 && adjacent_sun > current_sun)
							{
								int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
								pokeSimpleSilently(c, 31, b, ndata);
								mods++;
								//System.out.println(cc + " : "+adjacent_sun);
								if (adjacent_sun > 2)
								{
									sunSources.addLast(c);
									sunSources.addLast(b);
									sunSources.addLast(31);
									//sunSources.addLast(c << 16 | b << 8 | 31);
								}
							}
						}
					}
				
				/*for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int heightInSummary = world.getRegionsSummariesHolder().getHeightAtWorldCoordinates(chunkX * 32 + b, chunkZ * 32 + c);
						// System.out.println("compute "+heightInSummary+" <= ? "+chunkY*32);
						if (heightInSummary <= chunkY * 32 + 32)
						{
							int sourceAt = chunkY * 32 - heightInSummary;
							sourceAt = Math.min(31, sourceAt);
							int current_data = peekSimple(b, sourceAt, c);

							int ndata = current_data & sunAntiMask | (15) << sunBitshift;
							pokeSimple(b, sourceAt, c, ndata);

							sunSources.addLast(b);
							sunSources.addLast(c);
							sunSources.addLast(sourceAt);
							//sunSources.addLast(b << 16 | c << 8 | sourceAt);
							// System.out.println("Added sunsource cause summary etc");
						}
					}*/
			}
			// Bottom chunk
			cc = world.getChunk(chunkX, chunkY - 1, chunkZ);
			if (cc != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(c, 31, b);
						int current_data = peekSimple(c, 0, b);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(c, 0, b, ndata);
							mods++;
							if (adjacent_blo > 2)
							{
								blockSources.addLast(c);
								blockSources.addLast(b);
								blockSources.addLast(0);
								//blockSources.addLast(c << 16 | b << 8 | 0);
							}
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(c, 0, b, ndata);
							mods++;
							if (adjacent_sun > 2)
							{
								sunSources.addLast(c);
								sunSources.addLast(b);
								sunSources.addLast(0);
								//sunSources.addLast(c << 16 | b << 8 | 0);
							}
						}
					}
			}
			// Z
			cc = world.getChunk(chunkX, chunkY, chunkZ + 1);
			if (cc != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(c, b, 0);
						int current_data = peekSimple(c, b, 31);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(c, b, 31, ndata);
							mods++;
							blockSources.addLast(c);
							blockSources.addLast(31);
							blockSources.addLast(b);
							//blockSources.addLast(c << 16 | 31 << 8 | b);
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(c, b, 31, ndata);
							mods++;
							sunSources.addLast(c);
							sunSources.addLast(31);
							sunSources.addLast(b);
							//sunSources.addLast(c << 16 | 31 << 8 | b);
						}
					}
			}
			cc = world.getChunk(chunkX, chunkY, chunkZ - 1);
			if (cc != null)
			{
				for (int b = 0; b < 32; b++)
					for (int c = 0; c < 32; c++)
					{
						int adjacent_data = cc.peekSimple(c, b, 31);
						int current_data = peekSimple(c, b, 0);

						int adjacent_blo = ((adjacent_data & blocklightMask) >>> blockBitshift);
						int current_blo = ((current_data & blocklightMask) >>> blockBitshift);
						int adjacent_sun = ((adjacent_data & sunlightMask) >>> sunBitshift);
						int current_sun = ((current_data & sunlightMask) >>> sunBitshift);
						if (adjacent_blo > 1 && adjacent_blo > current_blo)
						{
							int ndata = current_data & blockAntiMask | (adjacent_blo - 1) << blockBitshift;
							pokeSimpleSilently(c, b, 0, ndata);
							mods++;
							blockSources.addLast(c);
							blockSources.addLast(0);
							blockSources.addLast(b);
							//blockSources.addLast(c << 16 | 0 << 8 | b);
						}
						if (adjacent_sun > 1 && adjacent_sun > current_sun)
						{
							int ndata = current_data & sunAntiMask | (adjacent_sun - 1) << sunBitshift;
							pokeSimpleSilently(c, b, 0, ndata);
							mods++;
							sunSources.addLast(c);
							sunSources.addLast(0);
							sunSources.addLast(b);
							//sunSources.addLast(c << 16 | 0 << 8 | b);
						}
					}
			}
		}
		
		return mods;
	}

	private void computeLightSpread(int bx, int by, int bz, int dataBefore, int data)
	{
		int sunLightBefore = VoxelFormat.sunlight(dataBefore);
		int blockLightBefore = VoxelFormat.blocklight(dataBefore);

		int sunLightAfter = VoxelFormat.sunlight(data);
		int blockLightAfter = VoxelFormat.blocklight(data);

		int csh = world.getRegionsSummariesHolder().getHeightAtWorldCoordinates(bx + chunkX * 32, bz + chunkZ * 32);
		int block_height = by + chunkY * 32;

		//If the block is at or above (never) the topmost tile it's sunlit
		if (block_height >= csh)
			sunLightAfter = 15;

		IntDeque blockSourcesRemoval = CubicChunk.blockSourcesRemoval.get();
		IntDeque sunSourcesRemoval = CubicChunk.sunSourcesRemoval.get();
		IntDeque blockSources = CubicChunk.blockSources.get();
		IntDeque sunSources = CubicChunk.sunSources.get();

		blockSourcesRemoval.addLast(bx);
		blockSourcesRemoval.addLast(by);
		blockSourcesRemoval.addLast(bz);
		blockSourcesRemoval.addLast(blockLightBefore);

		sunSourcesRemoval.addLast(bx);
		sunSourcesRemoval.addLast(by);
		sunSourcesRemoval.addLast(bz);
		sunSourcesRemoval.addLast(sunLightBefore);

		propagateLightRemovalBeyondChunks(blockSources, sunSources, blockSourcesRemoval, sunSourcesRemoval);

		//Add light sources if relevant
		if (sunLightAfter > 0)
		{
			sunSources.addLast(bx);
			sunSources.addLast(bz);
			sunSources.addLast(by);
		}
		if (blockLightAfter > 0)
		{
			blockSources.addLast(bx);
			blockSources.addLast(bz);
			blockSources.addLast(by);
		}

		//Propagate remaining light
		this.propagateLightningBeyondChunk(blockSources, sunSources);
	}

	private void propagateLightRemovalBeyondChunks(IntDeque blockSources, IntDeque sunSources, IntDeque blockSourcesRemoval, IntDeque sunSourcesRemoval)
	{
		int bounds = 64;
		while (sunSourcesRemoval.size() > 0)
		{
			int sunLightLevel = sunSourcesRemoval.removeLast();
			int z = sunSourcesRemoval.removeLast();
			int y = sunSourcesRemoval.removeLast();
			int x = sunSourcesRemoval.removeLast();

			int neighborSunLightLevel;

			// X Axis
			if (x > -bounds)
			{
				neighborSunLightLevel = this.getSunLight(x - 1, y, z);
				if (neighborSunLightLevel > 0 && neighborSunLightLevel < sunLightLevel)
				{
					this.setSunLight(x - 1, y, z, 0);
					sunSourcesRemoval.addLast(x - 1);
					sunSourcesRemoval.addLast(y);
					sunSourcesRemoval.addLast(z);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x - 1);
					sunSources.addLast(z);
					sunSources.addLast(y);
				}
			}
			if (x < bounds)
			{
				neighborSunLightLevel = this.getSunLight(x + 1, y, z);
				if (neighborSunLightLevel > 0 && neighborSunLightLevel < sunLightLevel)
				{
					this.setSunLight(x + 1, y, z, 0);
					sunSourcesRemoval.addLast(x + 1);
					sunSourcesRemoval.addLast(y);
					sunSourcesRemoval.addLast(z);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x + 1);
					sunSources.addLast(z);
					sunSources.addLast(y);
				}
			}
			// Y axis
			if (y > -bounds)
			{
				neighborSunLightLevel = this.getSunLight(x, y - 1, z);
				if (neighborSunLightLevel > 0 && neighborSunLightLevel <= sunLightLevel)
				{
					this.setSunLight(x, y - 1, z, 0);
					sunSourcesRemoval.addLast(x);
					sunSourcesRemoval.addLast(y - 1);
					sunSourcesRemoval.addLast(z);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x);
					sunSources.addLast(z);
					sunSources.addLast(y - 1);
				}
			}
			if (y < bounds)
			{
				neighborSunLightLevel = this.getSunLight(x, y + 1, z);

				if (neighborSunLightLevel > 0 && neighborSunLightLevel < sunLightLevel)
				{
					this.setSunLight(x, y + 1, z, 0);
					sunSourcesRemoval.addLast(x);
					sunSourcesRemoval.addLast(y + 1);
					sunSourcesRemoval.addLast(z);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x);
					sunSources.addLast(z);
					sunSources.addLast(y + 1);
				}
			}
			// Z Axis
			if (z > -bounds)
			{
				neighborSunLightLevel = this.getSunLight(x, y, z - 1);
				if (neighborSunLightLevel > 0 && neighborSunLightLevel < sunLightLevel)
				{
					this.setSunLight(x, y, z - 1, 0);
					sunSourcesRemoval.addLast(x);
					sunSourcesRemoval.addLast(y);
					sunSourcesRemoval.addLast(z - 1);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x);
					sunSources.addLast(z - 1);
					sunSources.addLast(y);
				}
			}
			if (z < bounds)
			{
				neighborSunLightLevel = this.getSunLight(x, y, z + 1);
				if (neighborSunLightLevel > 0 && neighborSunLightLevel < sunLightLevel)
				{
					this.setSunLight(x, y, z + 1, 0);
					sunSourcesRemoval.addLast(x);
					sunSourcesRemoval.addLast(y);
					sunSourcesRemoval.addLast(z + 1);
					sunSourcesRemoval.addLast(neighborSunLightLevel);
				}
				else if (neighborSunLightLevel >= sunLightLevel)
				{
					sunSources.addLast(x);
					sunSources.addLast(z + 1);
					sunSources.addLast(y);
				}
			}
		}

		while (blockSourcesRemoval.size() > 0)
		{
			int blockLightLevel = blockSourcesRemoval.removeLast();
			int z = blockSourcesRemoval.removeLast();
			int y = blockSourcesRemoval.removeLast();
			int x = blockSourcesRemoval.removeLast();

			int neighborBlockLightLevel;

			// X Axis
			if (x > -bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x - 1, y, z);
				//System.out.println(neighborBlockLightLevel + "|" + blockLightLevel);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x - 1, y, z, 0);
					blockSourcesRemoval.addLast(x - 1);
					blockSourcesRemoval.addLast(y);
					blockSourcesRemoval.addLast(z);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x - 1);
					blockSources.addLast(z);
					blockSources.addLast(y);
				}
			}
			if (x < bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x + 1, y, z);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x + 1, y, z, 0);
					blockSourcesRemoval.addLast(x + 1);
					blockSourcesRemoval.addLast(y);
					blockSourcesRemoval.addLast(z);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x + 1);
					blockSources.addLast(z);
					blockSources.addLast(y);
				}
			}
			// Y axis
			if (y > -bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x, y - 1, z);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x, y - 1, z, 0);
					blockSourcesRemoval.addLast(x);
					blockSourcesRemoval.addLast(y - 1);
					blockSourcesRemoval.addLast(z);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x);
					blockSources.addLast(z);
					blockSources.addLast(y - 1);
				}
			}
			if (y < bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x, y + 1, z);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x, y + 1, z, 0);
					blockSourcesRemoval.addLast(x);
					blockSourcesRemoval.addLast(y + 1);
					blockSourcesRemoval.addLast(z);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x);
					blockSources.addLast(z);
					blockSources.addLast(y + 1);
				}
			}
			// Z Axis
			if (z > -bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x, y, z - 1);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x, y, z - 1, 0);
					blockSourcesRemoval.addLast(x);
					blockSourcesRemoval.addLast(y);
					blockSourcesRemoval.addLast(z - 1);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x);
					blockSources.addLast(z - 1);
					blockSources.addLast(y);
				}
			}
			if (z < bounds)
			{
				neighborBlockLightLevel = this.getBlockLight(x, y, z + 1);
				if (neighborBlockLightLevel > 0 && neighborBlockLightLevel < blockLightLevel)
				{
					this.setBlockLight(x, y, z + 1, 0);
					blockSourcesRemoval.addLast(x);
					blockSourcesRemoval.addLast(y);
					blockSourcesRemoval.addLast(z + 1);
					blockSourcesRemoval.addLast(neighborBlockLightLevel);
				}
				else if (neighborBlockLightLevel >= blockLightLevel)
				{
					blockSources.addLast(x);
					blockSources.addLast(z + 1);
					blockSources.addLast(y);
				}
			}
		}
	}

	private int propagateLightningBeyondChunk(IntDeque blockSources, IntDeque sunSources)
	{
		//int data[] = world.chunksData.grab(dataPointer);
		int modifiedBlocks = 0;
		int bounds = 64;

		// The ints are composed as : 0x0BSMIIII
		// Second pass : loop fill bfs algo
		Voxel voxel;
		while (blockSources.size() > 0)
		{
			int y = blockSources.removeLast();
			int z = blockSources.removeLast();
			int x = blockSources.removeLast();
			int voxelData = getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z);
			int ll = (voxelData & blocklightMask) >> blockBitshift;
			int cId = VoxelFormat.id(voxelData);

			voxel = VoxelsStore.get().getVoxelById(cId);

			if (VoxelsStore.get().getVoxelById(cId).getType().isOpaque())
				ll = voxel.getLightLevel(voxelData);

			if (ll > 1)
			{
				// X-propagation
				if (x < bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x + 1, y, z);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x + 1, y, z, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x + 1);
						blockSources.addLast(z);
						blockSources.addLast(y);
						//blockSources.addLast(x + 1 << 16 | z << 8 | y);
					}
				}
				if (x > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x - 1, y, z);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x - 1, y, z, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x - 1);
						blockSources.addLast(z);
						blockSources.addLast(y);
						//blockSources.addLast(x - 1 << 16 | z << 8 | y);
					}
				}
				// Z-propagation
				if (z < bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z + 1);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z + 1, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z + 1);
						blockSources.addLast(y);
						//blockSources.addLast(x << 16 | z + 1 << 8 | y);
					}
				}
				if (z > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z - 1);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z - 1, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z - 1);
						blockSources.addLast(y);
						//blockSources.addLast(x << 16 | z - 1 << 8 | y);
					}
				}
				// Y-propagation
				if (y < bounds) // y = 254+1
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y + 1, z);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y + 1, z, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z);
						blockSources.addLast(y + 1);
						//blockSources.addLast(x << 16 | z << 8 | y + 1);
					}
				}
				if (y > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y - 1, z);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & blocklightMask) >> blockBitshift) < ll - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y - 1, z, adj & blockAntiMask | (ll - 1) << blockBitshift);
						modifiedBlocks++;
						blockSources.addLast(x);
						blockSources.addLast(z);
						blockSources.addLast(y - 1);
						//blockSources.addLast(x << 16 | z << 8 | y - 1);
					}
				}
			}
		}
		// Sunlight propagation
		while (sunSources.size() > 0)
		{
			int y = sunSources.removeLast();
			int z = sunSources.removeLast();
			int x = sunSources.removeLast();

			int voxelData = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z);
			int ll = (voxelData & sunlightMask) >> sunBitshift;
			int cId = VoxelFormat.id(voxelData);

			voxel = VoxelsStore.get().getVoxelById(cId);

			if (voxel.getType().isOpaque())
				ll = 0;

			if (ll > 1)
			{
				// X-propagation
				if (x < bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x + 1, y, z);
					int llRight = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.RIGHT);

					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llRight - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x + 1, y, z, adj & sunAntiMask | (llRight - 1) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x + 1);
						sunSources.addLast(z);
						sunSources.addLast(y);
					}
				}
				if (x > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x - 1, y, z);
					int llLeft = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.LEFT);
					//int id = (adj & 0xFFFF);
					//if(id == 25)
					//	System.out.println("topikek"+VoxelTypes.get((adj & 0xFFFF)).getType().isOpaque() + " -> " +((adj & sunlightMask) >> sunBitshift));
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llLeft - 1)
					{
						//if(id == 25)
						//	System.out.println("MAIS LEL TARACE"+VoxelTypes.get((adj & 0xFFFF)).getType().isOpaque() + " -> " +((adj & sunlightMask) >> sunBitshift));
						this.setWorldDataOnlyForLightningUpdatesFunctions(x - 1, y, z, adj & sunAntiMask | (llLeft - 1) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x - 1);
						sunSources.addLast(z);
						sunSources.addLast(y);
						//sunSources.addLast(x - 1 << 16 | z << 8 | y);
					}
				}
				// Z-propagation
				if (z < bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z + 1);
					int llFront = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.FRONT);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llFront - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z + 1, adj & sunAntiMask | (llFront - 1) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z + 1);
						sunSources.addLast(y);
						//sunSources.addLast(x << 16 | z + 1 << 8 | y);
					}
				}
				if (z > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z - 1);
					int llBack = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.BACK);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llBack - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z - 1, adj & sunAntiMask | (llBack - 1) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z - 1);
						sunSources.addLast(y);
						//sunSources.addLast(x << 16 | z - 1 << 8 | y);
					}
				}
				// Y-propagation
				if (y < bounds) // y = 254+1
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y + 1, z);
					int llTop = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.TOP);
					if (!VoxelsStore.get().getVoxelById((adj & 0xFFFF)).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llTop - 1)
					{
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y + 1, z, adj & sunAntiMask | (llTop - 1) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z);
						sunSources.addLast(y + 1);
						//sunSources.addLast(x << 16 | z << 8 | y + 1);
					}
				}
				if (y > -bounds)
				{
					int adj = this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y - 1, z);
					int llBottm = ll - voxel.getLightLevelModifier(voxelData, adj, VoxelSides.BOTTOM);
					if (!VoxelsStore.get().getVoxelById(adj).getType().isOpaque() && ((adj & sunlightMask) >> sunBitshift) < llBottm)
					{
						//removed = ((((data[x * 1024 + y * 32 + z] & 0x000000FF) == 128)) ? 1 : 0)
						this.setWorldDataOnlyForLightningUpdatesFunctions(x, y - 1, z, adj & sunAntiMask | (llBottm /* - removed */) << sunBitshift);
						modifiedBlocks++;
						sunSources.addLast(x);
						sunSources.addLast(z);
						sunSources.addLast(y - 1);
						//sunSources.addLast(x << 16 | z << 8 | y - 1);
					}
				}
			}
		}

		return modifiedBlocks;
	}

	private int getWorldDataOnlyForLightningUpdatesFuncitons(int x, int y, int z)
	{
		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
					this.peekSimple(x, y, z);
		return world.peekSimple(x + chunkX * 32, y + chunkY * 32, z + chunkZ * 32);
	}

	private void setWorldDataOnlyForLightningUpdatesFunctions(int x, int y, int z, int data)
	{
		//Still within bounds !
		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
				{
					this.pokeSimpleSilently(x, y, z, data);
					return;
				}

		int oldData = world.peekSimple(x, y, z);
		world.pokeSimpleSilently(x + chunkX * 32, y + chunkY * 32, z + chunkZ * 32, data);
		
		Chunk c = world.getChunk((x + chunkX * 32) / 32, (y + chunkY * 32) / 32, (z + chunkZ * 32) / 32);
		if (c != null && oldData != data)
			c.lightBaker().requestLightningUpdate();
			//c.markInNeedForLightningUpdate();
	}

	private int getSunLight(int x, int y, int z)
	{
		//if(this.dataPointer == -1)
		//	return y <= world.getRegionSummaries().getHeightAt(chunkX * 32 + x, chunkZ * 32 + z) ? 0 : 15;

		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
					return VoxelFormat.sunlight(this.peekSimple(x, y, z));
		// Stronger implementation for unbound spread functions
		return VoxelFormat.sunlight(this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z));
	}

	private int getBlockLight(int x, int y, int z)
	{
		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
					return VoxelFormat.blocklight(this.peekSimple(x, y, z));
		// Stronger implementation for unbound spread functions
		return VoxelFormat.blocklight(this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z));
	}

	private void setSunLight(int x, int y, int z, int level)
	{
		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
				{
					this.pokeSimpleSilently(x, y, z, VoxelFormat.changeSunlight(this.peekSimple(x, y, z), level));
					return;
				}
		// Stronger implementation for unbound spread functions
		this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z, VoxelFormat.changeSunlight(this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z), level));
	}
	
	private void setBlockLight(int x, int y, int z, int level)
	{
		if (x > 0 && x < 31)
			if (y > 0 && y < 31)
				if (z > 0 && z < 31)
				{
					this.pokeSimpleSilently(x, y, z, VoxelFormat.changeBlocklight(this.peekSimple(x, y, z), level));
					return;
				}
		// Stronger implementation for unbound spread functions
		this.setWorldDataOnlyForLightningUpdatesFunctions(x, y, z, VoxelFormat.changeBlocklight(this.getWorldDataOnlyForLightningUpdatesFuncitons(x, y, z), level));
	}

	public boolean isAirChunk()
	{
		return chunkVoxelData == null;
	}

	@Override
	public World getWorld()
	{
		return world;
	}

	@Override
	public Region getRegion()
	{
		return holdingRegion;
	}
	
	public ChunkHolderImplementation holder()
	{
		return chunkHolder;
	}

	@Override
	public int hashCode()
	{
		return uuid;
	}
	
	class ActualChunkVoxelContext implements ChunkVoxelContext {
		
		final int x, y, z;
		final int data;
		
		public ActualChunkVoxelContext(int x, int y, int z, int data)
		{
			this.x = x & 0x1F;
			this.y = y & 0x1F;
			this.z = z & 0x1F;
			
			this.data = data;
		}

		@Override
		public World getWorld()
		{
			return world;
		}

		@Override
		public Location getLocation()
		{
			return new Location(world, getX(), getY(), getZ());
		}

		@Override
		public Voxel getVoxel()
		{
			Voxel voxel = world.getGameContext().getContent().voxels().getVoxelById(data);
			return voxel == null ? world.getGameContext().getContent().voxels().getVoxelById(0) : voxel;
		}

		@Override
		public int getData()
		{
			return data;
		}

		@Override
		public int getX()
		{
			return CubicChunk.this.getChunkX() * 32 + x;
		}

		@Override
		public int getY()
		{
			return CubicChunk.this.getChunkY() * 32 + y;
		}

		@Override
		public int getZ()
		{
			return CubicChunk.this.getChunkZ() * 32 + z;
		}

		@Override
		public int getNeightborData(int side)
		{
			switch (side)
			{
			case (0):
				return world.peekSimple(getX() - 1, getY(), getZ());
			case (1):
				return world.peekSimple(getX(), getY(), getZ() + 1);
			case (2):
				return world.peekSimple(getX() + 1, getY(), getZ());
			case (3):
				return world.peekSimple(getX(), getY(), getZ() - 1);
			case (4):
				return world.peekSimple(getX(), getY() + 1, getZ());
			case (5):
				return world.peekSimple(getX(), getY() - 1, getZ());
			}
			throw new RuntimeException("Fuck off");
		}

		@Override
		public Chunk getChunk()
		{
			return CubicChunk.this;
		}

		@Override
		public EditableVoxelContext poke(int newVoxelData, WorldModificationCause cause)
				throws WorldException {
			return CubicChunk.this.poke(x, y, z, newVoxelData, cause);
		}

		@Override
		public EditableVoxelContext pokeSilently(int newVoxelData) throws WorldException {

			return CubicChunk.this.pokeSilently(x, y, z, newVoxelData);
		}

		@Override
		public void pokeSimple(int newVoxelData) {
			CubicChunk.this.pokeSimple(x, y, z, newVoxelData);
		}

		@Override
		public void pokeSimpleSilently(int newVoxelData) {
			CubicChunk.this.pokeSimpleSilently(x, y, z, newVoxelData);
		}

		@Override
		public VoxelComponents components() {
			return CubicChunk.this.components(x, y, z);
		}
	}

	@Override
	public void destroy() {
		this.lightBakingStatus.destroy();
	}

	@Override
	public void addEntity(Entity entity) {
		entitiesLock.lock();
		localEntities.add(entity);
		entitiesLock.unlock();
	}

	@Override
	public void removeEntity(Entity entity) {
		entitiesLock.lock();
		localEntities.remove(entity);
		entitiesLock.unlock();
	}
	
	@Override
	public IterableIterator<Entity> getEntitiesWithinChunk()
	{
		return new IterableIteratorWrapper<Entity>(localEntities.iterator()) {

			@Override
			public void remove() {

				entitiesLock.lock();
				super.remove();
				entitiesLock.unlock();
			}
			
		};
	}

	@Override
	public ChunkLightUpdater lightBaker() {
		return lightBakingStatus;
	}
}
