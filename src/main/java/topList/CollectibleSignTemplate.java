package topList;

import org.bukkit.Material;

import java.util.List;

record CollectibleSignTemplate(
        String name,
        String displayName,
        Material material,
        List<String> lines,
        List<String> guiLore,
        String rewardAmount
) {
}
