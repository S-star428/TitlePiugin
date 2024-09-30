package com.star.titleplugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TitlePlugin extends JavaPlugin implements Listener, TabExecutor, TabCompleter {

    private final HashMap<UUID, List<String>> playerTitles = new HashMap<>();
    private final HashMap<UUID, String> activeTitles = new HashMap<>();
    private final HashMap<UUID, UUID> deleteTargets = new HashMap<>();
    private File titlesFile;
    private FileConfiguration titlesConfig;
    private File soundFile;
    private FileConfiguration soundConfig;
    private File messageFile;
    private FileConfiguration messageConfig;

    private static final String TITLE_BOOK_METADATA = "titleBook";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSoundConfig(); // sound.yml 로드
        loadMessageConfig(); // message.yml 로드
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("칭호").setExecutor(this);
        getCommand("칭호").setTabCompleter(this);
        loadTitles();
        startUpdateTask();
        getLogger().info(ChatColor.YELLOW + "[ S - Title ] 플러그인이 활성화 되었습니다.");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // PlaceholderAPI가 서버에 설치되어 있음
            new TitlePlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI를 성공적으로 찾았습니다!");
        } else {
            getLogger().warning("PlaceholderAPI를 찾을 수 없습니다!");
        }

    }

    @Override
    public void onDisable() {
        saveTitles();
        getLogger().info(ChatColor.GOLD + "[ S - Title ]" + ChatColor.WHITE + "플러그인이 비활성화 되었습니다.");
    }

    private void loadTitles() {
        titlesFile = new File(getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            try {
                titlesFile.createNewFile();
                titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);
                titlesConfig.save(titlesFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);
        }

        for (String key : titlesConfig.getKeys(false)) {
            UUID playerId = UUID.fromString(key);
            List<String> titles = titlesConfig.getStringList(key + ".titles");
            String activeTitle = titlesConfig.getString(key + ".activeTitle", null);
            playerTitles.put(playerId, titles);
            activeTitles.put(playerId, activeTitle);
        }
    }

    private void saveTitles() {
        for (UUID playerId : playerTitles.keySet()) {
            titlesConfig.set(playerId.toString() + ".titles", playerTitles.get(playerId));
            titlesConfig.set(playerId.toString() + ".activeTitle", activeTitles.get(playerId));
        }

        try {
            titlesConfig.save(titlesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadSoundConfig() {
        soundFile = new File(getDataFolder(), "sound.yml");
        if (!soundFile.exists()) {
            saveResource("sound.yml", false);
        }
        soundConfig = YamlConfiguration.loadConfiguration(soundFile);
    }

    private void loadMessageConfig() {
        messageFile = new File(getDataFolder(), "message.yml");
        if (!messageFile.exists()) {
            saveResource("message.yml", false);
        }
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    private String getMessageWithPrefix(String key, String defaultMessage, String prefix) {
        String message = messageConfig.getString(key, defaultMessage).replace("prefix", prefix);
        String fixedPrefix = ChatColor.WHITE + "[ " + ChatColor.AQUA + "S - Title" + ChatColor.WHITE + " ] ";
        return fixedPrefix + ChatColor.GREEN + message;
    }

    private String getMessageWithPlayerAndPrefix(String key, String defaultMessage, String playerName, String prefix) {
        String message = messageConfig.getString(key, defaultMessage).replace("player", playerName).replace("prefix", prefix);
        String fixedPrefix = ChatColor.WHITE + "[ " + ChatColor.AQUA + "S - Title" + ChatColor.WHITE + " ] ";
        return fixedPrefix + ChatColor.GREEN + message;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.WHITE + "===========[" + ChatColor.AQUA + " S - Title " + ChatColor.WHITE + "]===========");
                player.sendMessage(ChatColor.WHITE + "/칭호 열기 : 자신의 칭호창을 엽니다.");
                player.sendMessage(ChatColor.WHITE + "/칭호 제작 [원하는 칭호] : 자신의 원하는 칭호를 제작하여 인벤토리에 지급합니다.");
                player.sendMessage(ChatColor.WHITE + "/칭호 삭제 [플레이어 닉네임] : 해당 플레이어의 칭호창을 열어 본인이 삭제하고 싶은 칭호를 삭제합니다.");
                player.sendMessage(ChatColor.WHITE + "/칭호 리로드 : 설정 파일을 리로드합니다.");
                player.sendMessage(ChatColor.WHITE + "=================================");
                return true;
            }

            switch (args[0]) {
                case "열기":
                    openTitleGUI(player);
                    break;
                case "제작":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "사용법: /칭호 제작 [원하는 칭호]");
                    } else {
                        String title = ChatColor.translateAlternateColorCodes('&', String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                        giveTitleBook(player, title);
                    }
                    break;
                case "삭제":
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "사용법: /칭호 삭제 [플레이어닉네임]");
                    } else {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target != null) {
                            openTitleDeleteGUI(player, target);
                        } else {
                            player.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다.");
                        }
                    }
                    break;
                case "리로드":
                    reloadConfig();
                    loadSoundConfig(); // sound.yml 리로드
                    loadMessageConfig(); // message.yml 리로드
                    player.sendMessage(ChatColor.WHITE + "[ " + ChatColor.AQUA + "S - Title" + ChatColor.WHITE + " ] 설정, 출력 메세지, 사운드 파일이 리로드되었습니다.");
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "[ Error ] 알 수 없는 명령어입니다.");
                    break;
            }
        }
        return true;
    }

    private void openTitleGUI(Player player) {
        UUID playerId = player.getUniqueId();
        int guiRows = getValidGuiRows();
        Inventory inv = Bukkit.createInventory(null, guiRows * 9, ChatColor.GREEN + player.getName() + "님의 칭호 현황");

        List<String> titles = playerTitles.getOrDefault(playerId, new ArrayList<>());
        String activeTitle = activeTitles.get(playerId);

        for (String title : titles) {
            ItemStack item;
            if (title.equals(activeTitle)) {
                item = getConfigItem("Title_book_Icon", title);
                item = updateConfigItemWithLore(item, "Title_book_Icon_select");
            } else {
                item = getConfigItem("Title_book_Icon", title);
            }
            inv.addItem(item);
        }

        player.openInventory(inv);
    }

    private void giveTitleBook(Player player, String title) {
        ItemStack book = getConfigItem("Title_book", title);
        ItemMeta meta = book.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(this, TITLE_BOOK_METADATA), PersistentDataType.STRING, "true");
        book.setItemMeta(meta);
        player.getInventory().addItem(book);
        String message = getMessageWithPrefix("give_titleBook_Message", "칭호북이 인벤토리에 추가되었습니다: prefix", title);
        player.sendMessage(message);

        // 칭호북 지급 시 소리 재생
        playSound(player, "title_get_sound", "ENTITY_PLAYER_LEVELUP");
    }

    private void openTitleDeleteGUI(Player admin, Player target) {
        UUID targetId = target.getUniqueId();
        int guiRows = getValidGuiRows();
        Inventory inv = Bukkit.createInventory(null, guiRows * 9, "칭호 삭제 - " + target.getName());

        List<String> titles = playerTitles.getOrDefault(targetId, new ArrayList<>());
        for (String title : titles) {
            ItemStack item = getConfigItem("Title_book_del", title);
            inv.addItem(item);
        }

        deleteTargets.put(admin.getUniqueId(), targetId); // 저장 삭제 대상
        admin.openInventory(inv);
    }

    private int getValidGuiRows() {
        int guiRows = getConfig().getInt("gui_row", 3);
        if (guiRows < 1 || guiRows > 6) {
            guiRows = 3;
        }
        return guiRows;
    }

    private ItemStack getConfigItem(String configPath, String title) {
        String type = getConfig().getString(configPath + ".type", "BOOK");
        String name = getConfig().getString(configPath + ".name", "").replace("prefix", title);
        List<String> loreList = getConfig().getStringList(configPath + ".lore");
        if (loreList.isEmpty()) {
            // config 파일에 lore가 없을 경우 단일 문자열로 처리
            String lore = getConfig().getString(configPath + ".lore", "").replace("prefix", title);
            loreList = Arrays.asList(lore);
        } else {
            loreList = loreList.stream().map(lore -> lore.replace("prefix", title)).collect(Collectors.toList());
        }

        ItemStack item = new ItemStack(Material.getMaterial(type));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(loreList.stream().map(lore -> ChatColor.translateAlternateColorCodes('&', lore)).collect(Collectors.toList()));
        item.setItemMeta(meta);

        return item;
    }

    private String extractPrefix(String name) {
        if (name.contains("prefix")) {
            return name.substring(name.indexOf("prefix")).replace("prefix", "").trim();
        }
        return name;
    }

    private ItemStack updateConfigItemWithLore(ItemStack item, String configPath) {
        ItemMeta meta = item.getItemMeta();
        List<String> loreList = getConfig().getStringList(configPath + ".lore");
        if (loreList.isEmpty()) {
            // config 파일에 lore가 없을 경우 단일 문자열로 처리
            String lore = getConfig().getString(configPath + ".lore", "").replace("prefix", extractPrefix(meta.getDisplayName()));
            loreList = Arrays.asList(lore);
        } else {
            loreList = loreList.stream().map(lore -> lore.replace("prefix", extractPrefix(meta.getDisplayName()))).collect(Collectors.toList());
        }
        meta.setLore(loreList.stream().map(lore -> ChatColor.translateAlternateColorCodes('&', lore)).collect(Collectors.toList()));
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.getMaterial(getConfig().getString("Title_book.type", "BOOK"))) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getPersistentDataContainer().has(new NamespacedKey(this, TITLE_BOOK_METADATA), PersistentDataType.STRING)) {
                String fullName = meta.getDisplayName();
                String title = extractPrefix(fullName);
                UUID playerId = player.getUniqueId();

                if (playerTitles.getOrDefault(playerId, new ArrayList<>()).contains(title)) {
                    player.sendMessage(ChatColor.RED + "이미 보유한 칭호입니다: " + title);
                    return;
                }

                playerTitles.computeIfAbsent(playerId, k -> new ArrayList<>()).add(title);
                String message = getMessageWithPrefix("add_Title_message", "칭호를 획득했습니다: prefix", title);
                player.sendMessage(message);
                player.getInventory().remove(item);

                // 칭호 획득 시 소리 재생
                playSound(player, "title_add_sound", "UI_TOAST_CHALLENGE_COMPLETE");

                openTitleGUI(player); // Automatically open the title GUI after getting a new title
            }
        }
    }

