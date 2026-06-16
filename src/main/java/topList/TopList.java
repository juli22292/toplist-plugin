package topList;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class TopList extends JavaPlugin {

    private Economy economy;
    private TopListManager topListManager;
    private TutorialHologramService tutorialHologramService;
    private TutorialHologramService freeHologramService;
    private CollectibleSignService collectibleSignService;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("databaseconfig.yml", false);
        saveResource("tutorialconfig.yml", false);
        saveResource("hologramconfig.yml", false);
        saveResource("signconfig.yml", false);
        saveResource("updates.yml", false);

        if (!setupEconomy()) {
            getLogger().severe("Vault is installed, but no Economy provider is registered.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!FancyHologramsPlugin.isEnabled()) {
            getLogger().severe("FancyHolograms is not enabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!getServer().getPluginManager().isPluginEnabled("Essentials")) {
            getLogger().warning("EssentialsX is not enabled. Playtime still uses the same Bukkit statistic as EssentialsX /playtime.");
        }

        topListManager = new TopListManager(this, economy);
        topListManager.load();
        tutorialHologramService = new TutorialHologramService(this);
        tutorialHologramService.load();
        freeHologramService = new TutorialHologramService(this, "hologramconfig.yml", "freiehologramme", "free_", "free");
        freeHologramService.load();
        collectibleSignService = new CollectibleSignService(this);
        collectibleSignService.load();
        updateChecker = new UpdateChecker(this);
        updateChecker.load();

        TopListManagementGui managementGui = new TopListManagementGui(this, topListManager);
        TutorialManagementGui tutorialManagementGui = new TutorialManagementGui(tutorialHologramService);
        TutorialManagementGui freeHologramGui = new TutorialManagementGui(
                freeHologramService,
                "&8Freie Hologramme",
                org.bukkit.Material.END_CRYSTAL,
                "&d&lFreie Hologramme",
                java.util.List.of("&7Erstelle normale Text-Hologramme", "&7aus der &fhologramconfig.yml&7."),
                "hologramconfig.yml",
                "Hologramm",
                "Freies Hologramm",
                "Hologramm-Menü"
        );
        SignManagementGui signManagementGui = new SignManagementGui(collectibleSignService);
        ManagementHubGui managementHubGui = new ManagementHubGui(topListManager, tutorialHologramService, freeHologramService, managementGui, tutorialManagementGui, freeHologramGui, collectibleSignService, signManagementGui);
        LAdvancementGui lAdvancementGui = new LAdvancementGui(collectibleSignService);
        managementGui.setHubGui(managementHubGui);
        tutorialManagementGui.setHubGui(managementHubGui);
        freeHologramGui.setHubGui(managementHubGui);
        signManagementGui.setHubGui(managementHubGui);
        getServer().getPluginManager().registerEvents(managementGui, this);
        getServer().getPluginManager().registerEvents(tutorialManagementGui, this);
        getServer().getPluginManager().registerEvents(freeHologramGui, this);
        getServer().getPluginManager().registerEvents(signManagementGui, this);
        getServer().getPluginManager().registerEvents(managementHubGui, this);
        getServer().getPluginManager().registerEvents(collectibleSignService, this);
        getServer().getPluginManager().registerEvents(lAdvancementGui, this);
        getServer().getPluginManager().registerEvents(updateChecker, this);

        PluginCommand command = getCommand("topliste");
        if (command == null) {
            getLogger().severe("Command /topliste is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        TopListCommand topListCommand = new TopListCommand(topListManager, managementGui, tutorialHologramService, tutorialManagementGui, freeHologramService, freeHologramGui, managementHubGui, collectibleSignService, signManagementGui, updateChecker);
        command.setExecutor(topListCommand);
        command.setTabCompleter(topListCommand);

        PluginCommand lAdvancementCommand = getCommand("ladvancement");
        if (lAdvancementCommand == null) {
            getLogger().severe("Command /ladvancement is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        lAdvancementCommand.setExecutor(new LAdvancementCommand(collectibleSignService, lAdvancementGui));

        updateChecker.start();
    }

    @Override
    public void onDisable() {
        if (topListManager != null) {
            topListManager.shutdown();
        }
        if (tutorialHologramService != null) {
            tutorialHologramService.shutdown();
        }
        if (freeHologramService != null) {
            freeHologramService.shutdown();
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }

        economy = registration.getProvider();
        return economy != null;
    }
}
