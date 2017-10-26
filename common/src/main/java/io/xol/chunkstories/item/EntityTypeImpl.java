package io.xol.chunkstories.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import io.xol.chunkstories.api.Location;
import io.xol.chunkstories.api.Content.EntityTypes;
import io.xol.chunkstories.api.entity.Entity;
import io.xol.chunkstories.api.entity.EntityType;
import io.xol.chunkstories.api.exceptions.content.IllegalEntityDeclarationException;
import io.xol.chunkstories.entity.EntityTypesStore;
import io.xol.chunkstories.materials.GenericNamedConfigurable;
import io.xol.chunkstories.tools.ChunkStoriesLoggerImplementation;

public class EntityTypeImpl extends GenericNamedConfigurable implements EntityType {
	
	//final String name;
	//final String className;
	final EntityTypesStore store;
	
	final Constructor<? extends Entity> constructor;
	final short id;

	@SuppressWarnings("unchecked")
	public EntityTypeImpl(EntityTypesStore store, String name, short id, BufferedReader reader) throws IllegalEntityDeclarationException, IOException
	{
		super(name, reader);
		
		this.id = id;
		this.store = store;
		
		String className = this.resolveProperty("class", null);
		if(className == null)
			throw new IllegalEntityDeclarationException("'class' property isn't set for entity "+name);
		
		try
		{
			Class<?> entityClass = store.parent().modsManager().getClassByName(className);
			if(entityClass == null)
			{
				throw new IllegalEntityDeclarationException("Entity class " + className + " does not exist in codebase.");
			}
			else if (!(Entity.class.isAssignableFrom(entityClass)))
			{
				throw new IllegalEntityDeclarationException("Entity class " + entityClass + " is not implementing the Entity interface.");
			}
			else
			{
				@SuppressWarnings("rawtypes")
				Class[] types = { EntityType.class, Location.class  };
				
				this.constructor = (Constructor<? extends Entity>) entityClass.getConstructor(types);
				
				if(constructor == null)
				{
					throw new IllegalEntityDeclarationException("Entity "+name+" does not provide a valid constructor.");
				}
			}

		}
		catch (NoSuchMethodException | SecurityException | IllegalArgumentException e)
		{
			e.printStackTrace();
			e.printStackTrace(store.parent().logger().getPrintWriter());

			throw new IllegalEntityDeclarationException("Entity "+name+" failed to provide an acceptable constructor, exception="+e.getMessage());
		}
	}

	@Override
	public short getId() {
		return id;
	}

	@Override
	public Entity create(Location location) {
		Object[] parameters = { this, location };
		try
		{
			return constructor.newInstance(parameters);
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
		{
			//This is bad
			ChunkStoriesLoggerImplementation.getInstance().log("Couldn't instanciate entity "+this+" at " + location);
			e.printStackTrace();
			e.printStackTrace(ChunkStoriesLoggerImplementation.getInstance().getPrintWriter());
			return null;
		}
	}

	@Override
	public EntityTypes store() {
		return store;
	}
}
