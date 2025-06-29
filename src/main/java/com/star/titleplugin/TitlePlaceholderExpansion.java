package com.star.titleplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TitlePlaceholderExpansion extends PlaceholderExpansion {

    private final TitlePlugin plugin;

    public TitlePlaceholderExpansion(TitlePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "star";
    }

    @Override
    public @NotNull String getAuthor() {
        return "star";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; //서버가 재시작되어도 계속 유지되도록 설정
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        UUID playerId = player.getUniqueId();
        TitleData activeTitleData = plugin.getActiveTitle(playerId);

        // 머리 위 네임태그 전용 플레이스홀더: %star_title_nametag%
        if (identifier.equals("title_nametag")) {
            if (activeTitleData == null || activeTitleData.getDisplay() == null) {
                return ""; // 칭호 없으면 빈 문자열
            }
            String display = activeTitleData.getDisplay();
            // MiniMessage (<gradient:...>)
            if (display.contains("<") && display.contains(">")) {
                return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(display));
            }
            // &색코드
            if (display.contains("&") || display.contains("§")) {
                return org.bukkit.ChatColor.translateAlternateColorCodes('&', display);
            }
            return display;
        }

        // 스코어보드/GUI/채팅 안내용: %star_title% 또는 %star_title_scoreboard%
        if (identifier.equals("title") || identifier.equals("title_scoreboard")) {
            if (activeTitleData == null || activeTitleData.getDisplay() == null) {
                return "장착중인 칭호 없음";
            }
            String display = activeTitleData.getDisplay();
            // MiniMessage (<gradient:...>)
            if (display.contains("<") && display.contains(">")) {
                return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(display));
            }
            // &색코드
            if (display.contains("&") || display.contains("§")) {
                return org.bukkit.ChatColor.translateAlternateColorCodes('&', display);
            }
            return display;
        }

        return null; // 등록되지 않은 식별자
    }
}