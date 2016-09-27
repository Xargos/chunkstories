package io.xol.chunkstories.content;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.xol.chunkstories.content.mods.Asset;
import io.xol.chunkstories.content.mods.ForeignCodeClassLoader;
import io.xol.chunkstories.content.mods.Mod;
import io.xol.chunkstories.content.mods.ModFolder;
import io.xol.chunkstories.content.mods.ModZip;
import io.xol.chunkstories.content.mods.exceptions.ModLoadFailureException;
import io.xol.chunkstories.content.mods.exceptions.ModNotFoundException;
import io.xol.chunkstories.content.mods.exceptions.NotAllModsLoadedException;
import io.xol.chunkstories.entity.Entities;
import io.xol.chunkstories.entity.EntityComponents;
import io.xol.chunkstories.item.ItemTypes;
import io.xol.chunkstories.materials.Materials;
import io.xol.chunkstories.net.packets.PacketsProcessor;
import io.xol.chunkstories.particles.ParticleTypes;
import io.xol.chunkstories.tools.ChunkStoriesLogger;
import io.xol.chunkstories.voxel.VoxelTextures;
import io.xol.chunkstories.voxel.Voxels;
import io.xol.chunkstories.voxel.models.VoxelModels;
import io.xol.chunkstories.world.generator.WorldGenerators;
import io.xol.engine.animation.BVHLibrary;
import io.xol.engine.concurrency.UniqueList;
import io.xol.engine.graphics.shaders.ShadersLibrary;
import io.xol.engine.graphics.textures.TexturesHandler;
import io.xol.engine.misc.FoldersUtils;
import io.xol.engine.model.ModelLibrary;
import io.xol.engine.sound.library.SoundsLibrary;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class Mods
{
	private static Mod baseAssets;
	private static String[] modsEnabled = new String[0];
	private static UniqueList<Mod> enabledMods = new UniqueList<Mod>();
	private static Map<String, AssetHierarchy> avaibleAssets = new HashMap<String, AssetHierarchy>();
	private static Map<String, ForeignCodeClassLoader> avaibleForeignClasses = new HashMap<String, ForeignCodeClassLoader>();

	private static File cacheFolder = null;

	public static void main(String a[])
	{
		try
		{
			setEnabledMods("C:\\Users\\Hugo\\workspace2\\Dogez-Plugin for CS\\mods\\dogez_content", "modInZip", "OveriddenModInZip", "md5:df9f7c813fdc72029b41758ef8dbb528", "md5:7f46165474d11ee5836777d85df2cdab:http://xol.io");
			loadEnabledMods();
		}
		catch (NotAllModsLoadedException e)
		{
			System.out.print(e.getMessage());
		}

		

		System.out.println("Done");
	}

	/**
	 * Sets the mods to be used
	 */
	public static void setEnabledMods(String... modsEnabled)
	{
		Mods.modsEnabled = modsEnabled;
	}

	/**
	 * Looks for the mods, resolves their paths, download them if possible and necessary, then descend in their data to enumerate contained assets and code
	 * 
	 * @throws NotAllModsLoadedException
	 *             if some of the mods failed to load, note that even if some mods fail to load the game engine will still alter what is loaded
	 */
	public static void loadEnabledMods() throws NotAllModsLoadedException
	{
		enabledMods.clear();
		List<ModLoadFailureException> modLoadExceptions = new ArrayList<ModLoadFailureException>();

		//Creates mods dir if it needs to
		File modsDir = new File(GameDirectory.getGameFolderPath() + "/mods");
		if (!modsDir.exists())
			modsDir.mkdirs();

		for (String name : modsEnabled)
		{
			try
			{
				Mod mod = null;

				//Servers give a md5 hash for their required mods
				if (name.startsWith("md5:"))
				{
					//Look for a mod with that md5 hash
					String hash = name.substring(4, name.length());
					String url = null;
					//If the hash is bundled with an url, split'em
					if (hash.contains(":"))
					{
						int i = hash.indexOf(":");
						url = hash.substring(i + 1);
						hash = hash.substring(0, i);
					}
					System.out.println("Looking for hashed mod " + hash + " (url = " + url + ")");

					//Look for the mod zip in local fs first.
					File zippedMod = new File(modsDir.getAbsolutePath() + "/" + hash + ".zip");
					if (zippedMod.exists())
					{
						//Awesome we found it !
						mod = new ModZip(zippedMod);
					}
					else if (url != null)
					{
						//TODO download and hanle files from server
					}
					else
					{
						//We failed. Mod won't be loaded
					}
				}
				else
				{
					System.out.println("Looking for mod " + name + " on the local filesystem");

					//First look for it in the directory section
					File modDirectory = new File(modsDir.getAbsolutePath() + "/" + name);
					if (modDirectory.exists())
					{
						mod = new ModFolder(modDirectory);
						System.out.println("Found mod in directory : " + modDirectory);
					}
					else
					{
						//Then look for a .zip file in the same directory
						File zippedMod = new File(modsDir.getAbsolutePath() + "/" + name + ".zip");
						if (zippedMod.exists())
						{
							mod = new ModZip(zippedMod);
							System.out.println("Found mod in zipfile : " + zippedMod);
						}
						else
						{
							//Finally just look for it in the global os path
							if (name.endsWith(".zip"))
							{
								zippedMod = new File(name);
								if (zippedMod.exists())
								{
									mod = new ModZip(zippedMod);
									System.out.println("Found mod in global zipfile : " + zippedMod);
								}
							}
							else
							{
								modDirectory = new File(name);
								if (modDirectory.exists())
								{
									mod = new ModFolder(modDirectory);
									System.out.println("Found mod in global directory : " + modDirectory);
								}
							}
						}
					}
				}

				//Did we manage it ?
				if (mod != null)
				{
					if (!enabledMods.add(mod))
					{
						//Somehow we added a mod twice and it's now conflicting.
						throw new ModLoadFailureException(mod, "Conflicting mod, another mod with the same name or hash is already loaded.");
					}
				}
				else
					throw new ModNotFoundException(name);
			}
			catch (ModLoadFailureException exception)
			{
				modLoadExceptions.add(exception);
			}
		}

		buildModsFileSystem();

		//Return an exception if some mods failed to load.
		if (modLoadExceptions.size() > 0)
			throw new NotAllModsLoadedException(modLoadExceptions);
	}

	private static void buildModsFileSystem()
	{
		avaibleAssets.clear();
		avaibleForeignClasses.clear();
		
		//Obtain a cache folder
		if (cacheFolder == null)
		{
			cacheFolder = new File(GameDirectory.getGameFolderPath() + "/cache/" + ((int) (Math.random() * 10000)));
			cacheFolder.mkdirs();
			//cacheFolder.deleteOnExit();
			Runtime.getRuntime().addShutdownHook(new Thread()
			{
				public void run()
				{
					System.out.println("Deleting cache folder " + cacheFolder);
					FoldersUtils.deleteFolder(cacheFolder);
				}
			});
		}

		// Checks for the base assets folder presence and sanity
		try
		{
			baseAssets = new ModFolder(new File(GameDirectory.getGameFolderPath() + "/res/"));
		}
		catch (ModLoadFailureException e)
		{
			ChunkStoriesLogger.getInstance().error("Fatal : failed to load in the base assets folder. Exception :");
			e.printStackTrace(ChunkStoriesLogger.getInstance().getPrintWriter());
		}

		//Iterates over mods, in order of priority
		for (Mod mod : enabledMods)
		{
			loadModAssets(mod);
		}
		//Just loads the rest
		loadModAssets(baseAssets);
	}

	private static void loadModAssets(Mod mod)
	{
		//For each asset in the said mod
		for (Asset asset : mod.assets())
		{
			//Skips mod.txt
			if (asset.getName().equals("./mod.txt"))
				continue;

			//Special case for .jar files : we extract them in the cache/ folder and make them avaible through secure ClassLoaders
			if (asset.getName().endsWith(".jar"))
			{
				loadJarFile(asset);
				continue;
			}

			//Look for it's entry
			AssetHierarchy entry = avaibleAssets.get(asset.getName());
			if (entry == null)
			{
				entry = new AssetHierarchy(asset);
				avaibleAssets.put(asset.getName(), entry);
			}
			else
			{
				System.out.println("Adding asset " + asset + " but it's already overriden ! (top=" + entry.topInstance() + ")");
				entry.addAssetInstance(asset);
			}
		}
	}

	private static void loadJarFile(Asset asset)
	{
		System.out.println("Handling jar file " + asset);
		try
		{
			//Read the jar file contents and extract it somewhere on cache
			//TODO hash dat crap this boi, the collision probs!!!
			int random = ((int) (Math.random() * 16384960));
			File cachedJarLocation = new File(cacheFolder.getAbsolutePath() + "/" + random + ".jar");
			FileOutputStream fos = new FileOutputStream(cachedJarLocation);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			InputStream is = asset.read();
			System.out.println("Writing to " + cachedJarLocation);
			byte[] buf = new byte[4096];
			while (is.available() > 0)
			{
				int read = is.read(buf);
				bos.write(buf, 0, read);
				if (read == 0)
					break;
			}
			bos.flush();
			bos.close();
			System.out.println("Done writing file");

			//Create a fancy class loader for this temp jar
			ForeignCodeClassLoader classLoader = new ForeignCodeClassLoader(asset.getSource(), cachedJarLocation, Thread.currentThread().getContextClassLoader());
			
			for(String className : classLoader.classes())
			{
				System.out.println("class "+className+" found in jar "+asset);
				avaibleForeignClasses.put(className, classLoader);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static Iterator<AssetHierarchy> getAllUniqueEntries()
	{
		return avaibleAssets.values().iterator();
	}

	public static Iterator<Asset> getAllUniqueFilesLocations()
	{
		return new Iterator<Asset>()
		{
			Iterator<AssetHierarchy> i = getAllUniqueEntries();

			@Override
			public boolean hasNext()
			{
				return i.hasNext();
			}

			@Override
			public Asset next()
			{
				return i.next().topInstance();
			}

		};
	}

	public static Asset getAsset(String assetName)
	{
		AssetHierarchy asset = avaibleAssets.get(assetName);
		if(asset == null)
			return null;
		
		return asset.topInstance();
	}
	
	public static AssetHierarchy getAssetInstances(String assetName)
	{
		return avaibleAssets.get(assetName);
	}
	
	public static Iterator<Asset> getAllAssetsByExtension(String extension)
	{
		return new Iterator<Asset>() {

			Iterator<AssetHierarchy> base = getAllUniqueEntries();
			
			Asset next = null;
			
			@Override
			public boolean hasNext()
			{
				if(next != null)
					return true;
				//If next == null, try to set it
				while(base.hasNext())
				{
					AssetHierarchy entry = base.next();
					if(entry.getName().endsWith(extension))
					{
						next = entry.topInstance();
						break;
					}
				}
				//Did we suceed etc
				return next != null;
			}

			@Override
			public Asset next()
			{
				//Try loading
				if(next == null)
					hasNext();
				//Null out reference and return it
				Asset ret = next;
				next = null;
				return ret;
			}
			
		};
	}
	
	public static Class<?> getClassByName(String className)
	{
		//First try to load it from classpath
		try
		{
			Class<?> inBaseClasspath = Class.forName(className);
			if(inBaseClasspath != null)
				return inBaseClasspath;
		}
		catch (ClassNotFoundException e)
		{
			e.printStackTrace();
		}
		//If this fails, try to obtain it from one of the loaded mods
		
		//TODO
		
		//If all fail, return null
		return null;
	}	
	
	public static class AssetHierarchy implements Iterable<Asset>
	{
		String assetName;
		Asset topInstance;
		Deque<Asset> instances;

		AssetHierarchy(Asset asset)
		{
			assetName = asset.getName();
			instances = new ArrayDeque<Asset>();
			addAssetInstance(asset);

			//Lower complexity for just the top intance
			topInstance = asset;
		}

		public String getName()
		{
			return assetName;
		}
		
		public Asset topInstance()
		{
			return topInstance;
		}

		public void addAssetInstance(Asset asset)
		{
			instances.addLast(asset);
		}

		public Iterator<Asset> iterator()
		{
			return instances.iterator();
		}

		//Below is hacks for HashSet to function properly

		public int hashCode()
		{
			return assetName.hashCode();
		}

		public boolean equals(Object o)
		{
			if (o instanceof String)
				return o.equals(assetName);
			if (o instanceof AssetHierarchy)
				return ((AssetHierarchy) o).assetName.equals(assetName);
			return false;
		}
	}

	public static void reload()
	{
		long total = System.nanoTime();
		long part = System.nanoTime();
		
		try
		{
			loadEnabledMods();
		}
		catch (NotAllModsLoadedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("fs reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		VoxelTextures.buildTextureAtlas();
		System.out.println("texture atlas reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		Materials.reload();
		System.out.println("materials reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		VoxelModels.resetAndLoadModels();
		System.out.println("voxel models reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		ItemTypes.reload();
		System.out.println("items reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		Voxels.loadVoxelTypes();
		System.out.println("voxels reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		Entities.reload();
		System.out.println("entities reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		EntityComponents.reload();
		System.out.println("components reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		PacketsProcessor.loadPacketsTypes();
		System.out.println("packets reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		WorldGenerators.loadWorldGenerators();
		System.out.println("generators reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		ParticleTypes.reload();
		System.out.println("particles reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		part = System.nanoTime();
		
		//Total
		System.out.println("Assets reload took "+Math.floor(((System.nanoTime() - part) / 1000L) / 100f) / 10f + "ms ");
		
		//Inputs.loadKeyBindsClient();
	}

	public static void reloadClientContent()
	{
		TexturesHandler.reloadAll();
		SoundsLibrary.clean();
		ModelLibrary.reloadAllModels();
		BVHLibrary.reloadAllAnimations();
		ShadersLibrary.reloadAllShaders();
	}
	
}