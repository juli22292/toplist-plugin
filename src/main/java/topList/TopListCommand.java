package topList;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TopListCommand implements CommandExecutor, TabCompleter {

    private static final LegacyComponentSerializer LEGACY_COLORS = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> ACTIONS = List.of("reload", "create", "delete", "tpo", "tph", "test", "list");
    private static final List<String> TUTORIAL_ACTIONS = List.of("create", "spawn", "reload");
    private static final List<String> SIGN_ACTIONS = List.of("give", "reload", "list");
    private static final List<String> MANAGEMENT_TARGETS = List.of("topliste", "tutorial", "hologram", "sign", "settings");
    private static final String PERMISSION_ADMIN = "topliste.admin";
    private static final String PERMISSION_WILDCARD = "topliste.*";
    private static final String PERMISSION_MANAGEMENT = "topliste.management";

    private final TopListManager manager;
    private final TopListManagementGui managementGui;
    private final TutorialHologramService tutorialService;
    private final TutorialManagementGui tutorialManagementGui;
    private final TutorialHologramService freeHologramService;
    private final TutorialManagementGui freeHologramGui;
    private final ManagementHubGui managementHubGui;
    private final CollectibleSignService signService;
    private final SignManagementGui signManagementGui;

    TopListCommand(
            TopListManager manager,
            TopListManagementGui managementGui,
            TutorialHologramService tutorialService,
            TutorialManagementGui tutorialManagementGui,
            TutorialHologramService freeHologramService,
            TutorialManagementGui freeHologramGui,
            ManagementHubGui managementHubGui,
            CollectibleSignService signService,
            SignManagementGui signManagementGui
    ) {
        this.manager = manager;
        this.managementGui = managementGui;
        this.tutorialService = tutorialService;
        this.tutorialManagementGui = tutorialManagementGui;
        this.freeHologramService = freeHologramService;
        this.freeHologramGui = freeHologramGui;
        this.managementHubGui = managementHubGui;
        this.signService = signService;
        this.signManagementGui = signManagementGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && "management".equalsIgnoreCase(args[0])) {
            openManagement(sender, args);
            return true;
        }

        if (args.length >= 1 && "tutorial".equalsIgnoreCase(args[0])) {
            handleTutorial(sender, args);
            return true;
        }

        if (args.length >= 1 && isFreeHologramArgument(args[0])) {
            handleFreeHologram(sender, args);
            return true;
        }

        if (args.length >= 1 && isSignArgument(args[0])) {
            handleSign(sender, args);
            return true;
        }

        if (args.length < 2) {
            send(sender, settings().message("usage"));
            return true;
        }

        TopListType type = TopListType.fromArgument(args[0]);
        if (type == null) {
            send(sender, settings().message("invalid-type"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            send(sender, settings().message("usage"));
            return true;
        }

        if (!hasPermission(sender, type, action)) {
            send(sender, settings().message("no-permission"));
            return true;
        }

        HologramService service = manager.service(type);
        switch (action) {
            case "reload" -> reload(sender, type);
            case "create" -> create(sender, service, args);
            case "delete" -> delete(sender, service, args);
            case "tpo" -> teleportToHologram(sender, service, args);
            case "tph" -> teleportHologramHere(sender, service, args);
            case "test" -> test(sender, service, args);
            case "list" -> list(sender, service);
            default -> send(sender, settings().message("usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(typeSuggestions(sender), args[0]);
        }

        if ("management".equalsIgnoreCase(args[0])) {
            if (args.length == 2 && hasManagementPermission(sender)) {
                return partial(MANAGEMENT_TARGETS, args[1]);
            }
            return List.of();
        }

        if ("tutorial".equalsIgnoreCase(args[0])) {
            return tutorialSuggestions(sender, args);
        }

        if (isFreeHologramArgument(args[0])) {
            return freeHologramSuggestions(sender, args);
        }

        if (isSignArgument(args[0])) {
            return signSuggestions(sender, args);
        }

        TopListType type = TopListType.fromArgument(args[0]);
        if (type == null) {
            return List.of();
        }

        if (args.length == 2) {
            return partial(actionSuggestions(sender, type), args[1]);
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && List.of("delete", "tpo", "tph", "test").contains(action) && hasPermission(sender, type, action)) {
            return partial(hologramNames(manager.service(type)), args[2]);
        }

        return List.of();
    }

    private void openManagement(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (!hasManagementPermission(sender)) {
            send(sender, settings().message("no-permission"));
            return;
        }

        if (args.length < 2) {
            managementHubGui.open(player, true);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "topliste" -> managementGui.openMain(player);
            case "tutorial" -> tutorialManagementGui.openMain(player);
            case "hologram", "hologramme" -> freeHologramGui.openMain(player);
            case "sign", "schild", "schilder" -> signManagementGui.openMain(player);
            case "settings", "einstellungen" -> managementHubGui.openSettings(player);
            default -> send(sender, settings().message("management-usage"));
        }
    }

    private void handleTutorial(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, tutorialSettings().message("usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!TUTORIAL_ACTIONS.contains(action)) {
            send(sender, tutorialSettings().message("usage"));
            return;
        }
        if (!hasTutorialPermission(sender, action)) {
            send(sender, settings().message("no-permission"));
            return;
        }

        switch (action) {
            case "create" -> tutorialCreate(sender, args);
            case "spawn" -> tutorialSpawn(sender, args);
            case "reload" -> tutorialReload(sender, args);
            default -> send(sender, tutorialSettings().message("usage"));
        }
    }

    private void tutorialCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (args.length < 3) {
            send(sender, tutorialSettings().message("create-usage"));
            return;
        }

        String templateName = args[2];
        String instanceName = args.length >= 4 ? args[3] : tutorialService.nextInstanceName(templateName);
        TutorialActionResult result = tutorialService.create(templateName, instanceName, player.getLocation().clone());
        sendTutorialResult(sender, result, instanceName, templateName, "created");
    }

    private void tutorialSpawn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, tutorialSettings().message("spawn-usage"));
            return;
        }

        String name = args[2];
        Location fallbackLocation = sender instanceof Player player ? player.getLocation().clone() : null;
        if (args.length >= 4) {
            String instanceName = args[3];
            sendTutorialResult(sender, tutorialService.spawn(name, instanceName, fallbackLocation), instanceName, name, "spawned");
            return;
        }

        if (tutorialService.hologram(name) == null && tutorialService.template(name) != null && fallbackLocation != null) {
            String instanceName = tutorialService.nextInstanceName(name);
            sendTutorialResult(sender, tutorialService.create(name, instanceName, fallbackLocation), instanceName, name, "spawned");
            return;
        }

        sendTutorialResult(sender, tutorialService.spawn(name, fallbackLocation), name, "", "spawned");
    }

    private void tutorialReload(CommandSender sender, String[] args) {
        if (args.length < 3 || "all".equalsIgnoreCase(args[2])) {
            tutorialService.reload();
            send(sender, tutorialSettings().message("reloaded"));
            return;
        }

        String name = args[2];
        sendTutorialResult(sender, tutorialService.reload(name), name, "", "reloaded-one");
    }

    private void sendTutorialResult(CommandSender sender, TutorialActionResult result, String name, String templateName, String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case INVALID_NAME -> "invalid-name";
            case TEMPLATE_NOT_FOUND -> "template-not-found";
            case ALREADY_EXISTS -> "already-exists";
            case NOT_FOUND -> "not-found";
        };
        String messageName = result == TutorialActionResult.TEMPLATE_NOT_FOUND && !templateName.isBlank() ? templateName : name;
        send(sender, tutorialSettings().message(key, Map.of("name", messageName, "template", templateName)));
    }

    private void handleFreeHologram(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, freeHologramSettings().message("usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!TUTORIAL_ACTIONS.contains(action)) {
            send(sender, freeHologramSettings().message("usage"));
            return;
        }
        if (!hasFreeHologramPermission(sender, action)) {
            send(sender, settings().message("no-permission"));
            return;
        }

        switch (action) {
            case "create" -> freeHologramCreate(sender, args);
            case "spawn" -> freeHologramSpawn(sender, args);
            case "reload" -> freeHologramReload(sender, args);
            default -> send(sender, freeHologramSettings().message("usage"));
        }
    }

    private void freeHologramCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (args.length < 3) {
            send(sender, freeHologramSettings().message("create-usage"));
            return;
        }

        String templateName = args[2];
        String instanceName = args.length >= 4 ? args[3] : freeHologramService.nextInstanceName(templateName);
        TutorialActionResult result = freeHologramService.create(templateName, instanceName, player.getLocation().clone());
        sendFreeHologramResult(sender, result, instanceName, templateName, "created");
    }

    private void freeHologramSpawn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, freeHologramSettings().message("spawn-usage"));
            return;
        }

        String name = args[2];
        Location fallbackLocation = sender instanceof Player player ? player.getLocation().clone() : null;
        if (args.length >= 4) {
            String instanceName = args[3];
            sendFreeHologramResult(sender, freeHologramService.spawn(name, instanceName, fallbackLocation), instanceName, name, "spawned");
            return;
        }

        if (freeHologramService.hologram(name) == null && freeHologramService.template(name) != null && fallbackLocation != null) {
            String instanceName = freeHologramService.nextInstanceName(name);
            sendFreeHologramResult(sender, freeHologramService.create(name, instanceName, fallbackLocation), instanceName, name, "spawned");
            return;
        }

        sendFreeHologramResult(sender, freeHologramService.spawn(name, fallbackLocation), name, "", "spawned");
    }

    private void freeHologramReload(CommandSender sender, String[] args) {
        if (args.length < 3 || "all".equalsIgnoreCase(args[2])) {
            freeHologramService.reload();
            send(sender, freeHologramSettings().message("reloaded"));
            return;
        }

        String name = args[2];
        sendFreeHologramResult(sender, freeHologramService.reload(name), name, "", "reloaded-one");
    }

    private void sendFreeHologramResult(CommandSender sender, TutorialActionResult result, String name, String templateName, String successKey) {
        String key = switch (result) {
            case SUCCESS -> successKey;
            case INVALID_NAME -> "invalid-name";
            case TEMPLATE_NOT_FOUND -> "template-not-found";
            case ALREADY_EXISTS -> "already-exists";
            case NOT_FOUND -> "not-found";
        };
        String messageName = result == TutorialActionResult.TEMPLATE_NOT_FOUND && !templateName.isBlank() ? templateName : name;
        send(sender, freeHologramSettings().message(key, Map.of("name", messageName, "template", templateName)));
    }

    private void handleSign(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, signSettings().message("usage"));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (!SIGN_ACTIONS.contains(action)) {
            send(sender, signSettings().message("usage"));
            return;
        }
        if (!hasSignPermission(sender, action)) {
            send(sender, settings().message("no-permission"));
            return;
        }

        switch (action) {
            case "give" -> signGive(sender, args);
            case "reload" -> signReload(sender);
            case "list" -> signList(sender);
            default -> send(sender, signSettings().message("usage"));
        }
    }

    private void signGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, signSettings().message("give-usage"));
            return;
        }

        String signName = args[2];
        CollectibleSignTemplate template = signService.template(signName);
        if (template == null) {
            send(sender, signSettings().message("template-not-found", Map.of("name", signName)));
            return;
        }

        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                send(sender, signSettings().message("target-not-found", Map.of("player", args[3])));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            send(sender, signSettings().message("give-usage"));
            return;
        }

        int amount = args.length >= 5 ? parseAmount(args[4]) : 1;
        if (amount < 1) {
            send(sender, signSettings().message("invalid-amount"));
            return;
        }

        ItemStack item = signService.itemFor(template.name(), amount);
        if (item == null) {
            send(sender, signSettings().message("template-not-found", Map.of("name", signName)));
            return;
        }

        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        send(sender, signSettings().message("given", Map.of(
                "name", template.displayName(),
                "player", target.getName(),
                "amount", String.valueOf(amount)
        )));
        if (!target.equals(sender)) {
            send(target, signSettings().message("received", Map.of(
                    "name", template.displayName(),
                    "amount", String.valueOf(amount)
            )));
        }
    }

    private void signReload(CommandSender sender) {
        signService.reload();
        send(sender, signSettings().message("reloaded"));
    }

    private void signList(CommandSender sender) {
        Collection<CollectibleSignTemplate> templates = signService.templates();
        if (templates.isEmpty()) {
            send(sender, signSettings().message("list-empty"));
            return;
        }

        send(sender, signSettings().message("list-header"));
        for (CollectibleSignTemplate template : templates) {
            send(sender, signSettings().message("list-entry", Map.of(
                    "id", template.name(),
                    "name", template.displayName(),
                    "amount", template.rewardAmount()
            )));
        }
    }

    private void reload(CommandSender sender, TopListType type) {
        manager.reload();
        send(sender, settings().message("reloaded", replacements(type)));
    }

    private void create(CommandSender sender, HologramService service, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (args.length < 3) {
            send(sender, settings().message("create-usage", replacements(service.type())));
            return;
        }

        String name = args[2];
        if (!HologramStore.isValidName(name)) {
            send(sender, settings().message("invalid-name"));
            return;
        }
        if (service.isKnown(name)) {
            send(sender, settings().message("already-exists", replacements(service.type(), name)));
            return;
        }

        service.create(name, player.getLocation().clone());
        send(sender, settings().message("created", replacements(service.type(), name)));
    }

    private void delete(CommandSender sender, HologramService service, String[] args) {
        if (args.length < 3) {
            send(sender, settings().message("delete-usage", replacements(service.type())));
            return;
        }

        String name = args[2];
        if (!service.delete(name)) {
            send(sender, settings().message("not-found", replacements(service.type(), name)));
            return;
        }

        send(sender, settings().message("deleted", replacements(service.type(), name)));
    }

    private void teleportToHologram(CommandSender sender, HologramService service, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (args.length < 3) {
            send(sender, settings().message("tpo-usage", replacements(service.type())));
            return;
        }

        StoredHologram hologram = service.hologram(args[2]);
        Location location = hologram == null ? null : hologram.toLocation();
        if (location == null) {
            send(sender, settings().message("not-found", replacements(service.type(), args[2])));
            return;
        }

        player.teleport(location);
        send(sender, settings().message("teleported-to-hologram", replacements(service.type(), hologram.name())));
    }

    private void teleportHologramHere(CommandSender sender, HologramService service, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, settings().message("player-only"));
            return;
        }
        if (args.length < 3) {
            send(sender, settings().message("tph-usage", replacements(service.type())));
            return;
        }

        String name = args[2];
        if (!service.moveHere(name, player.getLocation().clone())) {
            send(sender, settings().message("not-found", replacements(service.type(), name)));
            return;
        }

        send(sender, settings().message("hologram-teleported-here", replacements(service.type(), name)));
    }

    private void test(CommandSender sender, HologramService service, String[] args) {
        if (args.length >= 3) {
            String name = args[2];
            if (!service.refresh(name)) {
                send(sender, settings().message("not-found", replacements(service.type(), name)));
                return;
            }
            send(sender, settings().message("refreshed", replacements(service.type(), name)));
        } else {
            service.refreshAll();
        }

        send(sender, settings().message("test-header", replacements(service.type())));
        for (String line : service.previewLines()) {
            send(sender, line);
        }
    }

    private void list(CommandSender sender, HologramService service) {
        Collection<StoredHologram> holograms = service.holograms();
        if (holograms.isEmpty()) {
            send(sender, settings().message("list-empty", replacements(service.type())));
            return;
        }

        send(sender, settings().message("list-header", replacements(service.type())));
        for (StoredHologram hologram : holograms) {
            send(sender, settings().message("list-entry", replacements(service.type(), hologram.name(), Map.of(
                    "world", hologram.world(),
                    "x", formatCoordinate(hologram.x()),
                    "y", formatCoordinate(hologram.y()),
                    "z", formatCoordinate(hologram.z()),
                    "yaw", formatCoordinate(hologram.yaw()),
                    "pitch", formatCoordinate(hologram.pitch())
            ))));
        }
    }

    private List<String> typeSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();
        if (hasManagementPermission(sender)) {
            suggestions.add("management");
        }
        if (hasAnyTutorialPermission(sender)) {
            suggestions.add("tutorial");
        }
        if (hasAnyFreeHologramPermission(sender)) {
            suggestions.add("hologram");
        }
        if (hasAnySignPermission(sender)) {
            suggestions.add("sign");
        }
        for (TopListType type : TopListType.values()) {
            if (hasAnyTypePermission(sender, type)) {
                suggestions.add(type.argument());
            }
        }
        return suggestions;
    }

    private List<String> actionSuggestions(CommandSender sender, TopListType type) {
        List<String> suggestions = new ArrayList<>();
        for (String action : ACTIONS) {
            if (hasPermission(sender, type, action)) {
                suggestions.add(action);
            }
        }
        return suggestions;
    }

    private List<String> tutorialSuggestions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String action : TUTORIAL_ACTIONS) {
                if (hasTutorialPermission(sender, action)) {
                    suggestions.add(action);
                }
            }
            return partial(suggestions, args[1]);
        }

        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (!TUTORIAL_ACTIONS.contains(action) || !hasTutorialPermission(sender, action)) {
                return List.of();
            }

            if ("create".equals(action)) {
                return partial(templateNames(), args[2]);
            }

            List<String> names = new ArrayList<>();
            if ("reload".equals(action)) {
                names.add("all");
            }
            names.addAll(tutorialNames());
            return partial(names, args[2]);
        }

        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (!List.of("create", "spawn").contains(action) || !hasTutorialPermission(sender, action)) {
                return List.of();
            }
            if (tutorialService.template(args[2]) == null) {
                return List.of();
            }
            return partial(List.of(tutorialService.nextInstanceName(args[2])), args[3]);
        }

        return List.of();
    }

    private List<String> hologramNames(HologramService service) {
        List<String> names = new ArrayList<>();
        for (StoredHologram hologram : service.holograms()) {
            names.add(hologram.name());
        }
        return names;
    }

    private List<String> tutorialNames() {
        List<String> names = new ArrayList<>();
        names.addAll(templateNames());
        for (StoredHologram hologram : tutorialService.holograms()) {
            if (!names.contains(hologram.name())) {
                names.add(hologram.name());
            }
        }
        return names;
    }

    private List<String> templateNames() {
        return templateNames(tutorialService);
    }

    private List<String> freeHologramSuggestions(CommandSender sender, String[] args) {
        return staticHologramSuggestions(sender, args, freeHologramService, this::hasFreeHologramPermission);
    }

    private List<String> signSuggestions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String action : SIGN_ACTIONS) {
                if (hasSignPermission(sender, action)) {
                    suggestions.add(action);
                }
            }
            return partial(suggestions, args[1]);
        }

        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 3 && "give".equals(action) && hasSignPermission(sender, action)) {
            return partial(signService.templateNames(), args[2]);
        }
        if (args.length == 4 && "give".equals(action) && hasSignPermission(sender, action)) {
            return partial(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 5 && "give".equals(action) && hasSignPermission(sender, action)) {
            return partial(List.of("1", "8", "16", "32", "64"), args[4]);
        }

        return List.of();
    }

    private List<String> staticHologramSuggestions(
            CommandSender sender,
            String[] args,
            TutorialHologramService service,
            java.util.function.BiPredicate<CommandSender, String> permission
    ) {
        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>();
            for (String action : TUTORIAL_ACTIONS) {
                if (permission.test(sender, action)) {
                    suggestions.add(action);
                }
            }
            return partial(suggestions, args[1]);
        }

        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (!TUTORIAL_ACTIONS.contains(action) || !permission.test(sender, action)) {
                return List.of();
            }

            if ("create".equals(action)) {
                return partial(templateNames(service), args[2]);
            }

            List<String> names = new ArrayList<>();
            if ("reload".equals(action)) {
                names.add("all");
            }
            names.addAll(staticHologramNames(service));
            return partial(names, args[2]);
        }

        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (!List.of("create", "spawn").contains(action) || !permission.test(sender, action)) {
                return List.of();
            }
            if (service.template(args[2]) == null) {
                return List.of();
            }
            return partial(List.of(service.nextInstanceName(args[2])), args[3]);
        }

        return List.of();
    }

    private List<String> staticHologramNames(TutorialHologramService service) {
        List<String> names = new ArrayList<>();
        names.addAll(templateNames(service));
        for (StoredHologram hologram : service.holograms()) {
            if (!names.contains(hologram.name())) {
                names.add(hologram.name());
            }
        }
        return names;
    }

    private List<String> templateNames(TutorialHologramService service) {
        List<String> names = new ArrayList<>();
        for (TutorialTemplate template : service.templates()) {
            names.add(template.name());
        }
        return names;
    }

    private List<String> partial(Collection<String> values, String token) {
        String lowerToken = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerToken))
                .toList();
    }

    private boolean hasAnyTypePermission(CommandSender sender, TopListType type) {
        for (String action : ACTIONS) {
            if (hasPermission(sender, type, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyTutorialPermission(CommandSender sender) {
        for (String action : TUTORIAL_ACTIONS) {
            if (hasTutorialPermission(sender, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyFreeHologramPermission(CommandSender sender) {
        for (String action : TUTORIAL_ACTIONS) {
            if (hasFreeHologramPermission(sender, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySignPermission(CommandSender sender) {
        for (String action : SIGN_ACTIONS) {
            if (hasSignPermission(sender, action)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPermission(CommandSender sender, TopListType type, String action) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(PERMISSION_WILDCARD)
                || sender.hasPermission("topliste." + type.argument() + ".*")
                || sender.hasPermission("topliste." + type.argument() + "." + action);
    }

    private boolean hasManagementPermission(CommandSender sender) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(PERMISSION_WILDCARD)
                || sender.hasPermission(PERMISSION_MANAGEMENT);
    }

    private boolean hasTutorialPermission(CommandSender sender, String action) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(PERMISSION_WILDCARD)
                || sender.hasPermission("topliste.tutorial.*")
                || sender.hasPermission("topliste.tutorial." + action)
                || sender.hasPermission("toplist.tutorial.*")
                || sender.hasPermission("toplist.tutorial." + action);
    }

    private boolean hasFreeHologramPermission(CommandSender sender, String action) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(PERMISSION_WILDCARD)
                || sender.hasPermission("topliste.hologram.*")
                || sender.hasPermission("topliste.hologram." + action)
                || sender.hasPermission("toplist.hologram.*")
                || sender.hasPermission("toplist.hologram." + action);
    }

    private boolean hasSignPermission(CommandSender sender, String action) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || sender.hasPermission(PERMISSION_WILDCARD)
                || sender.hasPermission("topliste.sign.*")
                || sender.hasPermission("topliste.sign." + action)
                || sender.hasPermission("toplist.sign.*")
                || sender.hasPermission("toplist.sign." + action);
    }

    private boolean isFreeHologramArgument(String argument) {
        return "hologram".equalsIgnoreCase(argument) || "hologramme".equalsIgnoreCase(argument);
    }

    private boolean isSignArgument(String argument) {
        return "sign".equalsIgnoreCase(argument) || "schild".equalsIgnoreCase(argument) || "schilder".equalsIgnoreCase(argument);
    }

    private TopListSettings settings() {
        return manager.settings();
    }

    private TutorialSettings tutorialSettings() {
        return tutorialService.settings();
    }

    private TutorialSettings freeHologramSettings() {
        return freeHologramService.settings();
    }

    private CollectibleSignSettings signSettings() {
        return signService.settings();
    }

    private Map<String, String> replacements(TopListType type) {
        return replacements(type, "");
    }

    private Map<String, String> replacements(TopListType type, String name) {
        return replacements(type, name, Map.of());
    }

    private Map<String, String> replacements(TopListType type, String name, Map<String, String> extra) {
        Map<String, String> replacements = new java.util.HashMap<>(extra);
        replacements.put("type", type.argument());
        replacements.put("typ", type.displayName());
        replacements.put("name", name);
        return replacements;
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private int parseAmount(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > 64) {
                return -1;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(LEGACY_COLORS.deserialize(message));
    }
}
