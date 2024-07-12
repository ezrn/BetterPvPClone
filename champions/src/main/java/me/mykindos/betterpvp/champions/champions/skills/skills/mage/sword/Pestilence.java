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
import me.mykindos.betterpvp.core.utilities.UtilEntity;
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
                "Right click with a Sword to prepare",
                "",
                "Your next sword strike will inflict <effect>Pestilence</effect> on the target,",
                "<effect>Poisoning</effect> them, and spreading to nearby enemies",
                "up to " + getValueString(this::getRadius, level) + " blocks away",
                "",
                "While enemies are infected, they",
                "deal " + getValueString(this::getEnemyDamageReduction, level, 100, "%", 0) + " reduced damage from melee attacks",
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
        new BukkitRunnable() {
            Location originalLocation = caster.getLocation().add(0, 1, 0);
            Location currentLocation = originalLocation;
            long endTime = System.currentTimeMillis() + (long) (getCloudDuration(level) * 1000);

            @Override
            public void run() {
                if (System.currentTimeMillis() > endTime) {
                    this.cancel();
                    return;
                }

                spawnParticles(currentLocation);
                for (LivingEntity entity : UtilEntity.getNearbyEnemies(caster, currentLocation, 5.0)) {
                    infectEntity(caster, entity, level, (Player) caster);
                }
                currentLocation.add(originalLocation.getDirection().normalize().multiply(0.5));
            }
        }.runTaskTimer(champions, 1L, 1L);
    }

    private void infectEntity(LivingEntity caster, LivingEntity target, int level, Player originalCaster) {
        pestilenceData.computeIfAbsent(caster, k -> new HashSet<>()).add(target);
        infectionTimers.put(target, System.currentTimeMillis() + (long) (getInfectionDuration(level) * 1000));
        championsManager.getEffects().addEffect(target, EffectTypes.POISON, 1, (long) (getInfectionDuration(level) * 1000));
    }

    @UpdateEvent
    public void spreadPoison() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Long>> iterator = infectionTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = iterator.next();
            LivingEntity infected = entry.getKey();
            long endTime = entry.getValue();

            if (currentTime > endTime || infected == null || infected.isDead()) {
                iterator.remove();
                pestilenceData.remove(infected);
                trackingTasks.remove(infected);
                infectedTargets.remove(infected);
                originalCasters.remove(infected);
            } else {
                Set<LivingEntity> targets = pestilenceData.computeIfAbsent(infected, k -> new HashSet<>());
                for (LivingEntity target : UtilEntity.getNearbyEnemies(infected, infected.getLocation(), radius)) {
                    if (!targets.contains(target)) {
                        targets.add(target);
                        infectedTargets.computeIfAbsent(infected, k -> new HashSet<>()).add(target);
                        createTrackingTrail(infected, target, originalCasters.get(infected));
                    }
                }
            }
        }
    }

    private void createTrackingTrail(LivingEntity source, LivingEntity target, Player originalCaster) {
        if (trackingTasks.containsKey(source)) return;

        BukkitRunnable trailTask = new BukkitRunnable() {
            Location currentLocation = source.getLocation().add(0, 1.5, 0);

            @Override
            public void run() {
                if (target.isDead() || !pestilenceData.containsKey(source)) {
                    trackingTasks.remove(source);
                    this.cancel();
                    return;
                }

                Vector direction = target.getLocation().add(0, 1.5, 0).subtract(currentLocation).toVector().normalize().multiply(0.5);
                currentLocation.add(direction);

                spawnParticles(currentLocation);

                if (currentLocation.distance(target.getLocation().add(0, 1.5, 0)) <= 0.5) {
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
                .offset(0.2, 0.2, 0.2)
                .extra(0)
                .data(poisonDust)
                .receivers(60)
                .spawn();
    }

    @EventHandler
    public void onDamageReduction(CustomDamageEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (event.getDamager() == null) return;

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

    @UpdateEvent
    public void displayPestilence() {
        pestilenceData.keySet().forEach(this::displayInfectedParticles);
    }

    private void displayInfectedParticles(LivingEntity infected) {
        new ParticleBuilder(Particle.VILLAGER_HAPPY)
                .location(infected.getLocation().add(0, 1, 0))
                .count(1)
                .offset(0.1, 0.1, 0.1)
                .extra(0)
                .receivers(60)
                .spawn();
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
        cloudDuration = getConfig("cloudDuration", 10.0, Double.class);
        cloudDurationIncreasePerLevel = getConfig("cloudDurationIncreasePerLevel", 0.0, Double.class);
    }
}
