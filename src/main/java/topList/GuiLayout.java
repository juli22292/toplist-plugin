package topList;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class GuiLayout {

    private static final int COLUMNS = 9;

    private GuiLayout() {
    }

    static void fill(Inventory inventory, ItemStack borderItem, ItemStack innerItem) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, isBorder(slot, inventory.getSize()) ? borderItem : innerItem);
        }
    }

    private static boolean isBorder(int slot, int size) {
        int rows = size / COLUMNS;
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;
        return row == 0 || row == rows - 1 || column == 0 || column == COLUMNS - 1;
    }
}
