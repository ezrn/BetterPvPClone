package me.mykindos.betterpvp.champions.champions.skills.skills.mage.sword;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.data.ChargeData;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.AreaOfEffectSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.ChannelSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.EnergyChannelSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.OffensiveSkill;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.model.display.DisplayComponent;
import org.bukkit.Bukkit;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class StaticLazer extends ChannelSkill implements InteractSkill, EnergyChannelSkill, CooldownSkill, OffensiveSkill, AreaOfEffectSkill {

    private final WeakHashMap<Player, ChargeData> charging = new WeakHashMap<>();
    private final DisplayComponent actionBarComponent = ChargeData.getActionBar(this, charging);

    private double baseCharge;
    private double chargeIncreasePerLevel;
    private double baseDamage;
    private double damageIncreasePerLevel;
    private double baseRange;
    private double rangeIncreasePerLevel;
    private double blocksPerSecond;
    private double headshotDistance;
    private double headshotMultiplier;
    private double hitboxSize;
    private double offsetMultiplier;
    private final Map<Player, Map<LivingEntity, Boolean>> hitEntitiesMap = new HashMap<>();

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
                "Charges " + getValueString(this::getChargePerSecond, level, 1, "%", 0) + " per second,",
                "",
                "Shoot a bolt of static electricity that",
                "travels " + getValueString(this::getRange, level) + " blocks and deals " + getValueString(this::getDamage, level),
                "damage to anyone it comes in contact with",
                "",
                "Headshots will deal double damage",
                "",
                "Cooldown: " + getValueString(this::getCooldown, level),
                "Energy: <val>" + getEnergyPerSecond(level)
        };
    }

    private float getRange(int level) {
        return (float) (baseRange + rangeIncreasePerLevel * (level - 1));
    }

    private double getDamage(int level) {
        return baseDamage + damageIncreasePerLevel * (level - 1);
    }

    private float getChargePerSecond(int level) {
        return (float) (baseCharge + (chargeIncreasePerLevel * (level - 1))); // Increment of 10% per level
    }

    private float getEnergyPerSecond(int level) {
        return (float) (energy - ((level - 1) * energyDecreasePerLevel));
    }

    @Override
    public float getEnergy(int level) {
        return (float) (energy - energyDecreasePerLevel * (level - 1));
    }

    @Override
    public boolean shouldDisplayActionBar(Gamer gamer) {
        return !charging.containsKey(gamer.getPlayer()) && isHolding(gamer.getPlayer());
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
        charging.put(player, new ChargeData(getChargePerSecond(level) / 100));
    }

    @Override
    public void trackPlayer(Player player, Gamer gamer) {
        gamer.getActionBar().add(900, actionBarComponent);
    }

    @Override
    public void invalidatePlayer(Player player, Gamer gamer) {
        gamer.getActionBar().remove(actionBarComponent);
    }

    private void shoot(Player player, float charge, int level) {

        hitEntitiesMap.remove(player);

        final float range = (getRange(level) * charge);
        final Vector direction = player.getEyeLocation().getDirection();
        final Location start = player.getEyeLocation().add(direction);

        new BukkitRunnable() {
            float xDelta = 0;
            Location previousPoint = start.clone();
            final Random random = new Random();

            @Override
            public void run() {
                if (xDelta >= range) {
                    cancel();
                    return;
                }

                xDelta += blocksPerSecond / 20.0; // 20 ticks per second
                Location point = start.clone().add(direction.clone().multiply(xDelta));

                // Add random offset point
                Location midPoint = previousPoint.clone().add(point).multiply(0.5);
                midPoint.add(randomOffset(random), randomOffset(random), randomOffset(random));

                // Draw particles between previous point, mid point, and point
                drawParticleLine(previousPoint, midPoint);
                drawParticleLine(midPoint, point);

                previousPoint = point.clone();

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
                        impact(player, charge, hitPos, level, hitEntity);
                        hitEntitiesMap.get(player).put(hitEntity, true);
                    }
                }

                // Check for block collision
                final Block block = point.getBlock();
                if (block.getType().isSolid()) {
                    hitEntitiesMap.remove(player);
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
        }.runTaskTimer(champions, 0L, 1L); // Runs every tick

        UtilMessage.message(player, getClassType().getName(), "You fired <alt>%s %s</alt>.", getName(), level);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.5f);
    }

    private void impact(Player player, float charge, Location hitPosition, int level, LivingEntity hitEntity) {
        // Particles
        // Damage the hit entity
        double damage = getDamage(level) * charge;
        if (hitEntity.getEyeLocation().add(0, 0.1, 0).distance(hitPosition) <= headshotDistance) {
            damage *= headshotMultiplier;
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 1.0f);
            UtilMessage.message(player, getClassType().getName(), "You headshot <alt2>%s</alt2> with <alt>%s %s</alt>.",hitEntity.getName(), getName(), level);
        }
        else{
            UtilMessage.message(player, getClassType().getName(), "You hit <alt2>%s</alt2> with <alt>%s %s</alt>.",hitEntity.getName(), getName(), level);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 2.0f, 2.0f);
        }

        System.out.println("distance: " + hitEntity.getEyeLocation().distance(hitPosition));
        UtilDamage.doCustomDamage(new CustomDamageEvent(hitEntity, player, null, EntityDamageEvent.DamageCause.CUSTOM, damage, false, getName()));
    }

    private double randomOffset(Random random) {
        return (random.nextDouble() - 0.5) * offsetMultiplier; // Random offset between -0.25 and 0.25
    }

    private void drawParticleLine(Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector());
        int points = (int) (direction.length() * 10);
        direction.normalize().multiply(0.1);

        Location current = start.clone();
        for (int i = 0; i < points; i++) {
            Particle.FIREWORKS_SPARK.builder()
                    .extra(0)
                    .location(current)
                    .receivers(60, true)
                    .spawn();
            current.add(direction);
        }
    }

    @UpdateEvent
    public void updateCharge() {
        // Charge check
        Iterator<Player> iterator = charging.keySet().iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            ChargeData charge = charging.get(player);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            // Remove if they no longer have the skill
            int level = getLevel(player);
            if (level <= 0) {
                iterator.remove();
                continue;
            }

            // Check if they still are blocking and charge
            Gamer gamer = championsManager.getClientManager().search().online(player).getGamer();
            if (isHolding(player) && gamer.isHoldingRightClick() && championsManager.getEnergy().use(player, getName(), getEnergyPerSecond(level) / 20, true)) {
                charge.tick();
                charge.tickSound(player);
                Location loc = player.getLocation();
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BEE_POLLINATE, 0.4f, 2f);


                Random random = UtilMath.RANDOM;
                double x = loc.getX() + (random.nextDouble() - 0.5) * 0.5;
                double y = loc.getY() + (1 + (random.nextDouble() - 0.5) * 0.9);
                double z = loc.getZ() + (random.nextDouble() - 0.5) * 0.5;
                Location particleLoc = new Location(loc.getWorld(), x, y, z);
                new ParticleBuilder(Particle.FIREWORKS_SPARK)
                        .location(particleLoc)
                        .count(1)
                        .offset(0.0, 0.0, 0.0)
                        .extra(0)
                        .receivers(60)
                        .spawn();
                continue;
            }

            shoot(player, charge.getCharge(), level);
            iterator.remove();
        }
    }

    @Override
    public void loadSkillConfig() {
        baseCharge = getConfig("baseCharge", 80.0, Double.class);
        chargeIncreasePerLevel = getConfig("chargeIncreasePerLevel", 20.0, Double.class);
        baseDamage = getConfig("baseDamage", 3.0, Double.class);
        damageIncreasePerLevel = getConfig("damageIncreasePerLevel", 1.5, Double.class);
        baseRange = getConfig("baseRange", 25.0, Double.class);
        rangeIncreasePerLevel = getConfig("rangeIncreasePerLevel", 0.0, Double.class);
        blocksPerSecond = getConfig("blocksPerSecond", 100.0, Double.class);
        headshotDistance = getConfig("headshotDistance", 0.3, Double.class);
        headshotMultiplier = getConfig("headshotMultiplier", 2.0, Double.class);
        hitboxSize = getConfig("hitboxSize", 0.5, Double.class);
        offsetMultiplier = getConfig("offsetMultiplier", 0.5, Double.class);
    }
}
