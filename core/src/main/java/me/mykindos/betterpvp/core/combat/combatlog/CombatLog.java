package me.mykindos.betterpvp.core.combat.combatlog;

import lombok.CustomLog;
import lombok.Getter;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CustomLog
@Getter
public class CombatLog {

    private final UUID owner;
    private final List<ItemStack> items;
    private final long expiryTime;
    private final Skeleton combatLogSkeleton;
    private final String playerName;
    private final ItemStack[] armorContents;
    private final double playerHealth;

    public CombatLog(Player player, long expiryTime) {
        this.owner = player.getUniqueId();
        this.expiryTime = expiryTime;
        this.playerName = player.getName();
        this.playerHealth = player.getHealth();
        items = new ArrayList<>();
        armorContents = player.getInventory().getArmorContents();

        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack == null || itemStack.getType() == Material.AIR) continue;
            items.add(itemStack);
        }

        combatLogSkeleton = (Skeleton) player.getWorld().spawnEntity(player.getLocation(), EntityType.SKELETON);
        setupSkeleton(player);
    }

    private void setupSkeleton(Player player) {
        combatLogSkeleton.setAI(false);
        combatLogSkeleton.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(playerHealth);
        combatLogSkeleton.setHealth(playerHealth);
        combatLogSkeleton.setCustomNameVisible(true);
        combatLogSkeleton.setRemoveWhenFarAway(false);
        combatLogSkeleton.setCanPickupItems(false);

        // Set skeleton items
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && mainHandItem.getType() != Material.AIR) {
            combatLogSkeleton.getEquipment().setItemInMainHand(mainHandItem);
        }

        combatLogSkeleton.getEquipment().setArmorContents(armorContents);
        updateSkeletonName();
    }

    public void updateSkeletonName() {
        long timeRemaining = expiryTime - System.currentTimeMillis();
        String timeDisplay = timeRemaining >= 10000 ? "Infinity" : String.format("%.1f", timeRemaining / 1000.0);
        String displayName = playerName + " " + timeDisplay;
        combatLogSkeleton.setCustomName(displayName);
    }

    public void onSkeletonDeath(EntityDeathEvent event) {
        for (ItemStack stack : items) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            combatLogSkeleton.getLocation().getWorld().dropItemNaturally(combatLogSkeleton.getLocation(), stack);
        }

        UtilMessage.broadcast("Log", "<yellow>%s</yellow> dropped their inventory for combat logging.", playerName);
        File file = new File("world/playerdata", owner + ".dat");
        if (file.exists()) {
            if (!file.delete()) {
                log.error("Failed to delete dat file for player {}", owner);
            }
        }
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() > expiryTime;
    }
}
