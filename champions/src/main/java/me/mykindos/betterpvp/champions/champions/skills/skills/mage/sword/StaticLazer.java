package me.mykindos.betterpvp.champions.champions.skills.skills.mage.sword;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.AreaOfEffectSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.ChannelSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.OffensiveSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

@Singleton
@BPvPListener
public class StaticLazer extends ChannelSkill implements InteractSkill, CooldownSkill, OffensiveSkill, AreaOfEffectSkill {
    private double baseDamage;
    private double damageIncreasePerLevel;
    private double baseRange;
    private double rangeIncreasePerLevel;
    private double blocksPerSecond;
    private double headshotDistance;
    private double headshotMultiplier;
    private double hitboxSize;
    private final Map<Player, Map<LivingEntity, Boolean>> hitEntitiesMap = new HashMap<>();
    private final Map<LivingEntity, Location> playerEyeLocationMap = new HashMap<>();
    private final Map<Player, Location> hitPositionMap = new HashMap<>();

    @Inject
    public StaticLazer(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Static Lazer";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Hold right click with a Sword to channel",
                "",
                "Shoot a bolt of static electricity that",
                "travels " + getValueString(this::getRange, level) + " blocks and deals " + getValueString(this::getDamage, level),
                "damage to anyone it comes in contact with",
                "",
                "Headshots will deal double damage",
                "",
                "Cooldown: " + getValueString(this::getCooldown, level),
        };
    }

    private float getRange(int level) {
        return (float) (baseRange + rangeIncreasePerLevel * (level - 1));
    }

    private double getDamage(int level) {
        return baseDamage + damageIncreasePerLevel * (level - 1);
    }

    @Override
    public Role getClassType() {
        return Role.MAGE;
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - (level - 1) * cooldownDecreasePerLevel;
    }

    @Override
    public SkillType getType() {
        return SkillType.SWORD;
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Override
    public void activate(Player player, int level) {
        clearHitEntities(player);
        shoot(player, level);
    }

    private void clearHitEntities(Player player) {
        hitEntitiesMap.remove(player);
        playerEyeLocationMap.remove(player);
        hitPositionMap.remove(player);
    }

    private void shoot(Player player, int level) {
        final float range = getRange(level);
        final Vector direction = player.getEyeLocation().getDirection();
        final Location start = player.getEyeLocation().add(direction);

        new BukkitRunnable() {
            float xDelta = 0;
            Location previousPoint = start.clone();

            @Override
            public void run() {
                if (xDelta >= range) {
                    clearHitEntities(player);
                    cancel();
                    return;
                }

                xDelta += blocksPerSecond / 20.0; // 20 ticks per second
                Location currentPoint = start.clone().add(direction.clone().multiply(xDelta));

                for (int i = 0; i <= 5; i++) {
                    Location point = previousPoint.clone().add(currentPoint.clone().subtract(previousPoint).multiply(i / 5.0));
                    final RayTraceResult result = player.getEyeLocation().getWorld().rayTraceEntities(
                            player.getEyeLocation(),
                            direction,
                            xDelta,
                            hitboxSize,
                            entity -> entity instanceof LivingEntity && !entity.equals(player)
                    );

                    // Check if the ray trace hit an entity
                    if (result != null && result.getHitEntity() instanceof LivingEntity) {
                        LivingEntity hitEntity = (LivingEntity) result.getHitEntity();
                        if (!hitEntitiesMap.computeIfAbsent(player, k -> new HashMap<>()).containsKey(hitEntity)) {
                            Location hitPos = result.getHitPosition().toLocation(point.getWorld());
                            // Set the x and z coordinates of the hitPos to the player's x and z coordinates
                            hitPos.setX(hitEntity.getLocation().getX());
                            hitPos.setZ(hitEntity.getLocation().getZ());
                            impact(player, hitPos, level, hitEntity);
                            hitEntitiesMap.get(player).put(hitEntity, true);
                            playerEyeLocationMap.put(hitEntity, hitEntity.getEyeLocation().add(0, 0.1, 0));
                            hitPositionMap.put(player, hitPos);
                        }
                    }

                    // Check for block collision
                    final Block block = point.getBlock();
                    if (block.getType().isSolid()) {
                        clearHitEntities(player);
                        cancel();
                        return;
                    }

                    // Particle
                    Particle.FIREWORKS_SPARK.builder()
                            .extra(0)
                            .location(point)
                            .receivers(60, true)
                            .spawn();
                }

                previousPoint = currentPoint.clone();
            }
        }.runTaskTimer(champions, 0L, 1L); // Runs every tick

        UtilMessage.message(player, getClassType().getName(), "You fired <alt>%s %s</alt>.", getName(), level);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.5f);
    }

    private void impact(Player player, Location hitPosition, int level, LivingEntity hitEntity) {
        // Particles
        // Damage the hit entity
        double damage = getDamage(level);
        if (hitEntity.getEyeLocation().add(0, 0.1, 0).distance(hitPosition) <= headshotDistance) {
            damage *= headshotMultiplier;
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 2.0f);
            UtilMessage.message(player, getClassType().getName(), "You headshot <alt2>%s<alt2> with <alt>%s %s</alt>.",hitEntity.getName(), getName(), level);
        }
        else{
            UtilMessage.message(player, getClassType().getName(), "You hit <alt2>%s<alt2> with <alt>%s %s</alt>.",hitEntity.getName(), getName(), level);
        }

        System.out.println("distance: " + hitEntity.getEyeLocation().distance(hitPosition));
        UtilDamage.doCustomDamage(new CustomDamageEvent(hitEntity, player, null, EntityDamageEvent.DamageCause.CUSTOM, damage, false, getName()));
    }

    @UpdateEvent
    public void spawnParticles() {
        for (LivingEntity ent : playerEyeLocationMap.keySet()) {
            Location eyeLocation = playerEyeLocationMap.get(ent);

            if (eyeLocation != null) {
                new ParticleBuilder(Particle.REDSTONE)
                        .location(eyeLocation)
                        .count(10)
                        .offset(0, 0, 0)
                        .color(Color.BLUE)
                        .receivers(60)
                        .spawn();
            }

            for (Player player : hitPositionMap.keySet()) {
                Location hitPosition = hitPositionMap.get(player);
                if (hitPosition != null) {
                    new ParticleBuilder(Particle.REDSTONE)
                            .location(hitPosition)
                            .count(10)
                            .offset(0, 0, 0)
                            .color(Color.RED)
                            .receivers(60)
                            .spawn();
                }
            }
        }
    }

    @Override
    public void loadSkillConfig() {
        baseDamage = getConfig("baseDamage", 3.0, Double.class);
        damageIncreasePerLevel = getConfig("damageIncreasePerLevel", 1.5, Double.class);
        baseRange = getConfig("baseRange", 15.0, Double.class);
        rangeIncreasePerLevel = getConfig("rangeIncreasePerLevel", 0.0, Double.class);
        blocksPerSecond = getConfig("blocksPerSecond", 30.0, Double.class);
        headshotDistance = getConfig("headshotDistance", 0.3, Double.class);
        headshotMultiplier = getConfig("headshotMultiplier", 2.0, Double.class);
        hitboxSize = getConfig("hitboxSize", 0.1, Double.class);
    }
}
