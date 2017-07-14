package io.xol.chunkstories.converter;

import io.xol.chunkstories.api.util.concurrency.Fence;
import io.xol.chunkstories.api.voxel.Voxel;
import io.xol.chunkstories.api.world.chunk.ChunkHolder;
import io.xol.chunkstories.converter.ConverterWorkers.ConverterWorkerThread;
import io.xol.chunkstories.tools.WorldTool;
import io.xol.chunkstories.workers.Task;
import io.xol.chunkstories.workers.TaskExecutor;
import io.xol.chunkstories.world.summary.RegionSummaryImplementation;
import io.xol.engine.concurrency.CompoundFence;

public class TaskBuildRegionSummary extends Task {

	int regionX, regionZ;
	WorldTool csWorld;
	
	public TaskBuildRegionSummary(int regionX, int regionZ, WorldTool csWorld) {
		super();
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.csWorld = csWorld;
	}

	@Override
	protected boolean task(TaskExecutor taskExecutor) {

		ConverterWorkerThread cwt = (ConverterWorkerThread)taskExecutor;
		
		//We wait on a bunch of stuff to load everytime
		CompoundFence loadRelevantData = new CompoundFence();
		
		RegionSummaryImplementation summary = csWorld.getRegionsSummariesHolder().aquireRegionSummary(cwt, regionX, regionZ);
		loadRelevantData.add(summary.waitForLoading());

		//Aquires the chunks we want to make the summaries of.
		for (int innerCX = 0; innerCX < 8; innerCX++)
			for (int innerCZ = 0; innerCZ < 8; innerCZ++)
				for (int chunkY = 0; chunkY < OfflineWorldConverter.mcWorldHeight / 32; chunkY++)
				{
					ChunkHolder holder = csWorld.aquireChunkHolder(cwt, regionX * 8 + innerCX, chunkY, regionZ * 8 + innerCZ);
					if (holder != null) {
						loadRelevantData.add(holder.waitForLoading());
						
						if(cwt.registeredCS_Holders.add(holder))
							cwt.chunksAquired++;
					}
				}
		
		//Wait until all of that crap loads
		loadRelevantData.traverse();

		//Descend from top
		for (int i = 0; i < 256; i++)
			for (int j = 0; j < 256; j++)
			{
				for (int h = OfflineWorldConverter.mcWorldHeight; h > 0; h--)
				{
					int data = csWorld.getVoxelData(regionX * 256 + i, h, regionZ * 256 + j);
					if (data != 0)
					{
						Voxel vox = cwt.converter().getContent().voxels().getVoxelById(data);
						if (vox.getType().isSolid() || vox.getType().isLiquid())
						{
							summary.setHeightAndId(regionX * 256 + i, h, regionZ * 256 + j, data & 0x0000FFFF);
							break;
						}
					}
				}
			}
		
		Fence waitForSummarySave = summary.saveSummary();
		//cwt.converter().verbose("Waiting for summary saving...");
		waitForSummarySave.traverse();
		//cwt.converter().verbose("Done.");
		
		//We don't need the summary anymore
		summary.unregisterUser(cwt);
		
		return true;
	}

}