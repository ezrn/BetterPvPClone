package me.mykindos.betterpvp.champions.champions.skills.skills.mage.sword;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.DebuffSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.OffensiveSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.effects.events.EffectClearEvent;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@Singleton
@BPvPListener
public class Pestilence extends Skill implements InteractSkill, CooldownSkill, OffensiveSkill, DebuffSkill {

    private final ConcurrentHashMap<LivingEntity, Long> infectionTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LivingEntity, Double> damageReductions = new ConcurrentHashMap<>();
    private final Set<LivingEntity> infectedEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> spreadTracking = new ConcurrentHashMap<>();
    private final Logger logger = Bukkit.getLogger();

    private double infectionDuration;
    private double infectionDurationIncreasePerLevel;
    private double enemyDamageReduction;
    private double enemyDamageReductionIncreasePerLevel;
    private double radius;
    private double radiusIncreasePerLevel;
    private double cloudDuration;

    @Inject
    public Pestilence(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Pestilence";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Right click with a Sword to prepare",
                "",
                "Create a poison cloud that moves forward,",
                "infecting those who are touched by it.",
                "",
                "Enemies deal " + getValueString(this::getEnemyDamageReduction, level, 100, "%", 0) + " reduced damage",
                "",
                "<effect>Pestilence</effect> lasts " + getValueString(this::getInfectionDuration, level) + " seconds",
                "",
                "Cooldown: " + getValueString(this::getCooldown, level)
        };
    }

    public double getEnemyDamageReduction(int level) {
        return enemyDamageReduction + ((level - 1) * enemyDamageReductionIncreasePerLevel);
    }

    public double getInfectionDuration(int level) {
        return infectionDuration + ((level - 1) * infectionDurationIncreasePerLevel);
    }

    public double getRadius(int level) {
        return radius + ((level - 1) * radiusIncreasePerLevel);
    }

    public double getCloudDuration(int level) {
        return cloudDuration + ((level - 1) * 0.5);
    }

    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @EventHandler
    public void onDamageReduction(CustomDamageEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (event.getDamager() == null) return;

        double reduction = getDamageReduction(event.getDamager());
        event.setDamage(event.getDamage() * (1 - reduction));
        logger.info("Damage reduction applied: " + (reduction * 100) + "%");
    }

    public double getDamageReduction(LivingEntity entity) {
        return damageReductions.getOrDefault(entity, 0.0);
    }

    @EventHandler
    public void onEffectClear(EffectClearEvent event) {
        LivingEntity player = event.getPlayer();
        infectionTimes.remove(player);
        damageReductions.remove(player);
        infectedEntities.remove(player);
        spreadTracking.remove(player.getUniqueId());
        logger.info("Effect cleared for player: " + player.getName());
    }

    @Override
    public void activate(Player player, int level) {
        logger.info("Activating Pestilence for player: " + player.getName() + " at level: " + level);
        createPoisonCloud(player, level, player.getEyeLocation());
    }

    private void createPoisonCloud(Player player, int level, Location startLocation) {
        new BukkitRunnable() {
            Location currentLocation = startLocation.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= getCloudDuration(level) * 20) {
                    logger.info("Poison cloud expired after " + ticks + " ticks");
                    cancel();
                    return;
                }
                currentLocation.add(currentLocation.getDirection().multiply(0.2));

                double spread = 1.5;
                for (int i = 0; i < 30; i++) {
                    Random random = UtilMath.RANDOM;

                    double dx = (random.nextDouble() - 0.5) * spread;
                    double dy = (random.nextDouble() - 0.5) * spread;
                    double dz = (random.nextDouble() - 0.5) * spread;

                    Location particleLocation = currentLocation.clone().add(dx, dy, dz);

                    double red = 0.4;
                    double green = 0.8;
                    double blue = 0.4;

                    new ParticleBuilder(Particle.SPELL_MOB)
                            .location(particleLocation)
                            .count(0)
                            .offset(red, green, blue)
                            .extra(1.0)
                            .receivers(60)
                            .spawn();
                }

                for (LivingEntity entity : UtilEntity.getNearbyEnemies(player, currentLocation, 2)) {
                    if (infectEntity(player, entity, level)) {
                        logger.info("Entity " + entity.getName() + " infected by poison cloud at location " + currentLocation);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(champions, 0L, 1L);
    }

    @UpdateEvent
    public void spreadPoison() {
        logger.info("running");
        infectedEntities.forEach(infected -> {
            logger.info("There is an infected");
            UUID originalCaster = getOriginalCaster(infected);
            if (originalCaster == null){
                return;
            }

            logger.info("original caster isnt null");
            Player player = Bukkit.getPlayer(originalCaster);
            if (player == null) {
                return;
            }

            for (LivingEntity target : UtilEntity.getNearbyEnemies(player, infected.getLocation(), getRadius(getLevel(player)))) {
                logger.info("getting nearby entities for player around infected location");
                if (canSpreadTo(infected, target)) {
                    logger.info("Spreading poison from " + infected.getName() + " to " + target.getName());
                    createTrackingPoisonCloud(infected, getLevel(player), target);
                    addSpread(infected, target);
                    return;
                }
            }
        });
    }

    private boolean infectEntity(Player player, LivingEntity entity, int level) {
        long duration = (long) getInfectionDuration(level) * 1000;
        double reduction = getEnemyDamageReduction(level);
        if (!infectedEntities.contains(entity)) {
            logger.info("Entity " + entity.getName() + " infected by player " + player.getName());
            infectedEntities.add(entity);
            infectionTimes.put(entity, System.currentTimeMillis() + duration);
            damageReductions.put(entity, reduction);
            championsManager.getEffects().addEffect(entity, EffectTypes.POISON, 1, duration);
            return true;
        }
        return false;
    }

    private void createTrackingPoisonCloud(LivingEntity source, int level, LivingEntity target) {
        new BukkitRunnable() {
            Location currentLocation = source.getLocation().add(0, 1.5, 0);

            @Override
            public void run() {
                if (!infectedEntities.contains(target) || target.isDead()) {
                    logger.info("Stopping tracking poison cloud for entity " + target.getName());
                    this.cancel();
                    return;
                }

                Vector direction = target.getLocation().add(0, 1.5, 0).subtract(currentLocation).toVector().normalize().multiply(0.5);
                currentLocation.add(direction);

                new ParticleBuilder(Particle.SPELL_MOB)
                        .location(currentLocation)
                        .count(1)
                        .offset(0.1, 0.1, 0.1)
                        .extra(0)
                        .spawn();

                if (currentLocation.distance(target.getLocation().add(0, 1.5, 0)) <= 1.0) {
                    logger.info("Target entity " + target.getName() + " hit by tracking poison cloud");
                    infectEntity((Player) source, target, level);
                    this.cancel();
                }
            }
        }.runTaskTimer(champions, 0L, 1L);
    }

    private UUID getOriginalCaster(LivingEntity entity) {
        return infectionTimes.keySet().stream()
                .filter(e -> e.equals(entity))
                .findFirst()
                .map(LivingEntity::getUniqueId)
                .orElse(null);
    }

    private boolean canSpreadTo(LivingEntity source, LivingEntity target) {
        UUID sourceUUID = source.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        return !spreadTracking.getOrDefault(sourceUUID, new HashSet<>()).contains(targetUUID);
    }

    private void addSpread(LivingEntity source, LivingEntity target) {
        spreadTracking.computeIfAbsent(source.getUniqueId(), k -> new HashSet<>()).add(target.getUniqueId());
    }

    @Override
    public Role getClassType() {
        return Role.MAGE;
    }

    @Override
    public SkillType getType() {
        return SkillType.SWORD;
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }

    @Override
    public void loadSkillConfig() {
        infectionDuration = getConfig("infectionDuration", 5.0, Double.class);
        infectionDurationIncreasePerLevel = getConfig("infectionDurationIncreasePerLevel", 0.0, Double.class);
        enemyDamageReduction = getConfig("enemyDamageReduction", 0.20, Double.class);
        enemyDamageReductionIncreasePerLevel = getConfig("enemyDamageReductionIncreasePerLevel", 0.0, Double.class);
        radius = getConfig("radius", 5.0, Double.class);
        radiusIncreasePerLevel = getConfig("radiusIncreasePerLevel", 0.0, Double.class);
        cloudDuration = getConfig("cloudDuration", 5.0, Double.class);
    }
}
