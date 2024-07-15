package me.mykindos.betterpvp.champions.champions.skills.skills.mage.sword;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
@BPvPListener
public class Pestilence extends Skill implements CooldownSkill, Listener, InteractSkill, OffensiveSkill, DebuffSkill {

    private final Map<LivingEntity, Set<LivingEntity>> pestilenceData = new ConcurrentHashMap<>();
    private final Map<LivingEntity, BukkitRunnable> trackingTasks = new ConcurrentHashMap<>();
    private final Map<LivingEntity, Long> infectionTimers = new ConcurrentHashMap<>();
    private final Map<LivingEntity, Set<LivingEntity>> infectedTargets = new ConcurrentHashMap<>();
    private final Map<LivingEntity, Player> originalCasters = new ConcurrentHashMap<>();
    private final Champions champions;
    private double infectionDuration;
    private double infectionDurationIncreasePerLevel;
    private double enemyDamageReduction;
    private double enemyDamageReductionIncreasePerLevel;
    private double radius;
    private double radiusIncreasePerLevel;
    private double cloudDuration;
    private double cloudDurationIncreasePerLevel;

    @Inject
    public Pestilence(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
        this.champions = champions;
    }

    @Override
    public String getName() {
        return "Pestilence";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Right click with a Sword to activate",
                "",
                "Shoot out a poison cloud that lasts " + getValueString(this::getCloudDuration, level) + "seconds and infects anyone it hits",
                "infects anyone it hits, <effect>Poisoning</effect> them, and spreading ",
                "to neatby enemies up to " + getValueString(this::getRadius, level) + " blocks away",
                "",
                "While enemies are infected, they",
                "deal " + getValueString(this::getEnemyDamageReduction, level, 100, "%", 0) + " less damage from melee attacks",
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
        return cloudDuration + ((level - 1) * cloudDurationIncreasePerLevel);
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Override
    public void activate(Player player, int level) {
        createPoisonCloud(player, level);
    }

    private void createPoisonCloud(Player caster, int level) {
        System.out.println("ran createPoisonCloud");
        new BukkitRunnable() {
            Location originalLocation = caster.getLocation().add(0, 1, 0);
            Location currentLocation = originalLocation;
            long endTime = System.currentTimeMillis() + (long) (getCloudDuration(level) * 1000);

            @Override
            public void run() {
                if (System.currentTimeMillis() > endTime) {
                    System.out.println("Cloud expired");
                    this.cancel();
                    return;
                }

                spawnParticles(currentLocation);
                for (LivingEntity entity : UtilEntity.getNearbyEnemies(caster, currentLocation, 2.0)) {
                    System.out.println("Found entity: " + entity.getName() + " for caster: " + caster.getName() + " at location: " + currentLocation);
                    // Check if entity is already infected before trying to infect them again
                    if (!infectionTimers.containsKey(entity)) {
                        infectEntity(caster, entity, level, (Player) caster);
                    }
                }
                if(!UtilBlock.airFoliage(currentLocation.add(originalLocation.getDirection().normalize().multiply(0.5)).getBlock())) {
                    this.cancel();
                }
                currentLocation.add(originalLocation.getDirection().normalize().multiply(0.25));

            }
        }.runTaskTimer(champions, 1L, 1L);
    }

    private void infectEntity(LivingEntity caster, LivingEntity target, int level, Player originalCaster) {
        if (originalCaster == null) {
            log.error("Original caster is null for caster: " + caster.getName() + " and target: " + target.getName());
            return;
        }

        pestilenceData.computeIfAbsent(caster, k -> new HashSet<>()).add(target);
        System.out.println("Infecting target: " + target.getName());
        infectionTimers.put(target, System.currentTimeMillis() + (long) (getInfectionDuration(level) * 1000));
        championsManager.getEffects().addEffect(target, EffectTypes.POISON, 1, (long) (getInfectionDuration(level) * 1000));
        originalCasters.put(target, originalCaster);
        spreadPoison();
    }

    @UpdateEvent(delay = 500)
    public void spreadPoison() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Long>> iterator = infectionTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = iterator.next();
            LivingEntity infected = entry.getKey();
            long endTime = entry.getValue();
            Player caster = originalCasters.get(infected);

            if (currentTime > endTime || infected == null || infected.isDead()) {
                iterator.remove();
                if (infected != null) {
                    System.out.println("Pestilence wore off on player: " + infected.getName());
                }
                pestilenceData.remove(infected);
                trackingTasks.remove(infected);
                infectedTargets.remove(infected);
                originalCasters.remove(infected);
            } else {
                int level = getLevel(caster);
                if(level <= 0) return;
                Set<LivingEntity> targets = pestilenceData.computeIfAbsent(infected, k -> new HashSet<>());

                for (LivingEntity target : UtilEntity.getNearbyEnemies(infected, infected.getLocation(), getRadius(level))) {
                    System.out.println("Found target: " + target.getName() + " in range: " + radius + " of infected player: " + infected.getName());
                    if (!targets.contains(target) && !infectionTimers.containsKey(target)) {
                        System.out.println("Target has not already been sent a poison trail and is not infected");
                        targets.add(target);
                        infectedTargets.computeIfAbsent(infected, k -> new HashSet<>()).add(target);
                        createTrackingTrail(infected, target, caster);
                    } else {
                        System.out.println("Target has already been sent a poison trail or is infected");
                        //need to allow an infected to send poison to multiple targets
                    }
                }
            }
        }
    }

    private void createTrackingTrail(LivingEntity source, LivingEntity target, Player originalCaster) {
        if (trackingTasks.containsKey(source)) return;

        if (originalCaster == null) {
            log.error("Original caster is null for tracking trail. Source: " + source.getName() + " Target: " + target.getName());
            return;
        }

        System.out.println("Creating tracking trail");

        long startTime = System.currentTimeMillis();
        double cloudDuration = getCloudDuration(getLevel(originalCaster)) * 1000; // Convert seconds to milliseconds

        BukkitRunnable trailTask = new BukkitRunnable() {
            Location currentLocation = source.getEyeLocation();

            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (target.isDead() || elapsedTime > cloudDuration) {
                    trackingTasks.remove(source);
                    System.out.println("Canceling tracking trail");
                    this.cancel();
                    return;
                }

                Vector direction = target.getEyeLocation().subtract(currentLocation).toVector().normalize().multiply(0.25);

                if (!UtilBlock.airFoliage(currentLocation.add(direction).getBlock())) {
                    this.cancel();
                    return;
                }

                currentLocation.add(direction);
                spawnParticles(currentLocation);

                if (currentLocation.distance(target.getEyeLocation()) <= 0.5) {
                    System.out.println("Tracking trail reached the target");
                    infectEntity(source, target, getLevel(originalCaster), originalCaster);
                    trackingTasks.remove(source);
                    this.cancel();
                }
            }
        };

        trailTask.runTaskTimer(champions, 1L, 1L);
        trackingTasks.put(source, trailTask);
    }

    private void spawnParticles(Location location) {
        Particle.DustOptions poisonDust = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1);
        new ParticleBuilder(Particle.REDSTONE)
                .location(location)
                .count(15)
                .offset(0.1, 0.1, 0.1)
                .extra(0)
                .data(poisonDust)
                .receivers(60)
                .spawn();

        Random random = UtilMath.RANDOM;
        double dx = (random.nextDouble() - 0.5) * 0.2;
        double dy = (random.nextDouble() - 0.5) * 0.2;
        double dz = (random.nextDouble() - 0.5) * 0.2;

        Location particleLocation = location.clone().add(dx, dy, dz);

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

    @EventHandler
    public void onDamageReduction(CustomDamageEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (event.getDamager() == null) return;
        System.out.println("Damage was reduced");

        double reduction = getDamageReduction((LivingEntity) event.getDamager());
        event.setDamage(event.getDamage() * (1 - reduction));
    }

    public double getDamageReduction(LivingEntity entity) {
        return pestilenceData.values().stream()
                .flatMap(Collection::stream)
                .filter(target -> target.equals(entity))
                .map(target -> enemyDamageReduction)
                .max(Double::compare)
                .orElse(0d);
    }

    @UpdateEvent(delay = 1000)
    public void displayPestilence() {
        pestilenceData.forEach((caster, infectedEntities) -> {
            infectedEntities.forEach(infected -> {
                for (int q = 0; q <= 10; q++) {
                    final float x = (float) (1 * Math.cos(2 * Math.PI * q / 10));
                    final float z = (float) (1 * Math.sin(2 * Math.PI * q / 10));

                    Bukkit.getScheduler().scheduleSyncDelayedTask(champions, () -> {
                        if (infectionTimers.containsKey(infected)) {
                            new ParticleBuilder(Particle.VILLAGER_HAPPY)
                                    .location(infected.getLocation().add(x, 1, z))
                                    .receivers(30)
                                    .extra(0)
                                    .spawn();
                        }
                    }, q * 5L);
                }
            });
        });
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
        radiusIncreasePerLevel = getConfig("radiusIncreasePerLevel", 1.0, Double.class);
        cloudDuration = getConfig("cloudDuration", 4.0, Double.class);
        cloudDurationIncreasePerLevel = getConfig("cloudDurationIncreasePerLevel", 1.0, Double.class);
    }
}
