package topList;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

record HologramOptions(
        int visibilityDistance,
        Display.Billboard billboard,
        TextDisplay.TextAlignment textAlignment,
        boolean textShadow,
        boolean seeThrough,
        boolean doubleSided
) {
}
