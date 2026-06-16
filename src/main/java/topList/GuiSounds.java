package topList;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

final class GuiSounds {

    private GuiSounds() {
    }

    static void open(Player player) {
        play(player, Sound.BLOCK_CHEST_OPEN, 0.45F, 1.25F);
    }

    static void close(Player player) {
        play(player, Sound.BLOCK_CHEST_CLOSE, 0.45F, 1.0F);
    }

    static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.45F, 1.35F);
    }

    static void page(Player player) {
        play(player, Sound.ITEM_BOOK_PAGE_TURN, 0.55F, 1.15F);
    }

    static void success(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45F, 1.25F);
    }

    static void error(Player player) {
        play(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.55F, 0.75F);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
