package com.star.titleplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TitlePlugin extends JavaPlugin implements Listener, TabExecutor, TabCompleter {

    private final HashMap<UUID, List<TitleData>> playerTitles = new HashMap<>();
    private final HashMap<UUID, TitleData> activeTitles = new HashMap<>();
    private final HashMap<UUID, UUID> deleteTargets = new HashMap<>();
    private final HashMap<String, PotionEffectType> titleEffects = new HashMap<>();
    private final HashMap<UUID, PotionEffectType> pendingEffects = new HashMap<>();
    private final HashMap<UUID, Integer> pendingEffectLevels = new HashMap<>();
    private static final String TITLE_BOOK_METADATA = "title_book";
    private static final String EFFECT_METADATA = "title_effect";
    private static final String LEVEL_METADATA = "title_level";
    private static final String TITLE_PREFIX = "<gradient:#7CFC00:#00FFFF>[ S - Title ]</gradient> ";

    private File titlesFile;
    private FileConfiguration titlesConfig;
    private File soundFile;
    private FileConfiguration soundConfig;
    private File messageFile;
    private FileConfiguration messageConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSoundConfig();
        loadMessageConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("칭호").setExecutor(this);
        getCommand("칭호").setTabCompleter(this);
        loadTitles();
        loadEffects();
        startUpdateTask();
        getLogger().info(ChatColor.YELLOW + "[ S - Title ] 플러그인이 활성화 되었습니다.");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
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

    private Component toMiniMessageComponent(String key, String fallback, String prefixRaw) {
        String msgRaw = messageConfig.getString(key, fallback);

        String parsedPrefix;
        if (prefixRaw.contains("<") && prefixRaw.contains(">")) {
            parsedPrefix = prefixRaw;
        } else if (prefixRaw.contains("&")) {
            Component legacy = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixRaw);
            parsedPrefix = MiniMessage.miniMessage().serialize(legacy);
        } else {
            parsedPrefix = MiniMessage.miniMessage().serialize(Component.text(prefixRaw));
        }

        String finalMsg = msgRaw.replace("prefix", parsedPrefix);
        return MiniMessage.miniMessage().deserialize(finalMsg);
    }

    private String addTitlePrefix(String message) {
        // 이미 prefix가 있으면 중복 방지 (선택)
        if (message != null && !message.startsWith(TITLE_PREFIX)) {
            return TITLE_PREFIX + message;
        }
        return message;
    }

    private void loadTitles() {
        titlesFile = new File(getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            try {
                titlesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);

        for (String key : titlesConfig.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                List<Map<?, ?>> titleSection = titlesConfig.getMapList(key + ".titles");
                List<TitleData> titles = new ArrayList<>();
                for (Map<?, ?> map : titleSection) {
                    String name = (String) map.get("name");
                    String display = (String) map.get("display");
                    String typeStr = (String) map.get("type");
                    TitleData.Type type = TitleData.Type.valueOf(typeStr);
                    titles.add(new TitleData(name, display, type));
                }
                Map<?,?> activeMap = titlesConfig.getConfigurationSection(key + ".activeTitle") != null
                        ? titlesConfig.getConfigurationSection(key + ".activeTitle").getValues(false)
                        : null;
                TitleData activeTitle = null;
                if (activeMap != null) {
                    String name = (String) activeMap.get("name");
                    String display = (String) activeMap.get("display");
                    String typeStr = (String) activeMap.get("type");
                    TitleData.Type type = TitleData.Type.valueOf(typeStr);
                    activeTitle = new TitleData(name, display, type);
                }
                playerTitles.put(playerId, titles);
                if (activeTitle != null)
                    activeTitles.put(playerId, activeTitle);
            } catch (IllegalArgumentException e) {
                getLogger().warning("[S-Title] 잘못된 UUID: " + key);
            }
        }
    }

    private void saveTitles() {
        for (UUID playerId : playerTitles.keySet()) {
            List<Map<String,Object>> titlesList = new ArrayList<>();
            for (TitleData title : playerTitles.get(playerId)) {
                Map<String,Object> map = new HashMap<>();
                map.put("name", title.getName());
                map.put("display", title.getDisplay());
                map.put("type", title.getType().name());
                titlesList.add(map);
            }
            titlesConfig.set(playerId.toString() + ".titles", titlesList);

            TitleData active = activeTitles.get(playerId);
            if (active != null) {
                Map<String,Object> map = new HashMap<>();
                map.put("name", active.getName());
                map.put("display", active.getDisplay());
                map.put("type", active.getType().name());
                titlesConfig.set(playerId.toString() + ".activeTitle", map);
            } else {
                titlesConfig.set(playerId.toString() + ".activeTitle", null);
            }
            String nickname = Bukkit.getOfflinePlayer(playerId).getName();
            titlesConfig.set(playerId.toString() + ".nickname", nickname != null ? nickname : "UnknownPlayer");
        }
        try {
            titlesConfig.save(titlesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public TitleData getActiveTitle(UUID playerId) {
        return activeTitles.get(playerId);
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
        String fixedPrefix = "<white>[ <aqua>S - Title</aqua> ] ";
        return fixedPrefix + "<green>" + message + "</green>";
    }

    private String getMessageWithPlayerAndPrefix(String key, String defaultMessage, String playerName, String prefix) {
        String message = messageConfig.getString(key, defaultMessage).replace("player", playerName).replace("prefix", prefix);
        String fixedPrefix = "<white>[ <aqua>S - Title</aqua> ] ";
        return fixedPrefix + "<green>" + message + "</green>";
    }

    // --- MiniMessage Gradient Util ---
    public static String createMiniMessageGradient(String text, String startHex, String endHex) {
        return "<gradient:" + startHex + ":" + endHex + ">" + text + "</gradient>";
    }
    public static Component miniMessageToComponent(String mm) {
        return MiniMessage.miniMessage().deserialize(mm);
    }

    public static Component getTitleComponent(String titleMiniMsg) {
        if (titleMiniMsg == null || titleMiniMsg.isEmpty()) return Component.empty();
        return MiniMessage.miniMessage().deserialize(titleMiniMsg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MiniMessage mm = MiniMessage.miniMessage();
        Component component1 = mm.deserialize("<White>===========</White> "+ TITLE_PREFIX +"<White>===========</White>");
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(component1);
                player.sendMessage(Component.text("/칭호 열기 : 자신의 칭호창을 엽니다."));
                player.sendMessage(Component.text("/칭호 제작 [원하는 칭호] : 일반 칭호 제작"));
                player.sendMessage(Component.text("/칭호 제작 gradient <HEX시작색> <HEX끝색> <텍스트> : 그라데이션 칭호 제작"));
                player.sendMessage(Component.text("/칭호 삭제 [플레이어 닉네임] : 해당 플레이어의 칭호창을 열어 칭호를 삭제"));
                player.sendMessage(Component.text("/칭호 리로드 : 설정 파일을 리로드합니다."));
                player.sendMessage(Component.text("================================="));
                return true;
            }

            switch (args[0]) {
                case "열기":
                    openTitleGUI(player);
                    break;
                case "제작":
                    // 그라데이션 칭호: /칭호 제작 gradient #ff0000 #00ff00 텍스트
                    if (args.length >= 5 && args[1].equalsIgnoreCase("gradient")) {
                        String startHex = args[2];
                        String endHex = args[3];
                        String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                        giveGradientTitleBook(player, text, startHex, endHex);
                        break;
                    }
                    // /칭호 제작 [원하는 칭호]
                    if (args.length >= 2) {
                        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
// colorCode는 상황에 맞게 추출(예: args[1]이 colorCode면 분리해서 사용)
                        String colorCode = "&f"; // 필요시 파싱
                        String display = colorCode + text;
                        TitleData title = new TitleData(text, display, TitleData.Type.NORMAL);

                        giveNormalTitleBook(player, title);
                    } else {
                        player.sendMessage(Component.text("사용법: /칭호 제작 [원하는 칭호]"));
                    }
                    break;
                case "삭제":
                    if (args.length < 2) {
                        player.sendMessage(Component.text("사용법: /칭호 삭제 [플레이어닉네임]"));
                    } else {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target != null) {
                            openTitleDeleteGUI(player, target);
                        } else {
                            player.sendMessage(Component.text("플레이어를 찾을 수 없습니다."));
                        }
                    }
                    break;
                case "리로드":
                    reloadConfig();
                    loadSoundConfig();
                    loadMessageConfig();
                    Component Comreload = mm.deserialize(TITLE_PREFIX + "설정, 출력 메세지, 사운드 파일이 리로드되었습니다.");
                    player.sendMessage(Comreload);
                    break;
                case "효과설정":
                    if (!isHoldingTitleBook(player)) {
                        player.sendMessage(Component.text("칭호북을 들고 있어야 합니다!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                        return true;
                    }
                    openEffectGUI(player);
                    break;
                default:
                    player.sendMessage(Component.text("[ Error ] 알 수 없는 명령어입니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                    break;
            }
        }
        return true;
    }

    // --- 칭호북 지급 (일반/그라데이션) ---
    private void giveNormalTitleBook(Player player, TitleData title) {
        giveTitleBookItem(player, title);
        MiniMessage mm = MiniMessage.miniMessage();
        String raw = messageConfig.getString("give_titleBook_Message", "&f칭호북이 인벤토리에 추가되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
        );

        String prefix = title.getDisplay();

        String parsedPrefix;
        if (prefix.contains("<") && prefix.contains(">")) {
            parsedPrefix = prefix;
        } else if (prefix.contains("&")) {
            parsedPrefix = MiniMessage.miniMessage().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(prefix)
            );
        } else {
            parsedPrefix = MiniMessage.miniMessage().serialize(Component.text(prefix));
        }

        String finalMsg = msgMini.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_get_sound", "ENTITY_PLAYER_LEVELUP");
    }

    private void giveGradientTitleBook(Player player, String text, String startHex, String endHex) {
        // 1. 칭호 MiniMessage 포맷에 <reset> 추가 (이탤릭 방지)
        String mm = "<!i><gradient:" + startHex + ":" + endHex + ">" + text + "</gradient>";
        TitleData title = new TitleData(text, mm, TitleData.Type.GRADIENT);
        giveTitleBookItem(player, title);

        // 2. 메시지는 config에서 MiniMessage 포맷으로 관리 추천!
        // 예: "give_titleBook_Message: '<white>칭호북이 인벤토리에 추가되었습니다: <gradient:#00ff99:#ff00ff>prefix</gradient>'"
        String raw = messageConfig.getString("give_Gradient_TitleBook_Message", "<white>칭호북이 인벤토리에 추가되었습니다: {prefix}");

        raw = addTitlePrefix(raw);
        // 3. prefix 치환시에도 <reset> 추가해서 이탤릭이 번지지 않도록!
        String parsedPrefix = mm; // 이미 <reset>이 들어간 상태

        String finalMsg = raw.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(finalMsg));

        playSound(player, "title_get_sound", "ENTITY_PLAYER_LEVELUP");
    }

    private void giveTitleBookItem(Player player, TitleData title) {
        FileConfiguration config = getConfig();

        String path = title.getType() == TitleData.Type.GRADIENT ? "Gradient_Title_book" : "Title_book";
        Material type = Material.valueOf(config.getString(path + ".type", "BOOK"));
        String nameTemplate = config.getString(path + ".name", "{prefix}");
        List<String> loreTemplate = config.getStringList(path + ".lore");

        String name = nameTemplate.replace("{prefix}", title.getDisplay());

        List<Component> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            String replaced = line.replace("{prefix}", title.getDisplay());
            if (title.getType() == TitleData.Type.GRADIENT) {
                lore.add(MiniMessage.miniMessage().deserialize("<!i>" + replaced));
            } else {
                lore.add(Component.text(ChatColor.translateAlternateColorCodes('&', replaced)));
            }
        }

        ItemStack book = new ItemStack(type);
        ItemMeta meta = book.getItemMeta();

        if (title.getType() == TitleData.Type.GRADIENT) {
            meta.displayName(MiniMessage.miniMessage().deserialize(name));
        } else {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        meta.lore(lore);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(this, TITLE_BOOK_METADATA), PersistentDataType.STRING, "true");
        data.set(new NamespacedKey(this, "title_name"), PersistentDataType.STRING, title.getName());
        data.set(new NamespacedKey(this, "title_display"), PersistentDataType.STRING, title.getDisplay());
        data.set(new NamespacedKey(this, "title_type"), PersistentDataType.STRING, title.getType().name());

        book.setItemMeta(meta);
        player.getInventory().addItem(book);
    }

    private void openTitleGUI(Player player) {
        UUID playerId = player.getUniqueId();
        int guiRows = getValidGuiRows();
        Inventory inv = Bukkit.createInventory(null, guiRows * 9, ChatColor.GREEN + player.getName() + "님의 칭호 현황");

        List<TitleData> titles = playerTitles.getOrDefault(playerId, new ArrayList<>());
        TitleData activeTitle = activeTitles.get(playerId);

        int maxSlots = guiRows * 9;
        int count = 0;
        for (TitleData title : titles) {
            if (title == null) continue;

            // 아이템 생성
            ItemStack item;
            if (title.equals(activeTitle)) {
                item = getConfigItem("Title_book_Icon", title.getDisplay());
                item = updateConfigItemWithLore(item, "Title_book_Icon_select");
            } else {
                item = getConfigItem("Title_book_Icon", title.getDisplay());
            }

            // NBT에 칭호 정보 저장
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(new NamespacedKey(this, "title_name"), PersistentDataType.STRING, title.getName());
            data.set(new NamespacedKey(this, "title_display"), PersistentDataType.STRING, title.getDisplay());
            data.set(new NamespacedKey(this, "title_type"), PersistentDataType.STRING, title.getType().name());
            item.setItemMeta(meta);

            inv.addItem(item);

            if (++count >= maxSlots) break;
        }

        player.openInventory(inv);
    }


    private boolean isHoldingTitleBook(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        return data.has(new NamespacedKey(this, TITLE_BOOK_METADATA), PersistentDataType.STRING);
    }

    private void loadEffects() {
        titleEffects.clear();
        titleEffects.put("스피드", PotionEffectType.SPEED);
        titleEffects.put("힘", PotionEffectType.INSTANT_DAMAGE);
        titleEffects.put("점프", PotionEffectType.JUMP_BOOST);
    }

    private void openEffectGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "칭호 효과 설정");

        for (String title : titleEffects.keySet()) {
            ItemStack item = new ItemStack(Material.POTION, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + title);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "이 효과를 선택하면 칭호북에 적용됩니다.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    // 칭호북 우클릭: 획득 처리
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey bookKey = new NamespacedKey(this, TITLE_BOOK_METADATA);
        if (!data.has(bookKey, PersistentDataType.STRING)) return;
        if (item.getType() != Material.BOOK) return;

        String name = data.get(new NamespacedKey(this, "title_name"), PersistentDataType.STRING);
        String display = data.get(new NamespacedKey(this, "title_display"), PersistentDataType.STRING);
        String typeStr = data.get(new NamespacedKey(this, "title_type"), PersistentDataType.STRING);
        if (name == null || display == null || typeStr == null) return;

        TitleData.Type type;
        try {
            type = TitleData.Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return;
        }
        TitleData title = new TitleData(name, display, type);

        UUID playerId = player.getUniqueId();
        List<TitleData> titles = playerTitles.computeIfAbsent(playerId, k -> new ArrayList<>());

        if (titles.contains(title)) {
            sendAlreadyHaveMessage(player, title);
            event.setCancelled(true);
            return;
        }
        titles.add(title);

        if (title.getType() == TitleData.Type.GRADIENT) {
            sendGradientTitleObtainedMessage(player, title);
        } else {
            sendNormalTitleObtainedMessage(player, title);
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.setItemInHand(new ItemStack(Material.AIR));
        }
        event.setCancelled(true);
        updatePlayerDisplayName(player);


    }

    private void sendNormalTitleObtainedMessage(Player player, TitleData title) {
        String rawMsg = messageConfig.getString("give_titleBook_Message", "&e칭호를 획득했습니다: {prefix}")
                .replace("{prefix}", title.getDisplay());

        String miniMsg = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg)
        );
        playSound(player, "title_add_sound", "UI_TOAST_CHALLENGE_COMPLETE");
        String msgWithPrefix = TITLE_PREFIX + miniMsg;
        player.sendMessage(MiniMessage.miniMessage().deserialize(msgWithPrefix));
    }

    private void sendGradientTitleObtainedMessage(Player player, TitleData title) {
        String rawMsg = messageConfig.getString("add_Gradient_Title_message", "칭호를 획득했습니다: {prefix}")
                .replace("{prefix}", title.getDisplay());
        playSound(player, "title_add_sound", "UI_TOAST_CHALLENGE_COMPLETE");
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + rawMsg));
    }

    private void sendAlreadyHaveMessage(Player player, TitleData title) {
        String rawMsg;
        if (title.getType() == TitleData.Type.GRADIENT) {
            rawMsg = messageConfig.getString("already_have_Gradient_Title_message", "&c이미 보유한 칭호입니다: {prefix}")
                    .replace("{prefix}", title.getDisplay());
            rawMsg = addTitlePrefix(rawMsg);
            player.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg));
        } else {
            String deserialized = messageConfig.getString("already_have_Title_message", "&c이미 보유한 칭호입니다: {prefix}")
                    .replace("{prefix}", title.getDisplay());

            String miniMsg = MiniMessage.miniMessage().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(deserialized)
            );

            String msgWithPrefix = TITLE_PREFIX + miniMsg;
            player.sendMessage(MiniMessage.miniMessage().deserialize(msgWithPrefix));
        }
    }

    // MiniMessage 특수문자 이스케이프용 (필요 시)
    private String escapeMiniMessage(String str) {
        // MiniMessage에서 <, >, & 등 특수문자 이스케이프 필요 시 구현
        return str.replace("<", "&lt;").replace(">", "&gt;");
    }

    private void openTitleDeleteGUI(Player admin, Player target) {
        UUID targetId = target.getUniqueId();
        int guiRows = getValidGuiRows();
        Inventory inv = Bukkit.createInventory(null, guiRows * 9, "칭호 삭제 - " + target.getName());

        List<TitleData> titles = playerTitles.getOrDefault(targetId, new ArrayList<>());
        int maxSlots = guiRows * 9;
        int count = 0;
        for (TitleData title : titles) {
            if (title == null) continue;
            if (count++ >= maxSlots) break;
            ItemStack item = getConfigItem("Title_book_del", title.getDisplay());

            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "title_name"), PersistentDataType.STRING, title.getName());
            item.setItemMeta(meta);

            inv.addItem(item);
        }

        deleteTargets.put(admin.getUniqueId(), targetId);
        admin.openInventory(inv);
    }

    private int getValidGuiRows() {
        int guiRows = getConfig().getInt("gui_row", 3);
        if (guiRows < 1 || guiRows > 6) {
            guiRows = 3;
        }
        return guiRows;
    }

    private ItemStack getConfigItem(String configPath, String displayTitle) {
        String type = getConfig().getString(configPath + ".type", "BOOK");
        Material mat = Material.matchMaterial(type);
        if (mat == null) mat = Material.BOOK;
        String safeTitle = (displayTitle == null) ? "Unknown" : displayTitle;
        String name = getConfig().getString(configPath + ".name", "").replace("prefix", safeTitle);
        List<String> loreList = getConfig().getStringList(configPath + ".lore");
        if (loreList.isEmpty()) {
            String lore = getConfig().getString(configPath + ".lore", "").replace("prefix", safeTitle);
            loreList = Arrays.asList(lore);
        } else {
            loreList = loreList.stream()
                    .map(lore -> lore.replace("prefix", safeTitle))
                    .collect(Collectors.toList());
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // 이름 색코드 변환 (String만!)
        if (name.contains("&") || name.contains("§")) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        } else {
            meta.setDisplayName(LegacyComponentSerializer.legacySection()
                    .serialize(MiniMessage.miniMessage().deserialize(name)));
        }

        // 로어 색코드 변환 (String만!)
        List<String> loreColored = new ArrayList<>();
        for (String line : loreList) {
            if (line.contains("&") || line.contains("§")) {
                loreColored.add(ChatColor.translateAlternateColorCodes('&', line));
            } else {
                loreColored.add(LegacyComponentSerializer.legacySection()
                        .serialize(MiniMessage.miniMessage().deserialize(line)));
            }
        }
        meta.setLore(loreColored);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack updateConfigItemWithLore(ItemStack item, String configPath) {
        ItemMeta meta = item.getItemMeta();
        List<String> loreList = getConfig().getStringList(configPath + ".lore");
        if (loreList.isEmpty()) {
            String lore = getConfig().getString(configPath + ".lore", "");
            loreList = Arrays.asList(lore);
        }
        List<String> loreColored = new ArrayList<>();
        for (String line : loreList) {
            if (line.contains("&") || line.contains("§")) {
                loreColored.add(ChatColor.translateAlternateColorCodes('&', line));
            } else {
                loreColored.add(LegacyComponentSerializer.legacySection()
                        .serialize(MiniMessage.miniMessage().deserialize(line)));
            }
        }
        meta.setLore(loreColored);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        TitleData activeTitle = activeTitles.get(playerId);

        if (pendingEffects.containsKey(playerId)) {
            event.setCancelled(true);
            String message = event.getMessage();

            if (message.equals("-")) {
                pendingEffects.remove(playerId);
                pendingEffectLevels.remove(playerId);
                player.sendMessage(Component.text( TITLE_PREFIX + "효과 설정이 취소되었습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            try {
                int level = Integer.parseInt(message);
                if (level < 1 || level > 255) {
                    player.sendMessage(Component.text(TITLE_PREFIX + "잘못된 값입니다. 1~255 사이의 숫자를 입력하세요.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }

                ItemStack book = player.getInventory().getItemInMainHand();
                if (book == null || !book.hasItemMeta()) {
                    player.sendMessage(Component.text(TITLE_PREFIX + "칭호북을 들고 있어야 합니다!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }

                PotionEffectType effectType = pendingEffects.get(playerId);

                ItemMeta meta = book.getItemMeta();
                PersistentDataContainer data = meta.getPersistentDataContainer();
                data.set(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING, effectType.getName());
                data.set(new NamespacedKey(this, LEVEL_METADATA), PersistentDataType.INTEGER, level);
                book.setItemMeta(meta);

                player.sendMessage(Component.text(TITLE_PREFIX + "칭호북에 효과 [" + effectType.getName() + "] (레벨 " + level + ")이 저장되었습니다.").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                pendingEffects.remove(playerId);
                pendingEffectLevels.remove(playerId);

            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(TITLE_PREFIX + "숫자를 입력해야 합니다. 다시 입력해주세요.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            }
            return;
        }

        // 채팅에 칭호 적용
        if (activeTitle != null && !activeTitle.getDisplay().isEmpty()) {
            String display = activeTitle.getDisplay();
            String titleColored;
            if (display.contains("&") || display.contains("§")) {
                titleColored = ChatColor.translateAlternateColorCodes('&', display);
            } else {
                // MiniMessage를 legacy 색코드로 변환하여 String으로 사용
                titleColored = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(MiniMessage.miniMessage().deserialize(display));
            }
            event.setFormat(titleColored + " "+ ChatColor.WHITE + player.getName() + " : " + event.getMessage());
        } else {
            event.setFormat(" " + ChatColor.WHITE + player.getName() + " : " + event.getMessage());
        }
    }

    private void applyEffectFromTitle(Player player, ItemStack book) {
        if (book == null || !book.hasItemMeta()) return;

        PersistentDataContainer data = book.getItemMeta().getPersistentDataContainer();

        if (!data.has(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING)) {
            player.sendMessage(Component.text("이 칭호북에는 효과가 저장되어 있지 않습니다!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        String effectName = data.get(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING);
        Integer level = data.get(new NamespacedKey(this, LEVEL_METADATA), PersistentDataType.INTEGER);

        if (effectName == null || level == null) {
            player.sendMessage(Component.text("칭호북의 효과 데이터가 손상되었습니다!").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        PotionEffectType effectType = PotionEffectType.getByName(effectName);

        if (effectType == null) {
            player.sendMessage(Component.text("유효하지 않은 효과: " + effectName).color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        player.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, level - 1));
        player.sendMessage(Component.text(TITLE_PREFIX + "칭호 효과 [" + effectName + "] (레벨 " + level + ") 이 적용되었습니다!").color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Bukkit.getLogger().info("Inventory 클릭 이벤트 호출됨: " + event.getView().getTitle());
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String inventoryTitle = event.getView().getTitle();
        if (inventoryTitle.contains("님의 칭호 현황")) {
            event.setCancelled(true);
            UUID playerId = player.getUniqueId();
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String clickedTitleName = data.get(new NamespacedKey(this, "title_name"), PersistentDataType.STRING);
            if (clickedTitleName == null) {
                player.sendMessage(Component.text("[ Error ] 칭호 정보를 찾을 수 없습니다.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            TitleData clicked = playerTitles.getOrDefault(playerId, new ArrayList<>()).stream()
                    .filter(td -> td.getName().equals(clickedTitleName))
                    .findFirst().orElse(null);

            if (clicked == null) {
                player.sendMessage(Component.text("[ Error ] 칭호 데이터를 찾을 수 없습니다.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            boolean isEquipped = clicked.equals(activeTitles.get(playerId));

            if (event.isShiftClick() && isEquipped) {
                if (clicked.getType() == TitleData.Type.GRADIENT) {
                    unequipGradientTitle(player, clicked);
                } else {
                    unequipNormalTitle(player, clicked);
                }
            } else {
                if (clicked.getType() == TitleData.Type.GRADIENT) {
                    equipGradientTitle(player, clicked, item);
                } else {
                    equipNormalTitle(player, clicked);
                }
            }

            player.closeInventory();
            openTitleGUI(player);

        } else if (inventoryTitle.startsWith("칭호 삭제 - ")) {
//            event.setCancelled(true);
//            String display = item.getItemMeta().getDisplayName();
//            UUID adminId = player.getUniqueId();
//            UUID targetId = deleteTargets.get(adminId);
//
//            if (targetId != null) {
//                List<TitleData> titles = playerTitles.get(targetId);
//                TitleData toRemove = titles.stream()
//                        .filter(td -> {
//                            String expectedDisplay;
//                            if (td.getType() == TitleData.Type.GRADIENT) {
//                                expectedDisplay = td.getDisplay();
//                            } else {
//                                expectedDisplay = ChatColor.translateAlternateColorCodes('&', td.getDisplay());
//                            }
//                            return display.equals(expectedDisplay);
//                        })
//                        .findFirst().orElse(null);
//
//                if (titles != null && toRemove != null && titles.remove(toRemove)) {
//                    Player target = Bukkit.getPlayer(targetId);
//                    if (target != null) {
//                        if (toRemove.equals(activeTitles.get(targetId))) {
//                            if (toRemove.getType() == TitleData.Type.GRADIENT) {
//                                unequipGradientTitle(target, toRemove);
//                            } else {
//                                unequipNormalTitle(target, toRemove);
//                            }
//                        }
//                        String prefix = toRemove.getType() == TitleData.Type.GRADIENT
//                                ? toRemove.getDisplay()
//                                : MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(toRemove.getDisplay()));
//
//                        String rawMsg = messageConfig.getString("delete_Title_message", "player님의 칭호가 삭제되었습니다: prefix")
//                                .replace("prefix", prefix)
//                                .replace("player", target.getName());
//                        target.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg));
//                    }
//                    player.closeInventory();
//                } else {
//                    player.sendMessage(Component.text("[ Error ] 칭호를 삭제할 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
//                }
//            } else {
//                player.sendMessage(Component.text("[ Error ] 플레이어를 찾을 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
//            }
            handleTitleDelete(event, player, item);
        }
    }
    private void handleTitleDelete(InventoryClickEvent event, Player player, ItemStack item) {
        event.setCancelled(true);
        UUID adminId = player.getUniqueId();
        UUID targetId = deleteTargets.get(adminId);

        if (targetId != null) {
            List<TitleData> titles = playerTitles.get(targetId);
            if (titles == null) {
                player.sendMessage(Component.text("[ Error ] 대상 플레이어의 칭호 목록을 찾을 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            String clickedName = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(this, "title_name"), PersistentDataType.STRING);

            if (clickedName == null) {
                player.sendMessage(Component.text("[ Error ] 칭호 이름 데이터를 찾을 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            TitleData toRemove = titles.stream()
                    .filter(td -> td.getName().equals(clickedName))
                    .findFirst().orElse(null);

            if (toRemove != null && titles.remove(toRemove)) {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null) {
                    if (toRemove.equals(activeTitles.get(targetId))) {
                        if (toRemove.getType() == TitleData.Type.GRADIENT) {
                            unequipGradientTitle(target, toRemove);
                        } else {
                            unequipNormalTitle(target, toRemove);
                        }
                    }

                    if (toRemove.getType() == TitleData.Type.GRADIENT) {
                        sendDeleteGradientTitleMessage(player, target, toRemove);
                    } else {
                        sendDeleteNormalTitleMessage(player, target, toRemove);
                    }
                }
                player.closeInventory();
            } else {
                player.sendMessage(Component.text("[ Error ] 칭호를 삭제할 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        } else {
            player.sendMessage(Component.text("[ Error ] 플레이어를 찾을 수 없습니다.").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void sendDeleteNormalTitleMessage(Player admin, Player target, TitleData title) {
        String prefix = title.getDisplay();
        String rawMsg = messageConfig.getString("delete_Title_message", "&c{player}&f님의 칭호가 삭제되었습니다: {prefix}")
                .replace("{player}", target.getName())
                .replace("{prefix}", prefix);

        String miniMsg = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg)
        );

        String msgWithPrefix = TITLE_PREFIX + miniMsg;

        String coloredMsg = ChatColor.translateAlternateColorCodes('&', rawMsg);
        playSound(target, "title_delete_sound", "ENTITY_ITEM_BREAK");
        target.sendMessage(MiniMessage.miniMessage().deserialize(msgWithPrefix));
    }

    private void sendDeleteGradientTitleMessage(Player admin, Player target, TitleData title) {
        String prefix = title.getDisplay();
        String rawMsg = messageConfig.getString("delete_Gradient_Title_message", "<red>{player}<white>님의 칭호가 삭제되었습니다: {prefix}")
                .replace("{prefix}", prefix)
                .replace("{player}", target.getName());
        rawMsg = addTitlePrefix(rawMsg);
        playSound(target, "title_delete_sound", "ENTITY_ITEM_BREAK");
        target.sendMessage(MiniMessage.miniMessage().deserialize(rawMsg));
    }


    private void unequipNormalTitle(Player player, TitleData title) {
        activeTitles.remove(player.getUniqueId());
        removeAllPotionEffects(player);

        String raw = messageConfig.getString("unequip_Title_message", "&c칭호가 해제되었습니다: {prefix}");

        String msgMini = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
        );
        String parsedPrefix = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title.getDisplay())
        );
        String finalMsg = msgMini.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_unselect_sound", "ENTITY_VILLAGER_NO");
        updatePlayerDisplayName(player);
    }

    private void unequipGradientTitle(Player player, TitleData title) {
        activeTitles.remove(player.getUniqueId());
        removeAllPotionEffects(player);

        String raw = messageConfig.getString("unequip_Title_message", "칭호가 해제되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
        );
        String finalMsg = msgMini.replace("{prefix}", title.getDisplay());
        finalMsg = addTitlePrefix(finalMsg);
        player.sendMessage(MiniMessage.miniMessage().deserialize(finalMsg));

        playSound(player, "title_unselect_sound", "ENTITY_VILLAGER_NO");
        updatePlayerDisplayName(player);
    }

    private void removeAllPotionEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }


    private void equipNormalTitle(Player player, TitleData title) {
        activeTitles.put(player.getUniqueId(), title);
        applyEffectFromTitle(player, null); // 일반 칭호는 item 메타 없어도 됨

        // & → MiniMessage 변환
        String raw = messageConfig.getString("equip_Title_message", "&e칭호가 장착되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
        );
        String parsedPrefix = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(title.getDisplay())
        );
        String finalMsg = msgMini.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_select_sound", "ENTITY_VILLAGER_YES");
        updatePlayerDisplayName(player);
    }

    private void equipGradientTitle(Player player, TitleData title, ItemStack item) {
        activeTitles.put(player.getUniqueId(), title);
        applyEffectFromTitle(player, item); // item에 따른 효과 있음

        String raw = messageConfig.getString("equip_Title_message", "칭호가 장착되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
        );
        String finalMsg = msgMini.replace("{prefix}", title.getDisplay());
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_select_sound", "ENTITY_VILLAGER_YES");
        updatePlayerDisplayName(player);
    }

    private void applyEffect(Player player, PotionEffectType effectType, int level) {
        player.addPotionEffect(new PotionEffect(effectType, Integer.MAX_VALUE, level));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        TitleData title = activeTitles.get(playerId);

        if (!playerTitles.containsKey(playerId)) {
            playerTitles.put(playerId, new ArrayList<>());
        }

        String storedNickname = titlesConfig.getString(playerId.toString() + ".nickname", "");
        String currentNickname = player.getName();
        if (!storedNickname.equals(currentNickname)) {
            titlesConfig.set(playerId.toString() + ".nickname", currentNickname);
            saveTitles();
            getLogger().info("[S-Title] 플레이어의 닉네임이 업데이트되었습니다: " + storedNickname + " -> " + currentNickname);
        }
        updatePlayerDisplayName(player);

        if (title != null && title.getType() == TitleData.Type.NORMAL) {
            PotionEffectType effect = titleEffects.get(title.getName());
            if (effect != null) {
                applyEffect(player, effect, 0);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        resetPlayerDisplayName(player);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void updatePlayerDisplayName(Player player) {
        UUID playerId = player.getUniqueId();
        TitleData activeTitle = activeTitles.get(playerId);

        if (activeTitle != null && !activeTitle.getDisplay().isEmpty()) {
            String display = activeTitle.getDisplay();
            Component titleComp;

            if (display.contains("<") && display.contains(">")) {
                // MiniMessage 형식인 경우
                titleComp = MiniMessage.miniMessage().deserialize(display);
            } else if (display.contains("&") || display.contains("§")) {
                // Legacy 색코드인 경우
                titleComp = LegacyComponentSerializer.legacySection()
                        .deserialize(ChatColor.translateAlternateColorCodes('&', display));
            } else {
                titleComp = Component.text(display);
            }

            Component fullName = titleComp.append(Component.text(" " + player.getName()));
            player.displayName(fullName);
            player.playerListName(fullName);
        } else {
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
        }
    }

//    private void updatePlayerDisplayName(Player player) {
//        UUID playerId = player.getUniqueId();
//        TitleData activeTitle = activeTitles.get(playerId);
//
//        if (activeTitle != null && !activeTitle.getDisplay().isEmpty()) {
//            String display = activeTitle.getDisplay();
//            Component titleComp = MiniMessage.miniMessage().deserialize(display);
//            Component fullName = titleComp.append(Component.text(" " + player.getName()));
//            player.displayName(fullName);
//            player.playerListName(fullName);
//        } else {
//            player.displayName(Component.text(player.getName()));
//            player.playerListName(Component.text(player.getName()));
//        }
//    }

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
        }.runTaskTimer(this, 0L, 20L);
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
                completions.add("효과설정");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("삭제")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("제작")) {
                completions.add("[원하는 칭호]");
                completions.add("gradient");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("제작") && args[1].equalsIgnoreCase("gradient")) {
                completions.add("<HEX시작색>");
            } else if (args.length == 4 && args[0].equalsIgnoreCase("제작") && args[1].equalsIgnoreCase("gradient")) {
                completions.add("<HEX끝색>");
            } else if (args.length == 5 && args[0].equalsIgnoreCase("제작") && args[1].equalsIgnoreCase("gradient")) {
                completions.add("<텍스트>");
            }

            return completions;
        }
        return null;
    }

    private void playSound(Player player, String soundKey, String defaultSound) {
        if (soundConfig.getBoolean("sound_switch." + soundKey, true)) {
            String soundName = soundConfig.getString(soundKey, defaultSound);
            Sound sound = null;

            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException | NullPointerException e) {
                getLogger().warning("[S-Title] 잘못된 사운드 이름: " + soundName + ". 기본값(" + defaultSound + ")을 사용합니다.");
                try {
                    sound = Sound.valueOf(defaultSound);
                } catch (IllegalArgumentException ex) {
                    getLogger().severe("[S-Title] 기본값으로 설정된 사운드도 유효하지 않습니다: " + defaultSound);
                    return;
                }
            }

            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }
}