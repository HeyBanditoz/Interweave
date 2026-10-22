package utils;

import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import main.Interweave;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.players.PlayerList;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

public class DiscordListener extends ListenerAdapter {
    private PlayerList pm;

    public DiscordListener() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> pm = server.getPlayerList());
    }

    // Keep the names players can @ping by in sync with the bridged guild
    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        if (isBridgedGuild(event.getGuild())) {
            Interweave.updateMentionable(event.getMember());
        }
    }

    // Fires for nickname, display name and username changes, even though members aren't cached
    @Override
    public void onGuildMemberUpdate(@NotNull GuildMemberUpdateEvent event) {
        if (isBridgedGuild(event.getGuild())) {
            Interweave.updateMentionable(event.getMember());
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (isBridgedGuild(event.getGuild())) {
            Interweave.removeMentionable(event.getUser().getId());
        }
    }

    private boolean isBridgedGuild(Guild guild) {
        return guild.getTextChannelById(Interweave.getSettings().getChannelId()) != null;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (pm == null) {
            return;
        }
        JDA jda = event.getJDA();

        User author = event.getAuthor();
        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();

        if (!channel.getId().equals(Interweave.getSettings().getChannelId())) {
            return;
        }
        if (author.getId().equals(jda.getSelfUser().getId())) {
            return;
        }
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }
        // Guild nickname, falling back to the user's display name (webhook messages have no member)
        String senderName = event.getMember() != null ? event.getMember().getEffectiveName() : author.getEffectiveName();
        Interweave.log(Level.INFO, "<" + senderName + "> " + event.getMessage().getContentDisplay());
        if (SettingsManager.getInstance().getSettings().getDingPlayersIfNameFound() != null && SettingsManager.getInstance().getSettings().getDingPlayersIfNameFound()) { // :(
            pm.getPlayers().stream()
                    .filter(spe -> mentionsPlayer(event.getMessage().getContentDisplay(), spe.getName().getString()))
                    .forEach(PlayerUtils::dingPlayer);
        }
        try {
            String authorHover = event.getGuild().getName()
                    + " §o#" + event.getChannel().getName()
                    + "§r\n" + (senderName.equals(author.getName()) ? senderName : senderName + " (" + author.getName() + ")") + ' '
                    + (author.isBot() ? "§3Bot§r" : "§2User§r")
                    + '\n' + author.getId();
            Style style = Style.EMPTY
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(authorHover)))
                    .withClickEvent(new ClickEvent.SuggestCommand(author.getAsMention()));
            String messageText = Interweave.getSettings().getDiscordToMinecraftFormat().replace("%sender%", senderName).replace("%message%", message.getContentDisplay());
            if (message.getAttachments().size() > 0) {
                messageText = messageText + " " + buildAttachmentsUrl(message.getAttachments()); // append attachments URLs to the end of the message
            }
            if (message.getEmbeds().size() > 0) {
                messageText = messageText + '\n' + buildEmbeds(message.getEmbeds());
            }
            pm.broadcastSystemMessage(Component.literal(messageText).setStyle(style),false);
            Interweave.setLastMessage(messageText);
        } catch (Exception ex) {
            Interweave.log(Level.ERROR, "Could not send Discord message to Minecraft!", ex);
        }
    }

    // Minecraft names are only letters, digits and underscores, so \b matches whole names
    private boolean mentionsPlayer(String text, String playerName) {
        return Pattern.compile("\\b" + Pattern.quote(playerName) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private String buildAttachmentsUrl(List<Message.Attachment> attachments) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Message.Attachment attachment : attachments) {
            joiner.add(attachment.getUrl());
        }
        return joiner.toString();
    }

    private String buildEmbeds(List<MessageEmbed> embeds) {
        StringJoiner joiner = new StringJoiner("\n");
        for (MessageEmbed e : embeds) {
            joiner.add(EmbedFormatter.formatEmbed(e));
        }
        return joiner.toString();
    }
}
