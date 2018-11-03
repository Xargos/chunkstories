//
// This file is a part of the Chunk Stories Implementation codebase
// Check out README.md for more information
// Website: http://chunkstories.xyz
//

package io.xol.chunkstories.gui.layer.ingame;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import io.xol.chunkstories.api.client.IngameClient;
import io.xol.chunkstories.api.gui.Font;
import io.xol.chunkstories.api.gui.Gui;
import io.xol.chunkstories.api.gui.GuiDrawer;
import io.xol.chunkstories.api.util.configuration.Configuration;
import io.xol.chunkstories.client.InternalClientOptions;
import org.joml.Vector4f;

import io.xol.chunkstories.api.content.mods.Mod;
import io.xol.chunkstories.api.gui.Layer;
import io.xol.chunkstories.api.gui.elements.InputText;
import io.xol.chunkstories.api.input.Input;
import io.xol.chunkstories.api.input.Mouse.MouseScroll;
import io.xol.chunkstories.api.plugin.ChunkStoriesPlugin;
import io.xol.chunkstories.api.util.ColorsTools;
import io.xol.chunkstories.world.WorldClientLocal;
import io.xol.chunkstories.world.WorldClientRemote;

public class ChatManager {
	private final IngameLayer ingameGuiLayer;
	private final IngameClient ingameClient;
	private final Gui gui;

	private final int chatHistorySize = 150;

	private Deque<ChatLine> chat = new ConcurrentLinkedDeque<ChatLine>();
	private List<String> sent = new ArrayList<String>();

	private int sentMessages = 0;
	private int sentHistory = 0;

	public ChatManager(IngameClient ingameClient, IngameLayer ingameGuiLayer) {
		this.ingameClient = ingameClient;
		this.ingameGuiLayer = ingameGuiLayer;
		this.gui = ingameGuiLayer.getGui();
	}

	private class ChatLine {
		public ChatLine(String text) {
			if (text == null)
				text = "";
			this.text = text;
			time = System.currentTimeMillis();
		}

		public long time;
		public String text;

		@SuppressWarnings("unused")
		public void clickRelative(int x, int y) {
			// TODO clickable text, urls etc
		}
	}

	public class ChatPanelOverlay extends Layer {
		InputText inputBox;

		long delay;

		public ChatPanelOverlay(Gui gui, Layer parent) {
			super(gui, parent);

			// Add the inputBox
			this.inputBox = new InputText(this, 0, 0, 500);
			this.elements.add(inputBox);
			this.setFocusedElement(inputBox);

			// Reset the scroll
			ChatManager.this.scroll = 0;

			// 150ms of delay to avoid typing in by mistake
			this.delay = System.currentTimeMillis() + 30;
		}

		@Override
		public void render(GuiDrawer drawer) {
			parentLayer.render(drawer);

			inputBox.setPosition(12, 192);
			inputBox.setTransparent(true);
			inputBox.render(drawer);
		}

		@Override
		public void onResize(int newWidth, int newHeight) {
			parentLayer.onResize(newWidth, newHeight);
		}

		@Override
		public boolean handleInput(Input input) {
			if (input.equals("exit")) {
				gui.popTopLayer();
				return true;
			} else if (input.equals("uiUp")) {
				// sentHistory = 0 : empty message, = 1 last message, 2 last message before etc
				if (sentMessages > sentHistory) {
					sentHistory++;
				}
				if (sentHistory > 0)
					inputBox.setText(sent.get(sentHistory - 1));
				else
					inputBox.setText("");
			} else if (input.equals("uiDown")) {
				// sentHistory = 0 : empty message, = 1 last message, 2 last message before etc
				if (sentHistory > 0) {
					sentHistory--;
				}
				if (sentHistory > 0)
					inputBox.setText(sent.get(sentHistory - 1));
				else
					inputBox.setText("");
			} else if (input.equals("enter")) {
				processTextInput(inputBox.getText());
				inputBox.setText("");
				sentHistory = 0;
				gui.popTopLayer();
				return true;
			} else if (input instanceof MouseScroll) {
				MouseScroll ms = (MouseScroll) input;
				if (ms.amount() > 0)
					scroll++;
				else
					scroll--;
			}
			inputBox.handleInput(input);

			return true;
		}

		@Override
		public boolean handleTextInput(char c) {
			if (System.currentTimeMillis() <= delay)
				return false;
			else
				return super.handleTextInput(c);
		}
	}

	private int scroll = 0;

