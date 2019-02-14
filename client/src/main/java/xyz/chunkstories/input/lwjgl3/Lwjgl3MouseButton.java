//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package xyz.chunkstories.input.lwjgl3;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;

import xyz.chunkstories.api.client.Client;
import xyz.chunkstories.api.input.Input;
import xyz.chunkstories.api.input.Mouse.MouseButton;
import xyz.chunkstories.input.Pollable;

public class Lwjgl3MouseButton implements MouseButton, Pollable {
	private final Lwjgl3Mouse mouse;
	private final String name;
	private final int button;

	private boolean isDown = false;

	public Lwjgl3MouseButton(Lwjgl3Mouse mouse, String name, int button) {
		this.mouse = mouse;

		this.name = name;
		this.button = button;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isPressed() {
		return isDown;
	}

	public long getHash() {
		return button;
	}

	@Override
	public void updateStatus() {
		isDown = glfwGetMouseButton(mouse.im.getGameWindow().getGlfwWindowHandle(), button) == GLFW_PRESS;
	}

	@Override
	public Client getClient() {
		return mouse.im.getGameWindow().getClient();
	}

	@Override
	public Lwjgl3Mouse getMouse() {
		return mouse;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		else if (o instanceof Input) {
			return ((Input) o).getName().equals(getName());
		} else if (o instanceof String) {
			return ((String) o).equals(this.getName());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getName().hashCode();
	}

}