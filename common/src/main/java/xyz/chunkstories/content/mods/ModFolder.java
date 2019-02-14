//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.content.mods;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.LoggerFactory;

import xyz.chunkstories.api.content.Asset;
import xyz.chunkstories.api.content.mods.Mod;
import xyz.chunkstories.api.exceptions.content.mods.ModLoadFailureException;
import xyz.chunkstories.api.util.IterableIterator;

public class ModFolder extends ModImplementation {
	final File folder;
	final Map<String, ModFolderAsset> assets = new HashMap<String, ModFolderAsset>();

	public static void main(String[] a) {
		try {
			new ModFolder(new File("res/"));
		} catch (ModLoadFailureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String toString() {
		return "[ModFolder: " + folder.getAbsolutePath() + "]";
	}

	public ModFolder(File folder) throws ModLoadFailureException {
		this.folder = folder;

		recursiveFolderRead(folder);

		this.modInfo = ModInfoLoaderKt.loadModInfo(getAssetByName("modInfo.json").reader());
		// loadModInformation();
		logger = LoggerFactory.getLogger("mod." + this.modInfo.getInternalName());
	}

	private void recursiveFolderRead(File file) {
		if (file.isDirectory()) {
			for (File f : file.listFiles())
				recursiveFolderRead(f);
		} else {
			String fileName = file.getAbsolutePath().substring(folder.getAbsolutePath().length() + 1);
			fileName = fileName.replace('\\', '/');
			String assetName = fileName;

			assets.put(assetName, new ModFolderAsset(assetName, file));
		}
	}

	@Override
	public Asset getAssetByName(String name) {
		return assets.get(name);
	}

	public class ModFolderAsset implements Asset {
		String assetName;
		File file;

		public ModFolderAsset(String assetName, File file) {
			this.assetName = assetName;
			this.file = file;
		}

		@Override
		public String getName() {
			return assetName;
		}

		@Override
		public InputStream read() {
			try {
				return new FileInputStream(file);
			} catch (IOException e) {
				logger().warn("Failed to read asset : " + assetName + " from " + ModFolder.this);
				e.printStackTrace();
				return null;
			}
		}

		@Override
		public Mod getSource() {
			return ModFolder.this;
		}

		public String toString() {
			return "[Asset: " + assetName + " from mod " + ModFolder.this + "]";
		}

		public File getFile() {
			return file;
		}
	}

	@Override
	public void close() {
	}

	@Override
	public IterableIterator<Asset> assets() {
		return new IterableIterator<Asset>() {
			Iterator<ModFolderAsset> iz = assets.values().iterator();

			@Override
			public boolean hasNext() {
				return iz.hasNext();
			}

			@Override
			public Asset next() {
				return iz.next();
			}
		};
	}

	@Override
	public String getLoadString() {
		return folder.getAbsolutePath();
	}

}