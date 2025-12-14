package com.star.titleplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    // === 카탈로그(템플릿) 저장소 ===
    private final Map<String, TitleTemplate> templates = new LinkedHashMap<>(); // id -> template
    private File templatesFile;
    private FileConfiguration templatesConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // 선택 옵션
        getConfig().addDefault("allow_duplicate_gradient_per_user", false);
        getConfig().addDefault("catalog_gui_row", 6);
        getConfig().options().copyDefaults(true);
        saveConfig();

        loadSoundConfig();
        loadMessageConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("칭호")).setExecutor(this);
        Objects.requireNonNull(getCommand("칭호")).setTabCompleter(this);
        loadTitles();
        loadTemplates();
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
        saveTemplates();
        getLogger().info(ChatColor.GOLD + "[ S - Title ]" + ChatColor.WHITE + "플러그인이 비활성화 되었습니다.");
    }

    private String addTitlePrefix(String message) {
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
                List<Map<?, ?>> titleSection = titlesConfig.getMapList(key + ".titles"); // 칭호 리스트 로드
                List<TitleData> titles = new ArrayList<>();

                if (titleSection == null) {
                    titleSection = new ArrayList<>(); // null일 경우 빈 리스트로 초기화
                }

                for (Map<?, ?> map : titleSection) {
                    String name = (String) map.get("name");
                    String display = (String) map.get("display");
                    String typeStr = (String) map.get("type");
                    String templateId = (String) map.getOrDefault("templateId", null);
                    String acquiredAtStr = (String) map.getOrDefault("acquiredAt", null); // 획득 시간 로드

                    TitleData.Type type = TitleData.Type.valueOf(typeStr);
                    LocalDateTime acquiredAt = acquiredAtStr != null ? LocalDateTime.parse(acquiredAtStr) : LocalDateTime.now();

                    titles.add(new TitleData(name, display, type, templateId, acquiredAt)); // 획득 시간 포함한 TitleData 객체 생성
                }

                Map<?, ?> activeMap = titlesConfig.getConfigurationSection(key + ".activeTitle") != null
                        ? titlesConfig.getConfigurationSection(key + ".activeTitle").getValues(false)
                        : null;

                TitleData activeTitle = null;
                if (activeMap != null) {
                    String name = (String) activeMap.get("name");
                    String display = (String) activeMap.get("display");
                    String typeStr = (String) activeMap.get("type");
                    String templateId = (String) activeMap.getOrDefault("templateId", null);
                    String acquiredAtStr = (String) activeMap.getOrDefault("acquiredAt", null);

                    TitleData.Type type = TitleData.Type.valueOf(typeStr);
                    LocalDateTime acquiredAt = acquiredAtStr != null ? LocalDateTime.parse(acquiredAtStr) : LocalDateTime.now();

                    activeTitle = new TitleData(name, display, type, templateId, acquiredAt);
                }

                playerTitles.put(playerId, titles); // 플레이어의 칭호 데이터 저장
                if (activeTitle != null) activeTitles.put(playerId, activeTitle);
            } catch (IllegalArgumentException e) {
                getLogger().warning("[S-Title] 잘못된 UUID: " + key);
            }
        }
    }

    private void saveTitles() {
        for (UUID playerId : playerTitles.keySet()) {
            List<Map<String, Object>> titlesList = new ArrayList<>();
            for (TitleData title : playerTitles.get(playerId)) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", title.getName());
                map.put("display", title.getDisplay());
                map.put("type", title.getType().name());
                if (title.getTemplateId() != null) map.put("templateId", title.getTemplateId());
                map.put("acquiredAt", title.getAcquiredAt().toString()); // 획득 시간 저장
                titlesList.add(map);
            }
            titlesConfig.set(playerId.toString() + ".titles", titlesList);

            TitleData active = activeTitles.get(playerId);
            if (active != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", active.getName());
                map.put("display", active.getDisplay());
                map.put("type", active.getType().name());
                if (active.getTemplateId() != null) map.put("templateId", active.getTemplateId());
                map.put("acquiredAt", active.getAcquiredAt().toString()); // 획득 시간 저장
                titlesConfig.set(playerId.toString() + ".activeTitle", map);
            } else {
                titlesConfig.set(playerId.toString() + ".activeTitle", null);
            }
            String nickname = Bukkit.getOfflinePlayer(playerId).getName();
            titlesConfig.set(playerId.toString() + ".nickname", nickname != null ? nickname : "UnknownPlayer");
        }
        try {
            titlesConfig.save(titlesFile); // 파일 저장
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // === 템플릿 로드/저장 ===
    private void loadTemplates() {
        templatesFile = new File(getDataFolder(), "templates.yml");
        if (!templatesFile.exists()) {
            try { templatesFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        templates.clear();

        List<Map<?, ?>> list = templatesConfig.getMapList("templates");
        if (list != null) {
            for (Map<?, ?> m : list) {
                String id = String.valueOf(m.get("id"));
                String name = String.valueOf(m.get("name"));
                String display = String.valueOf(m.get("display"));
                String typeStr = String.valueOf(m.get("type"));
                if (id == null || id.equals("null")) continue;
                try {
                    TitleData.Type type = TitleData.Type.valueOf(typeStr);
                    TitleTemplate tpl = new TitleTemplate(id, name, display, type);
                    templates.put(id, tpl);
                } catch (Exception ignored) {}
            }
        }
    }

    private void saveTemplates() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TitleTemplate tpl : templates.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", tpl.getId());
            m.put("name", tpl.getName());
            m.put("display", tpl.getDisplay());
            m.put("type", tpl.getType().name());
            list.add(m);
        }
        templatesConfig.set("templates", list);
        try { templatesConfig.save(templatesFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // 동일 정의 템플릿 검색
    private TitleTemplate findExistingTemplate(TitleData data) {
        for (TitleTemplate t : templates.values()) {
            if (Objects.equals(t.getName(), data.getName())
                    && Objects.equals(t.getDisplay(), data.getDisplay())
                    && t.getType() == data.getType()) {
                return t;
            }
        }
        return null;
    }

    // 자동 ID 사용(기존)
    private TitleTemplate registerTemplateIfNew(TitleData data) {
        TitleTemplate existing = findExistingTemplate(data);
        if (existing != null) return existing;
        TitleTemplate tpl = TitleTemplate.fromTitleData(data);
        templates.put(tpl.getId(), tpl);
        saveTemplates();
        return tpl;
    }

    // 사용자 지정 ID 사용(마지막 인자로 받은 id)
    private TitleTemplate registerTemplateWithId(TitleData data, String desiredId) throws IllegalArgumentException {
        if (desiredId == null || desiredId.isBlank()) {
            return registerTemplateIfNew(data);
        }

        TitleTemplate byId = templates.get(desiredId);
        if (byId != null) {
            // 같은 정의면 재사용, 다르면 충돌 에러
            if (Objects.equals(byId.getName(), data.getName())
                    && Objects.equals(byId.getDisplay(), data.getDisplay())
                    && byId.getType() == data.getType()) {
                return byId;
            }
            throw new IllegalArgumentException("이미 존재하는 ID입니다: " + desiredId);
        }

        // 동일 정의 템플릿이 다른 ID로 이미 있으면 그걸 재사용
        TitleTemplate existingSame = findExistingTemplate(data);
        if (existingSame != null) return existingSame;

        // 신규 생성
        TitleTemplate tpl = TitleTemplate.fromTitleDataWithId(data, desiredId);
        templates.put(desiredId, tpl);
        saveTemplates();
        return tpl;
    }

    private TitleTemplate findTemplateByKey(String key) {
        if (templates.containsKey(key)) return templates.get(key); // ID 우선
        List<TitleTemplate> byName = templates.values().stream()
                .filter(t -> t.getName().equalsIgnoreCase(key))
                .collect(Collectors.toList());
        if (!byName.isEmpty()) return byName.get(0);
        return null;
    }

    private boolean matchesTemplate(TitleData d, TitleTemplate t) {
        if (d.getTemplateId() != null) return d.getTemplateId().equals(t.getId());
        return d.getName().equalsIgnoreCase(t.getName())
                && d.getType() == t.getType()
                && Objects.equals(d.getDisplay(), t.getDisplay());
    }

    public TitleData getActiveTitle(UUID playerId) {
        return activeTitles.get(playerId);
    }

    private void loadSoundConfig() {
        soundFile = new File(getDataFolder(), "sound.yml");
        if (!soundFile.exists()) { saveResource("sound.yml", false); }
        soundConfig = YamlConfiguration.loadConfiguration(soundFile);
    }

    private void loadMessageConfig() {
        messageFile = new File(getDataFolder(), "message.yml");
        if (!messageFile.exists()) { saveResource("message.yml", false); }
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MiniMessage mm = MiniMessage.miniMessage();
        Component header = mm.deserialize("<White>===========</White> "+ TITLE_PREFIX +"<White>===========</White>");

        if (!(sender instanceof Player)) {
            // 콘솔: 지급만 허용
            if (args.length > 0 && args[0].equalsIgnoreCase("지급")) {
                return handleGiveCommand(sender, args, true);
            }
            sender.sendMessage("[S-Title] 콘솔에서는 /칭호 지급 만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(header);
            player.sendMessage(mm.deserialize("<aqua>/칭호 열기</aqua> <gray>- 자신의 보유 칭호 GUI를 엽니다. 클릭 장착, 쉬프트 클릭 해제.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 제작 <white><텍스트...></white> [<yellow>id</yellow>]</aqua> <gray>- 칭호를 카탈로그(templates.yml)에 저장합니다. 마지막 인자를 넣으면 해당 값을 ID로 사용합니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 제작 gradient <white><HEX시작색> <HEX끝색> <텍스트...></white> [<yellow>id</yellow>]</aqua> <gray>- 그라데이션 칭호를 저장합니다. 예) /칭호 제작 gradient #ff1101 #123456 [ 죽음 ] 죽음</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 지급 <white>[칭호명 또는 ID]</white> <gray>[플레이어(선택)]</gray></aqua> <gray>- 책으로 지급합니다. 책을 우클릭하면 칭호가 등록됩니다. 플레이어를 생략하면 자기 자신에게 지급.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 목록</aqua> <gray>- 만들어진 모든 칭호(카탈로그) GUI를 엽니다. 로어에 <yellow>보유자/장착자</yellow>가 표시됩니다. <yellow>쉬프트 클릭</yellow> 시 카탈로그에서 완전 삭제되고, 보유/장착 중인 플레이어에게서도 해제/삭제됩니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 삭제 <white>[플레이어 닉네임]</white></aqua> <gray>- 해당 플레이어의 보유 목록에서만 삭제합니다. 카탈로그에는 영향을 주지 않습니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 효과설정</aqua> <gray>- 손에 든 칭호북에 효과를 저장합니다. 이후 그 북으로 장착 시 효과가 적용됩니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 리로드</aqua> <gray>- config.yml, message.yml, sound.yml을 리로드합니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 초기화 <white>[플레이어 닉네임]</white></aqua> <gray>- 특정 플레이어의 모든 칭호를 초기화합니다.</gray>"));
            player.sendMessage(mm.deserialize("<aqua>/칭호 전체초기화</aqua> <gray>- 모든 플레이어의 모든 칭호를 초기화합니다.</gray>"));
            player.sendMessage(mm.deserialize("<white>=================================</white>"));
            return true;
        }

        // /칭호 초기화 [플레이어 닉네임]
        if (args[0].equalsIgnoreCase("초기화")) {
            if (!player.hasPermission("titleplugin.reset")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.</red>"));
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "사용법: /칭호 초기화 [플레이어 닉네임]");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다: " + args[1]);
                return true;
            }

            UUID targetId = target.getUniqueId();
            playerTitles.remove(targetId);
            activeTitles.remove(targetId);
            updatePlayerDisplayName(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + "의 모든 칭호가 초기화되었습니다.");
            saveTitles();
            return true;
        }

        // /칭호 전체초기화
        if (args[0].equalsIgnoreCase("전체초기화")) {
            if (!player.hasPermission("titleplugin.resetall")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.</red>"));
                return true;
            }
            playerTitles.clear();
            activeTitles.clear();

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                updatePlayerDisplayName(onlinePlayer);
            }

            sender.sendMessage(ChatColor.GOLD + "모든 플레이어의 칭호가 초기화되었습니다.");
            saveTitles();
            return true;
        }

        // /칭호 열기
        if(!player.isOp() && !player.hasPermission("titleplugin.use")) {
            player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 플러그인을 사용할 권한이 없습니다.</red>")); return true;
        }
        if (args[0].equalsIgnoreCase("열기")) {
            if (!player.hasPermission("titleplugin.open")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.</red>"));
                return true;
            }
            openTitleGUI(player); return true;
        }

        // /칭호 제작: 마지막 인자를 ID로 허용
        if (args[0].equalsIgnoreCase("제작")) {
            if (!player.hasPermission("titleplugin.make")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.")); return true;
            }
            // gradient
            if (args.length >= 5 && args[1].equalsIgnoreCase("gradient")) {
                String startHex = args[2];
                String endHex = args[3];

                String desiredId = null;
                String text;

                if (args.length >= 6) {
                    // 마지막 토큰을 id로, 그 전까지를 텍스트로
                    desiredId = args[args.length - 1];
                    text = String.join(" ", Arrays.copyOfRange(args, 4, args.length - 1));
                } else {
                    text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                }

                String mmText = "<!i><gradient:" + startHex + ":" + endHex + ">" + text + "</gradient>";
                TitleData data = new TitleData(text, mmText, TitleData.Type.GRADIENT);

                try {
                    TitleTemplate tpl = registerTemplateWithId(data, desiredId);
                    player.sendMessage(mm.deserialize(TITLE_PREFIX + "<green>그라데이션 칭호가 저장되었습니다.</green> <gray>(ID: <yellow>"+ tpl.getId() +"</yellow>)</gray>"));
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>" + ex.getMessage() + "</red>"));
                }
                return true;
            }

            // normal
            if (args.length >= 2) {
                String desiredId = null;
                String text;
                if (args.length >= 3) {
                    desiredId = args[args.length - 1];
                    text = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                } else {
                    text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                }

                String display = "&f" + text;
                TitleData data = new TitleData(text, display, TitleData.Type.NORMAL);

                try {
                    TitleTemplate tpl = registerTemplateWithId(data, desiredId);
                    player.sendMessage(mm.deserialize(TITLE_PREFIX + "<green>칭호가 저장되었습니다.</green> <gray>(ID: <yellow>"+ tpl.getId() +"</yellow>)</gray>"));
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>" + ex.getMessage() + "</red>"));
                }
                return true;
            }

            player.sendMessage(Component.text("사용법: /칭호 제작 <텍스트...> [id] 또는 /칭호 제작 gradient <HEX시작색> <HEX끝색> <텍스트...> [id]"));
            return true;
        }

        // /칭호 지급 [칭호명 or ID] [플레이어?] -> 책 지급
        if (args[0].equalsIgnoreCase("지급")) {
            if (!player.hasPermission("titleplugin.give")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.</red>"));
                return true;
            }
            return handleGiveCommand(sender, args, false);
        }

        // /칭호 목록
        if (args[0].equalsIgnoreCase("목록")) {
            if (!player.hasPermission("titleplugin.list")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.</red>"));
                return true;
            }
            openTemplateCatalogGUI(player); return true;
        }

        // /칭호 삭제 [닉] (플레이어 보유 목록에서만 삭제)
        if (args[0].equalsIgnoreCase("삭제")) {
            if (!player.hasPermission("titleplugin.delete")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.")); return true;
            }
            if (args.length < 2) {
                player.sendMessage(Component.text("사용법: /칭호 삭제 [플레이어닉네임]")); return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) { openTitleDeleteGUI(player, target); }
            else { player.sendMessage(Component.text("<red>플레이어를 찾을 수 없습니다.")); }
            return true;
        }

        // /칭호 리로드
        if (args[0].equalsIgnoreCase("리로드")) {
            if (!player.hasPermission("titleplugin.reload")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.")); return true;
            }
            reloadConfig(); loadSoundConfig(); loadMessageConfig();
            player.sendMessage(mm.deserialize(TITLE_PREFIX + "설정, 출력 메세지, 사운드 파일이 리로드되었습니다.")); return true;
        }

        // /칭호 효과설정
        if (args[0].equalsIgnoreCase("효과설정")) {
            if (!player.hasPermission("titleplugin.effect")) {
                player.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>이 명령어를 사용할 권한이 없습니다.")); return true;
            }
            if (!isHoldingTitleBook(player)) {
                player.sendMessage(Component.text("칭호북을 들고 있어야 합니다!").color(net.kyori.adventure.text.format.NamedTextColor.RED)); return true;
            }
            openEffectGUI(player); return true;
        }

        if (!player.isOp()) { openTitleGUI(player); }
        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args, boolean console) {
        MiniMessage mm = MiniMessage.miniMessage();
        if (args.length < 2) {
            sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<gray>사용법: /칭호 지급 [칭호명 또는 ID] [플레이어(선택)]</gray>"));
            return true;
        }
        String titleKey = args[1];
        TitleTemplate tpl = findTemplateByKey(titleKey);
        if (tpl == null) {
            sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>해당 칭호를 카탈로그에서 찾을 수 없습니다.</red>"));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>플레이어를 찾을 수 없습니다.</red>"));
                return true;
            }
        } else {
            if (console) {
                sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>콘솔에서는 플레이어 닉네임을 반드시 지정해야 합니다.</red>"));
                return true;
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<red>대상 플레이어를 지정해주세요.</red>"));
                return true;
            }
        }

        // 책 지급
        TitleData data = new TitleData(tpl.getName(), tpl.getDisplay(), tpl.getType(), tpl.getId());
        giveTitleBookItemWithTemplate(target, data, tpl.getId());

        if (!(sender instanceof Player) || ((Player) sender).getUniqueId() != target.getUniqueId()) {
            sender.sendMessage(mm.deserialize(TITLE_PREFIX + "<green>칭호북 지급 완료:</green> <yellow>" + tpl.getName() + "</yellow> -> <aqua>" + target.getName() + "</aqua>"));
        }
        target.sendMessage(mm.deserialize(TITLE_PREFIX + "<green>칭호북을 받았습니다.</green> 우클릭하면 칭호가 등록됩니다."));
        playSound(target, "title_get_sound", "ENTITY_PLAYER_LEVELUP");
        return true;
    }

    // --- 카탈로그 GUI ---
    private void openTemplateCatalogGUI(Player player) {
        int guiRows = Math.min(6, Math.max(1, getConfig().getInt("catalog_gui_row", 6)));
        Inventory inv = Bukkit.createInventory(null, guiRows * 9, "칭호 목록");

        for (TitleTemplate tpl : templates.values()) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();

            // 이름 표시
            if (tpl.getDisplay().contains("<") && tpl.getDisplay().contains(">")) {
                meta.displayName(MiniMessage.miniMessage().deserialize(tpl.getDisplay()));
            } else if (tpl.getDisplay().contains("&") || tpl.getDisplay().contains("§")) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', tpl.getDisplay()));
            } else {
                meta.setDisplayName(tpl.getDisplay());
            }

            // 로어: 보유자/장착자
            List<String> owners = new ArrayList<>();
            List<String> equipped = new ArrayList<>();
            for (Map.Entry<UUID, List<TitleData>> e : playerTitles.entrySet()) {
                UUID uid = e.getKey();
                String nick = Bukkit.getOfflinePlayer(uid).getName();
                if (nick == null) nick = uid.toString().substring(0, 8);

                boolean has = e.getValue().stream().anyMatch(td -> matchesTemplate(td, tpl));
                if (has) owners.add(nick);

                TitleData act = activeTitles.get(uid);
                if (act != null && matchesTemplate(act, tpl)) {
                    equipped.add(nick);
                }
            }
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "타입: " + tpl.getType().name());
            lore.add(ChatColor.DARK_GRAY + "ID: " + tpl.getId());
            lore.add(ChatColor.YELLOW + "보유자(" + owners.size() + "): " + (owners.isEmpty() ? "-" : String.join(", ", trimList(owners, 10))));
            lore.add(ChatColor.AQUA + "장착자(" + equipped.size() + "): " + (equipped.isEmpty() ? "-" : String.join(", ", trimList(equipped, 10))));
            meta.setLore(lore);

            // PDC에 템플릿 ID 저장
            meta.getPersistentDataContainer().set(new NamespacedKey(this, "template_id"), PersistentDataType.STRING, tpl.getId());
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    private List<String> trimList(List<String> list, int limit) {
        if (list.size() <= limit) return list;
        List<String> result = new ArrayList<>(list.subList(0, limit));
        result.add("…+" + (list.size() - limit));
        return result;
    }

    // --- 책 지급(템플릿 ID 포함) ---
    private void giveTitleBookItemWithTemplate(Player player, TitleData title, String templateId) {
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
        if (templateId != null) {
            data.set(new NamespacedKey(this, "template_id"), PersistentDataType.STRING, templateId);
        }

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

            ItemStack item;
            if (title.equals(activeTitle)) {
                item = getConfigItem("Title_book_Icon", title.getDisplay());
                item = updateConfigItemWithLore(item, "Title_book_Icon_select");
            } else {
                item = getConfigItem("Title_book_Icon", title.getDisplay());
            }

            ItemMeta meta = item.getItemMeta();

            // 기존 로어를 가져옵니다.
            List<String> lore = (meta.getLore() == null) ? new ArrayList<>() : new ArrayList<>(meta.getLore());

            // 추가된 로어 처리: 획득 시간 맨 아래에 추가
            lore.add(ChatColor.GRAY + "획득 시간: " + title.getAcquiredAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            meta.setLore(lore);

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(new NamespacedKey(this, "title_name"), PersistentDataType.STRING, title.getName());
            data.set(new NamespacedKey(this, "title_display"), PersistentDataType.STRING, title.getDisplay());
            data.set(new NamespacedKey(this, "title_type"), PersistentDataType.STRING, title.getType().name());
            if (title.getTemplateId() != null) {
                data.set(new NamespacedKey(this, "template_id"), PersistentDataType.STRING, title.getTemplateId());
            }
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

        for (Map.Entry<String, PotionEffectType> entry : titleEffects.entrySet()) {
            String effectName = entry.getKey();
            PotionEffectType effectType = entry.getValue();

            ItemStack item = new ItemStack(Material.POTION, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + effectName);
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "이 효과를 선택하면 칭호북에 적용됩니다."));
                meta.getPersistentDataContainer().set(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING, effectType.getName());
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
        String templateId = data.get(new NamespacedKey(this, "template_id"), PersistentDataType.STRING);
        if (name == null || display == null || typeStr == null) return;

        TitleData.Type type;
        try {
            type = TitleData.Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        // 삭제된 템플릿인지 확인
        if (templateId == null || !templates.containsKey(templateId)) {
            player.sendMessage(ChatColor.RED + "이 칭호는 등록할 수 없습니다. 삭제된 칭호입니다.");
            event.setCancelled(true);
            return;
        }

        TitleData title = new TitleData(name, display, type, templateId);

        UUID playerId = player.getUniqueId();
        List<TitleData> titles = playerTitles.computeIfAbsent(playerId, k -> new ArrayList<>());

        boolean allowDupGradient = getConfig().getBoolean("allow_duplicate_gradient_per_user", false);
        boolean alreadyOwned = titles.contains(title);

        if (alreadyOwned && !(allowDupGradient && title.getType() == TitleData.Type.GRADIENT)) {
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
        saveTitles();
    }

    private void sendNormalTitleObtainedMessage(Player player, TitleData title) {
        String rawMsg = messageConfig.getString("give_titleBook_Message", "&e칭호를 획득했습니다: {prefix}")
                .replace("{prefix}", title.getDisplay());

        String miniMsg = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg));
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
            String miniMsg = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(deserialized));
            String msgWithPrefix = TITLE_PREFIX + miniMsg;
            player.sendMessage(MiniMessage.miniMessage().deserialize(msgWithPrefix));
        }
    }

    private String escapeMiniMessage(String str) {
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
            if (title.getTemplateId() != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(this, "template_id"), PersistentDataType.STRING, title.getTemplateId());
            }
            item.setItemMeta(meta);

            inv.addItem(item);
        }

        deleteTargets.put(admin.getUniqueId(), targetId);
        admin.openInventory(inv);
    }

    private int getValidGuiRows() {
        int guiRows = getConfig().getInt("gui_row", 3);
        if (guiRows < 1 || guiRows > 6) { guiRows = 3; }
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
            loreList = loreList.stream().map(lore -> lore.replace("prefix", safeTitle)).collect(Collectors.toList());
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (name.contains("&") || name.contains("§")) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        } else {
            meta.setDisplayName(LegacyComponentSerializer.legacySection()
                    .serialize(MiniMessage.miniMessage().deserialize(name)));
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
                player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<red>효과 설정이 취소되었습니다."));
                return;
            }

            try {
                int level = Integer.parseInt(message);
                if (level < 1 || level > 255) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<red>잘못된 값입니다. 1~255 사이의 숫자를 입력하세요."));
                    return;
                }

                ItemStack book = player.getInventory().getItemInMainHand();
                if (book == null || !book.hasItemMeta()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<red>칭호북을 들고 있어야 합니다!"));
                    return;
                }

                PotionEffectType effectType = pendingEffects.get(playerId);

                ItemMeta meta = book.getItemMeta();
                PersistentDataContainer data = meta.getPersistentDataContainer();
                data.set(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING, effectType.getName());
                data.set(new NamespacedKey(this, LEVEL_METADATA), PersistentDataType.INTEGER, level);
                book.setItemMeta(meta);

                player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<green>칭호북에 효과 [" + effectType.getName() + "] (레벨 " + level + ")이 저장되었습니다."));
                pendingEffects.remove(playerId);
                pendingEffectLevels.remove(playerId);

            } catch (NumberFormatException e) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<red>숫자를 입력해야 합니다. 다시 입력해주세요."));
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
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (player == null || item == null || !item.hasItemMeta()) return;

        String inventoryTitle = event.getView().getTitle();
        if (inventoryTitle.contains("님의 칭호 현황")) {
            event.setCancelled(true);
            UUID playerId = player.getUniqueId();
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            String clickedTitleName = data.get(new NamespacedKey(this, "title_name"), PersistentDataType.STRING);
            String clickedTemplateId = data.get(new NamespacedKey(this, "template_id"), PersistentDataType.STRING);
            if (clickedTitleName == null && clickedTemplateId == null) {
                player.sendMessage(Component.text("[ Error ] 칭호 정보를 찾을 수 없습니다.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            TitleData clicked = playerTitles.getOrDefault(playerId, new ArrayList<>()).stream()
                    .filter(td -> (clickedTemplateId != null && clickedTemplateId.equals(td.getTemplateId()))
                            || (clickedTemplateId == null && td.getName().equals(clickedTitleName)))
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

        } else if (inventoryTitle.equals("칭호 목록")) {
            event.setCancelled(true);
            handleCatalogClick(event, player, item);

        } else if (inventoryTitle.startsWith("칭호 삭제 - ")) {
            handleTitleDelete(event, player, item);

        } else if (inventoryTitle.equals("칭호 효과 설정")) {
            event.setCancelled(true);

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand == null || !mainHand.hasItemMeta() ||
                    !mainHand.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(this, TITLE_BOOK_METADATA), PersistentDataType.STRING)) {
                player.sendMessage(ChatColor.RED + "칭호북을 들고 있어야 효과를 적용할 수 있습니다!");
                player.closeInventory();
                return;
            }

            ItemMeta meta = item.getItemMeta();
            String effectTypeName = meta.getPersistentDataContainer().get(new NamespacedKey(this, EFFECT_METADATA), PersistentDataType.STRING);

            if (effectTypeName == null) {
                player.sendMessage(ChatColor.RED + "효과 정보를 찾을 수 없습니다!");
                player.closeInventory();
                return;
            }

            pendingEffects.put(player.getUniqueId(), PotionEffectType.getByName(effectTypeName));
            player.closeInventory();
            player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<aqua>적용할 효과 레벨(1~255)을 채팅으로 입력하세요. '-' 입력시 취소"));
        }

    }

    private void handleCatalogClick(InventoryClickEvent event, Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String templateId = meta.getPersistentDataContainer().get(new NamespacedKey(this, "template_id"), PersistentDataType.STRING);
        if (templateId == null) return;

        TitleTemplate tpl = templates.get(templateId);
        if (tpl == null) return;

        // 쉬프트 클릭: 카탈로그에서 완전 삭제 + 모든 플레이어 보유/장착 해제
        if (event.isShiftClick()) {
            if (!player.hasPermission("titleplugin.delete") && !player.isOp()) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<red>권한이 없습니다.</red>"));
                return;
            }
            for (UUID uid : new ArrayList<>(playerTitles.keySet())) {
                List<TitleData> list = playerTitles.get(uid);
                if (list == null) continue;

                TitleData active = activeTitles.get(uid);
                if (active != null && matchesTemplate(active, tpl)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) {
                        if (active.getType() == TitleData.Type.GRADIENT) unequipGradientTitle(p, active);
                        else unequipNormalTitle(p, active);
                    }
                    activeTitles.remove(uid);
                }
                list.removeIf(td -> matchesTemplate(td, tpl));
            }
            templates.remove(templateId);
            saveTemplates(); saveTitles();

            player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + "<yellow>카탈로그에서 칭호가 삭제되었습니다.</yellow> : " + " <gray>(ID: " + tpl.getId() + ")</gray>"));
            player.closeInventory();
            openTemplateCatalogGUI(player);
            return;
        }

        // 일반 클릭: 정보 출력
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                TITLE_PREFIX + "<white>선택:</white> " + tpl.getDisplay() + " <gray>(ID: " + tpl.getId() + ")</gray>"
        ));
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

            String clickedTemplateId = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(this, "template_id"), PersistentDataType.STRING);
            String clickedName = item.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(this, "title_name"), PersistentDataType.STRING);

            TitleData toRemove = titles.stream()
                    .filter(td -> (clickedTemplateId != null && clickedTemplateId.equals(td.getTemplateId()))
                            || (clickedTemplateId == null && td.getName().equals(clickedName)))
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
                saveTitles();
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

        String miniMsg = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(rawMsg));
        String msgWithPrefix = TITLE_PREFIX + miniMsg;

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
        String msgMini = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        String parsedPrefix = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(title.getDisplay()));
        String finalMsg = msgMini.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_unselect_sound", "ENTITY_VILLAGER_NO");
        updatePlayerDisplayName(player);
    }

    private void unequipGradientTitle(Player player, TitleData title) {
        activeTitles.remove(player.getUniqueId());
        removeAllPotionEffects(player);

        String raw = messageConfig.getString("unequip_Title_message", "칭호가 해제되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
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
        applyEffectFromTitle(player, null);

        String raw = messageConfig.getString("equip_Title_message", "&e칭호가 장착되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        String parsedPrefix = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(title.getDisplay()));
        String finalMsg = msgMini.replace("{prefix}", parsedPrefix);
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_select_sound", "ENTITY_VILLAGER_YES");
        updatePlayerDisplayName(player);
    }

    private void equipGradientTitle(Player player, TitleData title, ItemStack item) {
        activeTitles.put(player.getUniqueId(), title);
        applyEffectFromTitle(player, item);

        String raw = messageConfig.getString("equip_Title_message", "칭호가 장착되었습니다: {prefix}");
        String msgMini = MiniMessage.miniMessage().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        String finalMsg = msgMini.replace("{prefix}", title.getDisplay());
        player.sendMessage(MiniMessage.miniMessage().deserialize(TITLE_PREFIX + finalMsg));

        playSound(player, "title_select_sound", "ENTITY_VILLAGER_YES");
        updatePlayerDisplayName(player);
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
                player.addPotionEffect(new PotionEffect(effect, Integer.MAX_VALUE, 0));
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
                titleComp = MiniMessage.miniMessage().deserialize(display);
            } else if (display.contains("&") || display.contains("§")) {
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
        if (sender instanceof Player player && !player.isOp()) {
            return Collections.emptyList();
        }

        if (command.getName().equalsIgnoreCase("칭호")) {
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                completions.add("열기");
                completions.add("제작");
                completions.add("지급");
                completions.add("목록");
                completions.add("삭제");
                completions.add("리로드");
                completions.add("효과설정");
                completions.add("전체초기화");
                completions.add("초기화");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("초기화")) {
                // 두 번째 인자에서 접속 중인 플레이어 목록 제공
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("삭제")) {
                completions = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("제작")) {
                if (args.length == 2) {
                    completions.add("[텍스트...]");
                    completions.add("gradient");
                } else if (args.length == 3 && args[1].equalsIgnoreCase("gradient")) {
                    completions.add("<HEX시작색>");
                } else if (args.length == 4 && args[1].equalsIgnoreCase("gradient")) {
                    completions.add("<HEX끝색>");
                } else if (args.length >= 5 && args[1].equalsIgnoreCase("gradient")) {
                    completions.add("<텍스트...>");
                }else if (args.length == 6 && !args[1].equalsIgnoreCase("gradient")) {
                    completions.add("[id]");
                } else if (args.length >= 3 && !args[1].equalsIgnoreCase("gradient")) {
                    completions.add("[id]");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("지급")) {
                completions.addAll(templates.values().stream().map(TitleTemplate::getName).distinct().limit(20).collect(Collectors.toList()));
                completions.addAll(templates.keySet().stream().limit(10).collect(Collectors.toList()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("지급")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
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