	public void render(GuiDrawer drawer) {
		while (chat.size() > chatHistorySize)
			chat.removeLast();
		if (scroll < 0)// || !chatting)
			scroll = 0;
		if (scroll > chatHistorySize)
			scroll = chatHistorySize;
		int scrollLinesSkip = scroll;
		int linesDrew = 0;
		int maxLines = 18;
		Iterator<ChatLine> i = chat.iterator();
		while (linesDrew < maxLines && i.hasNext()) {
			// if (a >= chatHistorySize - lines)
			ChatLine line = i.next();
			if (scrollLinesSkip > 0) {
				scrollLinesSkip--;
				continue;
			}

			Font font = gui.getFonts().getFont("LiberationSans-Regular__aa", 18);

			int chatWidth = Math.max(750, gui.getViewportWidth() / 4 * 3);
			String localizedLine = gui.localization().localize(line.text);
			int actualLines = font.getLinesHeight(localizedLine, chatWidth);
			linesDrew += actualLines;
			float textFade = (line.time + 10000L - System.currentTimeMillis()) / 1000f;
			if (textFade < 0)
				textFade = 0;
			if (textFade > 1 || gui.getTopLayer() instanceof ChatPanelOverlay)
				textFade = 1;

			drawer.drawStringWithShadow(font, 9,
					(linesDrew - 1) * font.getLineHeight() + 180 + (50), localizedLine, chatWidth,
					new Vector4f(1, 1, 1, textFade));
		}

	}

	public void insert(String t) {
		chat.addFirst(new ChatLine(t));
	}

	private void processTextInput(String input) {
		String clientUserName = ingameClient.getUser().getName();

		if (input.startsWith("/")) {
			String chatMsg = input;

			chatMsg = chatMsg.substring(1, chatMsg.length());

			String cmdName = chatMsg.toLowerCase();
			String[] args = {};
			if (chatMsg.contains(" ")) {
				cmdName = chatMsg.substring(0, chatMsg.indexOf(" "));
				args = chatMsg.substring(chatMsg.indexOf(" ") + 1, chatMsg.length()).split(" ");
			}

			if (ingameClient.getPluginManager().dispatchCommand(ingameClient.getPlayer(),
					cmdName, args)) {
				if (sent.size() == 0 || !sent.get(0).equals(input)) {
					sent.add(0, input);
					sentMessages++;
				}
				return;
			} else if (cmdName.equals("plugins")) {
				String list = "";

				Iterator<ChunkStoriesPlugin> i = ingameClient.getPluginManager().activePlugins();
				while (i.hasNext()) {
					ChunkStoriesPlugin plugin = i.next();
					list += plugin.getName() + (i.hasNext() ? ", " : "");
				}

				if (ingameClient.getWorld() instanceof WorldClientLocal)
					insert("#00FFD0" + i + " active client [master] plugins : " + list);
				else
					insert("#74FFD0" + i + " active client [remote] plugins : " + list);

				if (sent.size() == 0 || !sent.get(0).equals(input)) {
					sent.add(0, input);
					sentMessages++;
				}
			} else if (cmdName.equals("mods")) {
				String list = "";
				int i = 0;
				for (Mod mod : ingameClient.getContent().modsManager().getCurrentlyLoadedMods()) {
					i++;
					list += mod.getModInfo().getName() + (i == ingameClient.getContent().modsManager().getCurrentlyLoadedMods().size() ? "" : ", ");
				}

				if (ingameClient.getWorld() instanceof WorldClientLocal)
					insert("#FF0000" + i + " active client [master] mods : " + list);
				else
					insert("#FF7070" + i + " active client [remote] mods : " + list);

				if (sent.size() == 0 || !sent.get(0).equals(input)) {
					sent.add(0, input);
					sentMessages++;
				}
			}
		}

		if (input.equals("/locclear")) {
			chat.clear();
		} else if (input.equals("I am Mr Debug")) {
			// it was you this whole time
			Configuration.OptionBoolean option = ingameClient.getConfiguration().get(InternalClientOptions.INSTANCE.getDebugMode());
			option.trySetting(true);
		}

		if (ingameClient.getWorld() instanceof WorldClientRemote)
			((WorldClientRemote) ingameClient.getWorld()).getConnection()
					.sendTextMessage("chat/" + input);
		else
			insert(ColorsTools.getUniqueColorPrefix(clientUserName) + clientUserName + "#FFFFFF > " + input);

		System.out.println(clientUserName + " > " + input);

		if (sent.size() == 0 || !sent.get(0).equals(input)) {
			sent.add(0, input);
			sentMessages++;
		}
	}
}
