package me.mykindos.betterpvp.champions.champions.skills.skills.mage.passives;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillWeapons;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.PassiveSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.combat.throwables.ThrowableItem;
import me.mykindos.betterpvp.core.combat.throwables.ThrowableListener;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@BPvPListener
public class GlacialBlade extends Skill implements PassiveSkill, CooldownSkill, ThrowableListener {

    private double damage;
    private double damageIncreasePerLevel;
    private List<Item> iceShards = new ArrayList<>();
    private Map<Item, Player> shardMap = new HashMap<>();


    @Inject
    public GlacialBlade(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Glacial Blade";
    }

    @Override
    public String[] getDescription(int level) {

        return new String[]{
                "Swinging your sword launches a glacial",
                "shard dealing <val>" + getDamage(level) + "</val> damage to enemeis",
                "",
                "If you are too close to your target, it will not activate",
                "",
                "Internal Cooldown: <val>" + getCooldown(level)
        };
    }

    public double getDamage(int level){
        return damage + ((level - 1) * damageIncreasePerLevel);
    }

    @EventHandler
    public void onSwing(PlayerInteractEvent event) {
        if (!SkillWeapons.isHolding(event.getPlayer(), SkillType.SWORD)) return;

        Player player = event.getPlayer();

        int level = getLevel(player);
        if (level < 1) return;

        if (!isObstructionNearby(player)) {
            ItemStack ghastTear = new ItemStack(Material.GHAST_TEAR);
            Item ice = player.getWorld().dropItem(player.getEyeLocation(), ghastTear);
            ice.getWorld().playSound(ice.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
            ice.setVelocity(player.getLocation().getDirection().multiply(2.5));
            championsManager.getThrowables().addThrowable(this, ice, player, getName(), 5000L);
            iceShards.add(ice);
            shardMap.put(ice, player);

            championsManager.getCooldowns().removeCooldown(player, getName(), true);
            championsManager.getCooldowns().use(player,
                    getName(),
                    getCooldown(level),
                    true,
                    true,
                    isCancellable());
        }
    }

    private boolean isObstructionNearby(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        for (double distance = 0; distance <= 3; distance += 0.2) {
            Location stepLocation = eyeLocation.clone().add(direction.multiply(distance));

            List<Entity> nearbyEntities = (List<Entity>) stepLocation.getWorld().getNearbyEntities(stepLocation, 0.2, 0.2, 0.2);
            if (!nearbyEntities.isEmpty()) {
                for (Entity entity : nearbyEntities) {
                    if (!entity.equals(player)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @UpdateEvent
    public void onUpdate() {
        for (Item ice : new ArrayList<>(iceShards)) {
            if (!ice.isOnGround() && !ice.isDead()) {
                ice.getWorld().spawnParticle(Particle.SNOWBALL, ice.getLocation(), 1);
            } else {
                ice.remove();
                iceShards.remove(ice);
            }
        }
    }

    @Override
    public void onThrowableHit(ThrowableItem throwableItem, LivingEntity thrower, LivingEntity hit) {
        if (hit instanceof ArmorStand) {
            return;
        }

        Item iceItem = throwableItem.getItem();
        if (iceItem != null) {
            iceShards.remove(iceItem);
            iceItem.remove();
        }

        if (thrower instanceof Player damager) {
            int level = getLevel(damager);

            CustomDamageEvent cde = new CustomDamageEvent(hit, damager, null, DamageCause.CUSTOM, getDamage(level), false, "Glacial Blade");
            cde.setDamageDelay(0);
            UtilDamage.doCustomDamage(cde);
            hit.getWorld().playEffect(hit.getLocation(), Effect.STEP_SOUND, Material.GLASS);

        }
    }
    @Override
    public Role getClassType() {
        return Role.MAGE;
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE_B;
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }

    public void loadSkillConfig() {
        damage = getConfig("damage", 2.5, Double.class);
        damageIncreasePerLevel = getConfig("damageIncreasePerLevel", 1.0, Double.class);
    }

}