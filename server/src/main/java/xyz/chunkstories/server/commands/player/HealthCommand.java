//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.server.commands.player;

import xyz.chunkstories.api.entity.Entity;
import xyz.chunkstories.api.entity.traits.serializable.TraitHealth;
import xyz.chunkstories.api.player.Player;
import xyz.chunkstories.api.plugin.commands.Command;
import xyz.chunkstories.api.plugin.commands.CommandEmitter;
import xyz.chunkstories.api.server.Server;
import xyz.chunkstories.server.commands.ServerCommandBasic;

/** Heals */
public class HealthCommand extends ServerCommandBasic {

	public HealthCommand(Server serverConsole) {
		super(serverConsole);
		server.getPluginManager().registerCommand("health", this);
	}

	// Lazy, why does Java standard lib doesn't have a clean way to do this tho
	// http://stackoverflow.com/questions/1102891/how-to-check-if-a-string-is-numeric-in-java
	public static boolean isNumeric(String str) {
		for (char c : str.toCharArray()) {
			if (!Character.isDigit(c))
				return false;
		}
		return true;
	}

	@Override
	public boolean handleCommand(CommandEmitter emitter, Command command, String[] arguments) {

		if (!(emitter instanceof Player)) {
			emitter.sendMessage("You need to be a player to use this command.");
			return true;
		}

		Player player = (Player) emitter;

		if (!emitter.hasPermission("self.sethealth")) {
			emitter.sendMessage("You don't have the permission.");
			return true;
		}

		if (arguments.length < 1 || !isNumeric(arguments[0])) {
			emitter.sendMessage("Syntax: /health <hp>");
			return true;
		}

		float health = Float.parseFloat(arguments[0]);

		Entity entity = player.getControlledEntity();

		if (!entity.traits.tryWithBoolean(TraitHealth.class, fm -> {
			fm.setHealth(health);
			player.sendMessage("Health set to: " + health + "/" + fm.getMaxHealth());

			return true;
		}))
			emitter.sendMessage("This action doesn't apply to your current entity.");

		return true;
	}

}