//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onPlayerChat(AsyncPlayerChatEvent event) {
//        Player player = event.getPlayer();
//        UUID playerId = player.getUniqueId();
//        String activeTitle = activeTitles.get(playerId);
//
//        event.setCancelled(true);
//        if (activeTitle != null) {
//            String format = ChatColor.translateAlternateColorCodes('&', activeTitle + " " + player.getName() + ChatColor.RESET + " : " + event.getMessage());
//            Bukkit.broadcastMessage(format);
//        } else {
//            Bukkit.broadcastMessage(player.getName() + ": " + event.getMessage());
//        }
//    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String activeTitle = activeTitles.get(playerId);

//        event.setCancelled(true);
//        String format = ChatColor.translateAlternateColorCodes('&', player.getName() + ChatColor.RESET + " : " + event.getMessage());
//        Bukkit.broadcastMessage(format);
        if (activeTitle != null) {
            event.setFormat(activeTitle + " " + player.getName() + " : " + event.getMessage());
        }else{
            event.setFormat(player.getName() + " : " + event.getMessage());
        }

    }
//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onPlayerChat(AsyncPlayerChatEvent event) {
//        Player player = event.getPlayer();
//        UUID playerId = player.getUniqueId();
//        String activeTitle = activeTitles.get(playerId);
//
//        if (!event.getMessage().startsWith("/")) {
//            event.setCancelled(true);
//
//            String format;
//            if (activeTitle != null) {
//                format = ChatColor.translateAlternateColorCodes('&', activeTitle + " " + player.getName() + ChatColor.RESET + " : " + event.getMessage());
//            } else {
//                format = player.getName() + ": " + event.getMessage();
//                event.setFormat(format);
//            }
//            Bukkit.broadcastMessage(format);
//        } else {
//            event.setCancelled(true);
//        }
//    }



    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle().contains("님의 칭호 현황")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.getMaterial(getConfig().getString("Title_book_Icon.type", "NAME_TAG"))) {
                String fullName = item.getItemMeta().getDisplayName();
                String title = extractPrefix(fullName);
                UUID playerId = player.getUniqueId();

                if (event.isShiftClick() && title.equals(activeTitles.get(playerId))) {
                    // 칭호 해제
                    activeTitles.remove(playerId);
                    String message = getMessageWithPrefix("unequip_Title_message", "칭호가 해제되었습니다: prefix", title);
                    player.sendMessage(message);

                    // 칭호 해제 시 소리 재생
                    playSound(player, "title_unselect_sound", "ENTITY_VILLAGER_NO");
                } else {
                    // 칭호 장착
                    activeTitles.put(playerId, title);
                    String message = getMessageWithPrefix("equip_Title_message", "칭호가 장착되었습니다: prefix", title);
                    player.sendMessage(message);

                    // 칭호 장착 시 소리 재생
                    playSound(player, "title_select_sound", "ENTITY_VILLAGER_YES");
                }

                player.closeInventory();
                updatePlayerDisplayName(player);
                openTitleGUI(player); // Update the GUI to show the active title status
            }
        } else if (event.getView().getTitle().startsWith("칭호 삭제 - ")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.getMaterial(getConfig().getString("Title_book_del.type", "BOOK"))) {
                String fullName = item.getItemMeta().getDisplayName();
                String title = extractPrefix(fullName);
                UUID adminId = player.getUniqueId();
                UUID targetId = deleteTargets.get(adminId);

                if (targetId != null) {
                    List<String> titles = playerTitles.get(targetId);

                    if (titles != null && titles.remove(title)) {
                        Player target = Bukkit.getPlayer(targetId);
                        if (target != null) {
                            if (activeTitles.get(targetId).equals(title)) {
                                activeTitles.remove(targetId);
                                updatePlayerDisplayName(target);
                            }
                            String message = getMessageWithPlayerAndPrefix("delete_Title_message", "player님의 칭호가 삭제되었습니다: prefix", target.getName(), title);
                            target.sendMessage(message);
                        }
                        player.closeInventory();
                    } else {
                        player.sendMessage(ChatColor.RED + "[ Error ] 칭호를 삭제할 수 없습니다.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "[ Error ] 플레이어를 찾을 수 없습니다.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!playerTitles.containsKey(playerId)) {
            String defaultPrefix = getConfig().getString("default_prefix", "");
            if (!defaultPrefix.isEmpty()) {
                playerTitles.computeIfAbsent(playerId, k -> new ArrayList<>()).add(defaultPrefix);
                activeTitles.put(playerId, defaultPrefix);
            }
        }
        updatePlayerDisplayName(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        resetPlayerDisplayName(player);
    }

    private void updatePlayerDisplayName(Player player) {
        UUID playerId = player.getUniqueId();
        String activeTitle = activeTitles.get(playerId);

        if (activeTitle != null && !activeTitle.isEmpty()) {
            // 칭호가 있을 때는 칭호와 닉네임을 함께 표시
            player.setPlayerListName(activeTitle + " " + player.getName());
            player.setDisplayName(activeTitle + " " + player.getName());
        } else {
            // 칭호가 없을 때는 닉네임만 표시
            player.setPlayerListName(player.getName());
            player.setDisplayName(player.getName());
        }
    }

    private void resetPlayerDisplayName(Player player) {
        player.setPlayerListName(player.getName());
        player.setDisplayName(player.getName());
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updatePlayerDisplayName(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 1초마다 업데이트
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("칭호")) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                completions.add("열기");
                completions.add("제작");
                completions.add("삭제");
                completions.add("리로드");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("삭제")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return completions;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("제작")) {
                completions.add("[원하는 칭호]");
                return completions;
            }
            return completions;
        }
        return null;
    }

    private void playSound(Player player, String soundKey, String defaultSound) {
        if (soundConfig.getBoolean("sound_switch." + soundKey, true)) {
            String soundName = soundConfig.getString(soundKey, defaultSound);
            Sound sound;
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                sound = Sound.valueOf(defaultSound);
            }
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    public String getActiveTitle(UUID playerId) {
        return activeTitles.get(playerId);
    }
}