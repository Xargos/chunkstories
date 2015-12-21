package io.xol.chunkstories.entity.inventory;

import java.util.Iterator;

import io.xol.chunkstories.item.ItemPile;

//(c) 2015-2016 XolioWare Interactive
// http://chunkstories.xyz
// http://xol.io

public class Inventory implements Iterable<ItemPile>
{
	public String name;

	public int width;
	public int height;

	private ItemPile[][] contents;

	public Inventory(int width, int height, String name)
	{
		this.width = width;
		this.height = height;
		this.name = name;
		contents = new ItemPile[width][height];
	}

	public ItemPile[][] getContents()
	{
		return contents;
	}
	
	public ItemPile getItem(int x, int y)
	{
		if (contents[x % width][y % height] != null)
			return contents[x % width][y % height] ;
		else
		{
			ItemPile p;
			for (int i = 0; i < x + (1); i++)
			{
				// If overflow in width
				if (i >= width)
					break;
				for (int j = 0; j < y + (1); j++)
				{
					// If overflow in height
					if (j >= height)
						break;
					p = contents[i % width][j % height];
					if (p != null)
					{
						if(i+p.item.slotsWidth-1 >= x && j+p.item.slotsHeight-1 >= y)
							return p;
					}
				}
			}
			return null;
		}
	}

	public boolean canPlaceItemAt(int x, int y, ItemPile pile)
	{
		if (contents[x % width][y % height] != null)
			return false;
		else
		{
			ItemPile p;
			for (int i = 0; i < x + (pile.item.slotsWidth); i++)
			{
				// If overflow in width
				if (i >= width)
					return false;
				for (int j = 0; j < y + (pile.item.slotsHeight); j++)
				{
					// If overflow in height
					if (j >= height)
						return false;
					p = contents[i % width][j % height];
					if (p != null)
					{
						if(i+p.item.slotsWidth-1 >= x && j+p.item.slotsHeight-1 >= y)
							return false;
					}
				}
			}
			return true;
		}
	}

	/**
	 * Returns null if the item was put in this inventory, the item if it wasn't
	 * 
	 * @param x
	 * @param y
	 * @param pile
	 * @return
	 */
	public ItemPile setItemAt(int x, int y, ItemPile pile)
	{
		if (pile == null)
		{
			contents[x % width][y % height] = null;
		}
		else if (canPlaceItemAt(x, y, pile))
		{
			pile.inventory = this;
			pile.x = x;
			pile.y = y;
			contents[x % width][y % height] = pile;
		}
		else
			return pile;
		return null;
	}

	@Override
	public Iterator<ItemPile> iterator()
	{
		Iterator<ItemPile> it = new Iterator<ItemPile>()
		{
			public int i = 0;
			public int j = 0;
			boolean reachedEnd = false;

			boolean mustSeek = false;

			void seek() // Just increments the 2d array until the end.
			{
				i++;
				if (i >= width)
				{
					i = 0;
					j++;
				}
				if (j >= height)
				{
					j = 0;
					reachedEnd = true;
				}
				if (!reachedEnd && contents[i][j] == null)
					seek();
				mustSeek = false;
			}

			@Override
			public boolean hasNext()
			{
				// If end was reached
				if (reachedEnd)
					return false;
				// If last next yelded a valid item
				if (mustSeek)
					seek();
				// If non-null
				if (contents[i][j] != null)
					return true;
				else
				// If it is null, try seeking
				{
					seek();
					if (contents[i][j] == null)
						return false;
				}
				return !reachedEnd;
			}

			@Override
			public ItemPile next()
			{
				// If inventory starts with a null item
				if (contents[i][j] == null)
					seek();
				// If last next yelded a valid item
				if (mustSeek)
					seek();
				ItemPile p = contents[i][j];
				mustSeek = true;
				return p;
			}

			@Override
			public void remove()
			{
				contents[i][j] = null;
			}
		};
		return it;

	}
}
