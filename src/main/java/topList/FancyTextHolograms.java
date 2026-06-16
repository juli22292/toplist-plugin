package topList;

import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

final class FancyTextHolograms {

    private static final String BACKSIDE_SUFFIX = "__backside";

    private FancyTextHolograms() {
    }

    static List<Hologram> spawn(HologramManager manager, String fancyName, Location location, HologramOptions options, List<String> lines) {
        removeIfPresent(manager, fancyName);
        removeIfPresent(manager, backsideName(fancyName));

        List<String> coloredLines = LegacyColor.colorize(lines);
        List<Hologram> holograms = new ArrayList<>();
        holograms.add(create(manager, fancyName, location.clone(), options, coloredLines));

        if (options.doubleSided()) {
            Location backsideLocation = location.clone();
            backsideLocation.setYaw(oppositeYaw(backsideLocation.getYaw()));
            holograms.add(create(manager, backsideName(fancyName), backsideLocation, options, coloredLines));
        }

        return List.copyOf(holograms);
    }

    static void update(List<Hologram> holograms, List<String> lines) {
        List<String> coloredLines = LegacyColor.colorize(lines);
        for (Hologram hologram : holograms) {
            if (hologram.getData() instanceof TextHologramData textData) {
                textData.setText(new ArrayList<>(coloredLines));
                hologram.forceUpdate();
            }
        }
    }

    static String backsideName(String fancyName) {
        return fancyName + BACKSIDE_SUFFIX;
    }

    private static Hologram create(HologramManager manager, String fancyName, Location location, HologramOptions options, List<String> coloredLines) {
        TextHologramData data = new TextHologramData(fancyName, location);
        data.setPersistent(false);
        data.setVisibilityDistance(options.visibilityDistance());
        data.setBillboard(options.billboard());
        data.setTextAlignment(options.textAlignment());
        data.setTextShadow(options.textShadow());
        data.setSeeThrough(options.seeThrough());
        data.setText(new ArrayList<>(coloredLines));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
        return hologram;
    }

    private static void removeIfPresent(HologramManager manager, String fancyName) {
        manager.getHologram(fancyName).ifPresent(manager::removeHologram);
    }

    private static float oppositeYaw(float yaw) {
        float opposite = yaw + 180.0F;
        while (opposite > 180.0F) {
            opposite -= 360.0F;
        }
        while (opposite <= -180.0F) {
            opposite += 360.0F;
        }
        return opposite;
    }
}
