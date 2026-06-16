package topList;

import org.bukkit.Material;

import java.util.List;

record TutorialTemplate(
        String name,
        String displayName,
        Material icon,
        List<String> lines
) {
}
