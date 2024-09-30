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
        return true; // 이 확장이 서버가 재시작되어도 계속 유지되도록 설정
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        // 플레이스홀더가 %star_title%인 경우
        if (identifier.equals("title")) {
            UUID playerId = player.getUniqueId();
            String activeTitle = plugin.getActiveTitle(playerId);

            return (activeTitle != null && !activeTitle.isEmpty()) ? activeTitle : "장착중인 칭호가 없습니다";
        }

        return null; // 등록된 플레이스홀더가 없을 경우
    }
}
