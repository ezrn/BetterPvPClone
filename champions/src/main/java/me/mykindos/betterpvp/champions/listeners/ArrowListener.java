package me.mykindos.betterpvp.champions.listeners;

import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.events.items.ItemUpdateLoreEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.champions.champions.roles.RoleManager;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;

import javax.inject.Inject;
import java.util.HashMap;

@BPvPListener
@Singleton
public class ArrowListener implements Listener {
    private final RoleManager roleManager;

    @Inject
    @Config(path = "combat.crit-arrows", defaultValue = "true")
    private boolean critArrowsEnabled;

    @Inject
    @Config(path = "combat.arrow-base-damage", defaultValue = "6.0")
    private double baseArrowDamage;

    @Inject
    @Config(path = "combat.assassin-arrow-damage", defaultValue = "5.0")
    private double assassinArrowDamage;
    private final HashMap<Arrow, Float> arrows = new HashMap<>();

    private final Champions champions;

    @Inject
    public ArrowListener(Champions champions, RoleManager roleManager) {
        this.champions = champions;
        this.roleManager = roleManager;
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Arrow arrow) {
            if (event.getEntity() instanceof Player player) {
                arrow.setMetadata("ShotWith", new FixedMetadataValue(champions, player.getInventory().getItemInMainHand().getType().name()));
            }
            arrows.put(arrow, event.getForce());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBaseArrowDamage(CustomDamageEvent event) {
        if (event.getProjectile() instanceof Arrow) {
            event.setDamage(baseArrowDamage);

            if (event.getDamager() instanceof Player player) {
                if (roleManager.hasRole(player, Role.ASSASSIN)) {
                    event.setDamage(assassinArrowDamage);
                }
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowDamage(CustomDamageEvent event) {
        if (event.getProjectile() instanceof Arrow arrow) {
            if (arrows.containsKey(arrow)) {
                float dmgMultiplier = arrows.get(arrow);
                event.setDamage(event.getDamage() * dmgMultiplier);
                arrows.remove(arrow);
            }
        }

    }

    @EventHandler
    public void onUpdateLore(ItemUpdateLoreEvent event) {
        if(event.getItemStack().getType() == Material.ARROW) {
            event.getItemLore().clear();
            event.getItemLore().add(UtilMessage.deserialize("<reset>Damage: <green>%s", baseArrowDamage));
        }
    }

    /*
     * Removes arrows when they hit the ground, or a player
     */
    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            UtilServer.runTaskLater(champions, () -> {
                arrow.remove();
                arrows.remove(arrow);
            }, 5L);
        }
    }

}
