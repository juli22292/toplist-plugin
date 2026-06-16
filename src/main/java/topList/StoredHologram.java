package topList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

final class StoredHologram {

    private String name;
    private String fancyName;
    private String templateName;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    StoredHologram() {
    }

    private StoredHologram(String name, String fancyName, String templateName, String world, double x, double y, double z, float yaw, float pitch) {
        this.name = name;
        this.fancyName = fancyName;
        this.templateName = templateName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    static StoredHologram fromLocation(String name, String fancyName, Location location) {
        return fromLocation(name, fancyName, null, location, false);
    }

    static StoredHologram fromLocation(String name, String fancyName, Location location, boolean useCurrentPitch) {
        return fromLocation(name, fancyName, null, location, useCurrentPitch);
    }

    static StoredHologram fromLocation(String name, String fancyName, String templateName, Location location) {
        return fromLocation(name, fancyName, templateName, location, false);
    }

    static StoredHologram fromLocation(String name, String fancyName, String templateName, Location location, boolean useCurrentPitch) {
        return new StoredHologram(
                name,
                fancyName,
                templateName,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                useCurrentPitch ? location.getPitch() : 0.0F
        );
    }

    String name() {
        return name;
    }

    String fancyName() {
        return fancyName;
    }

    String templateName() {
        if (templateName == null || templateName.isBlank()) {
            return name;
        }
        return templateName;
    }

    String world() {
        return world;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    float yaw() {
        return yaw;
    }

    float pitch() {
        return pitch;
    }

    boolean hasRequiredData() {
        return name != null && !name.isBlank() && world != null && !world.isBlank();
    }

    void ensureFancyName(String prefix) {
        if (fancyName == null || fancyName.isBlank()) {
            fancyName = prefix + name;
        }
    }

    Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    void setLocation(Location location) {
        setLocation(location, true);
    }

    void setLocation(Location location, boolean useCurrentPitch) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = useCurrentPitch ? location.getPitch() : 0.0F;
    }
}
