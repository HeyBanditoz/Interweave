package main;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.DiscordListener;
import utils.Settings;
import utils.SettingsManager;

public class Interweave implements DedicatedServerModInitializer {

	public static final Logger LOGGER = LogManager.getLogger();
	private static Settings settings;

	private static JDA jda;

	// Discord user ID -> names they can be pinged by, username first
	private static final Map<String, List<String>> mentionables = new ConcurrentHashMap<>();

	private static String lastMessage;

	private static final ExecutorService ES = Executors
			.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Interweave").build());

	@Override
	public void onInitializeServer() {
		LOGGER.info("Interweave is Initializing!");
		
		// JDA
		ES.execute(() -> {
			try {
				settings = SettingsManager.getInstance().getSettings();
				initJDA(settings);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		
		// Setup server startup callback
		ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
	        sendStartMessage();
	        setPlayers(0, server.getMaxPlayers());
		});

		// Setup server end callback
		ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
			LOGGER.info("Interweave shutting down.");
			sendStopMessageAndShutdown();
			ES.shutdown();
		});
	}

	private void initJDA(Settings settings) throws InterruptedException {
		if (settings.getDiscordToken() == null || settings.getDiscordToken().equals("Bot token here...")) {
			log(Level.ERROR,
					"The Discord token is not configured correctly! The bot will not run. Please check your InterweaveConfig.json file.");
			return;
		}
		if (settings.getChannelId() == null || settings.getChannelId().equals("Channel Id here...")) {
			log(Level.ERROR,
					"The Channel Id is not configured correctly! The bot will not run. Please check your InterweaveConfig.json file.");
			return;
		}

		// Only ping individual users, never @everyone, @here or roles
		MessageRequest.setDefaultMentions(EnumSet.of(Message.MentionType.USER));

		jda = JDABuilder.createLight(settings.getDiscordToken()).enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT).build();

		jda.addEventListener(new DiscordListener());

		jda.awaitReady();
		loadDiscordMentionables();
	}

	//
	public static void sendMessage(TranslatableContents msg) {
		ES.execute(() -> {
			// %message% message
			// %sender% sender
			String key = ((TranslatableContents) msg).getKey();
			StringBuilder messageBuilder = new StringBuilder();
			msg.visit((section) -> {
				messageBuilder.append(section);
				return Optional.empty();
			});
			String message = messageBuilder.toString();
			// admin command
			if (key.equals("chat.type.admin")) {
				return;
			}
			// server control
			if (key.contains("commands")) {
				return;
			}
			// advancement
			if (key.contains("chat.type.advancement")) {
				if (settings.getAdvancementFormat() == null) {
					return;
				}
				message = settings.getAdvancementFormat().replace("%message%", message).replace("[", "")
						.replace("]", "").replace("(", "").replace(")", "");
			}
			// Player Joined
			if (key.equals("multiplayer.player.joined")) {
				if (settings.getJoinFormat() == null) {
					return;
				}
				message = settings.getJoinFormat().replace("%message%", message);
			}
			// Player Left
			if (key.equals("multiplayer.player.left")) {
				if (settings.getLeaveFormat() == null) {
					return;
				}
				message = settings.getLeaveFormat().replace("%message%", message);
			}
			// /Say
			if (key.equals("chat.type.announcement")) {
				if (settings.getAnnouncementFormat() == null) {
					return;
				}
				message = settings.getAnnouncementFormat().replace("%message%", message);
			}
			// Death
			if (key.contains("death")) {
				if (settings.getDeathFormat() == null) {
					return;
				}
				message = settings.getDeathFormat().replace("%message%", message);
			}
			// /me Chat
			if (key.equals("chat.type.emote")) {
				if (settings.getEmoteFormat() == null) {
					return;
				}
				message = settings.getEmoteFormat()
						.replace("%sender%", ((ChatType.Bound) msg.getArgs()[0]).name().getString())
						.replace("%message%", ((Component) msg.getArgs()[1]).getString());
			}
			// Regular Chat
			if (key.equals("chat.type.text")) {
				if (settings.getChatFormat() == null) {
					return;
				}
				message = settings.getChatFormat()
						.replace("%sender%", ((ChatType.Bound) msg.getArgs()[0]).name().getString())
						.replace("%message%", ((PlainTextContents) msg.getArgs()[1]).text());
			}
			message = findMention(message);
			try {
				jda.awaitReady();
				jda.getTextChannelById(settings.getChannelId()).sendMessage(message).queue();
			} catch (Exception e) {
				log(Level.ERROR, "Exception thrown while sending a message to Discord!", e);
			}
		});
	}

	private static String findMention(String message) {
		StringBuilder builder = new StringBuilder();
		int i = 0;
		while (i < message.length()) {
			if (message.charAt(i) == '@') {
				// Longest name wins, so "@Big Steve" beats "@Big"; on a tie a username wins, since those are unique
				String bestId = null;
				int bestLength = 0;
				boolean bestIsUsername = false;
				for (Map.Entry<String, List<String>> entry : mentionables.entrySet()) {
					List<String> names = entry.getValue();
					for (int n = 0; n < names.size(); n++) {
						String name = names.get(n);
						boolean isUsername = n == 0;
						boolean better = name.length() > bestLength
								|| (name.length() == bestLength && isUsername && !bestIsUsername);
						if (better && isMentionAt(message, i + 1, name)) {
							bestId = entry.getKey();
							bestLength = name.length();
							bestIsUsername = isUsername;
						}
					}
				}
				if (bestId != null) {
					builder.append(UserSnowflake.fromId(bestId).getAsMention());
					i += 1 + bestLength;
					continue;
				}
			}
			builder.append(message.charAt(i));
			i++;
		}
		return builder.toString();
	}

	// The name has to follow the @ (any case) and not run on into more letters or digits,
	// so "@alice," matches alice but "@alicebob" doesn't
	private static boolean isMentionAt(String message, int start, String name) {
		if (name.isEmpty() || !message.regionMatches(true, start, name, 0, name.length())) {
			return false;
		}
		int end = start + name.length();
		return end == message.length() || !(Character.isLetterOrDigit(message.charAt(end)) || message.charAt(end) == '_');
	}

	public static void sendStartMessage() {
		ES.execute(() -> {
			try {
				jda.awaitReady();
				jda.getTextChannelById(settings.getChannelId()).sendMessage(settings.getStartFormat()).queue();
			} catch (Exception e) {
				log(Level.ERROR, "Could not send start message to Discord!", e);
			}
		});
	}

	public static void sendStopMessageAndShutdown() {
		ES.execute(() -> {
			try {
				jda.awaitReady();
				jda.getTextChannelById(settings.getChannelId()).sendMessage(settings.getStopFormat()).queue();
				jda.shutdown();
			} catch (Exception e) {
				log(Level.ERROR, "Could not send stop message to Discord!", e);
			}
		});
	}

	public static void log(Level level, String message) {
		LOGGER.log(level, message);
	}

	public static void log(Level level, String message, Throwable t) {
		LOGGER.log(level, message, t);
	}

	public static Settings getSettings() {
		return settings;
	}

	private static void loadDiscordMentionables() {
		Guild g = jda.getTextChannelById(SettingsManager.getInstance().getSettings().getChannelId()).getGuild();
		for (Member member : g.loadMembers().get()) {
			updateMentionable(member);
		}
	}

	public static void updateMentionable(Member member) {
		User u = member.getUser();
		List<String> names = new ArrayList<>();
		names.add(u.getName());
		if (u.getGlobalName() != null) {
			names.add(u.getGlobalName());
		}
		if (member.getNickname() != null) {
			names.add(member.getNickname());
		}
		mentionables.put(u.getId(), names);
	}

	public static void removeMentionable(String userId) {
		mentionables.remove(userId);
	}

	public static void setLastMessage(String lastMessage) {
		Interweave.lastMessage = lastMessage;
	}

	public static String getLastMessage() {
		return lastMessage;
	}

	public static void setPlayers(int current, int max) {
		ES.execute(() -> {
			try {
				jda.getPresence().setActivity(Activity.of(Activity.ActivityType.PLAYING, current + "/" + max));
			} catch (Exception e) {
				log(Level.ERROR, "Could not set player count!", e);
			}
		});

	}
}