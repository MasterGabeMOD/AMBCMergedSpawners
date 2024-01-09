package server.alanbecker.net;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {

    private Map<Location, Integer> spawnerCountMap = new HashMap<>();
    private int mergeRadius;
    private int mergeDelay;
    private int countDisplayDuration;
    private boolean allowMobEgg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();


        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        mergeRadius = config.getInt("merge-radius", 1);
        mergeDelay = config.getInt("merge-delay", 20);
        countDisplayDuration = config.getInt("count-display-duration", 5) * 20;
        allowMobEgg = config.getBoolean("allow-mob-egg", true);
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.SPAWNER && event.getPlayer().hasPermission("abmc.spawnermerge")) {
            Location spawnerLocation = event.getBlockPlaced().getLocation();

            int nearbySpawnerCount = countNearbySpawners(spawnerLocation);

            if (nearbySpawnerCount > 1) {
                mergeSpawners(spawnerLocation, nearbySpawnerCount);
            }

            spawnerCountMap.put(spawnerLocation, nearbySpawnerCount);
        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.SPAWNER && event.getPlayer().hasPermission("abmc.spawnermerge")) {
            Location spawnerLocation = event.getBlock().getLocation();

            if (spawnerCountMap.containsKey(spawnerLocation)) {
                int spawnerCount = spawnerCountMap.get(spawnerLocation);

                ItemStack spawnerItem = new ItemStack(Material.SPAWNER, spawnerCount);
                event.getPlayer().getInventory().addItem(spawnerItem);

                spawnerCountMap.remove(spawnerLocation);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (allowMobEgg && event.getAction().toString().contains("RIGHT") && event.hasItem()) {
            ItemStack item = event.getItem();

            if (item.getType() == Material.SPAWN_EGG) {
                Player player = event.getPlayer();
                BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                if (meta != null && meta.getBlockState() instanceof CreatureSpawner) {
                    CreatureSpawner spawner = (CreatureSpawner) meta.getBlockState();
                    EntityType entityType = spawner.getSpawnedType();

                    // Change the spawner type
                    Location location = player.getTargetBlock(null, 5).getLocation();
                    if (location.getBlock().getType() == Material.SPAWNER) {
                        CreatureSpawner targetSpawner = (CreatureSpawner) location.getBlock().getState();
                        targetSpawner.setSpawnedType(entityType);
                        targetSpawner.setDelay(mergeDelay);
                        targetSpawner.update();
                    }
                }
            }
        }
    }

    private int countNearbySpawners(Location location) {
        int count = 0;

        for (int x = -mergeRadius; x <= mergeRadius; x++) {
            for (int y = -mergeRadius; y <= mergeRadius; y++) {
                for (int z = -mergeRadius; z <= mergeRadius; z++) {
                    Location checkLocation = location.clone().add(x, y, z);

                    if (checkLocation.getBlock().getType() == Material.SPAWNER &&
                            !checkLocation.equals(location)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private void mergeSpawners(Location baseLocation, int spawnerCount) {
        CreatureSpawner baseSpawner = (CreatureSpawner) baseLocation.getBlock().getState();
        EntityType entityType = baseSpawner.getSpawnedType();

        for (int i = 0; i < spawnerCount - 1; i++) {
            baseLocation.getWorld().getBlockAt(baseLocation).setType(Material.AIR);
        }

        baseSpawner.setSpawnedType(entityType);
        baseSpawner.setDelay(mergeDelay);
        baseSpawner.update();

        for (int i = 0; i < spawnerCount; i++) {
            LivingEntity entity = (LivingEntity) baseLocation.getWorld().spawnEntity(baseLocation, entityType);
            entity.setCustomName("Stacked Entity");
        }

        displaySpawnerCount(baseLocation, spawnerCount);
    }

    private void displaySpawnerCount(Location location, int spawnerCount) {
        for (LivingEntity entity : location.getWorld().getLivingEntities()) {
            if (entity instanceof ArmorStand && entity.getCustomName() != null && entity.getCustomName().startsWith("Spawner Count")) {
                entity.remove();
            }
        }

        ArmorStand armorStand = (ArmorStand) location.getWorld().spawnEntity(location.add(0.5, 1.5, 0.5), EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setCustomName("Spawner Count: " + spawnerCount);
        armorStand.setCustomNameVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                armorStand.remove();
            }
        }.runTaskLater(this, countDisplayDuration);
    }
}
