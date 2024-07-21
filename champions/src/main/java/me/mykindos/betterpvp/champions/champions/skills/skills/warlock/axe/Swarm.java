package me.mykindos.betterpvp.champions.champions.skills.skills.warlock.axe;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Data;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.ChannelSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.EnergyChannelSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.combat.events.VelocityType;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import me.mykindos.betterpvp.core.utilities.UtilVelocity;
import me.mykindos.betterpvp.core.utilities.events.EntityProperty;
import me.mykindos.betterpvp.core.utilities.math.VelocityData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Bat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class Swarm extends ChannelSkill implements CooldownSkill, InteractSkill, Listener {

    private final WeakHashMap<Player, ArrayList<BatData>> batData = new WeakHashMap<>();
    private final WeakHashMap<Player, Boolean> leadsAttached = new WeakHashMap<>();
    private final WeakHashMap<Player, Vector> defaultDirections = new WeakHashMap<>();
    private final WeakHashMap<Player, Long> leadAttachTimes = new WeakHashMap<>();

    private double batLifespanIncreasePerLevel;
    private double batLifespan;
    private double batDamage;
    private int numberOfBats;
    private double pullSpeed;
    private double batSpeed;
    private int numberOfBatsIncreasePerLevel;
    private double baseHealthReduction;
    private double healthReductionDecreasePerLevel;
    private double  fallDamageLimit;
    private double knockbackStrength;
    private Random random = new Random();

    @Inject
    public Swarm(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Swarm";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Right click with an Axe to activate",
                "",
                "Release a swarm of " + getValueString(this::getNumBats, level) + " bats which will",
                "fly for " + getValueString(this::getBatLifespan, level) + " seconds, damaging, <effect>Shocking</effect>",
                "and knocking back any enemies they hit",
                "",
                "Right click again to pull yourself towards",
                "the bats, sacrificing " + getValueString(this::getHealthReduction, level) + " health",
                "",
                "Cooldown: " + getValueString(this::getCooldown, level),
        };
    }

    @Override
    public Role getClassType() {
        return Role.WARLOCK;
    }

    @Override
    public SkillType getType() {
        return SkillType.AXE;
    }

    public boolean hitPlayer(Location loc, LivingEntity player) {
        if (loc.add(0, -loc.getY(), 0).toVector().subtract(player.getLocation()
                .add(0, -player.getLocation().getY(), 0).toVector()).length() < 0.8D) {
            return true;
        }
        if (loc.add(0, -loc.getY(), 0).toVector().subtract(player.getLocation()
                .add(0, -player.getLocation().getY(), 0).toVector()).length() < 1.2) {
            return (loc.getY() > player.getLocation().getY()) && (loc.getY() < player.getEyeLocation().getY());
        }
        return false;
    }

    @Override
    public boolean canUse(Player player) {
        int level = getLevel(player);

        if ((batData.containsKey(player)) && !batData.get(player).isEmpty()){
            activate(player, level);
            return false;
        }
        return true;
    }

    public double getHealthReduction(int level) {
        return baseHealthReduction - ((level - 1) * healthReductionDecreasePerLevel);
    }

    public int getNumBats(int level){
        return numberOfBats + ((level - 1) * numberOfBatsIncreasePerLevel);
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }

    public double getBatLifespan(int level){
        return batLifespan + ((level - 1) * batLifespanIncreasePerLevel);
    }

    @UpdateEvent
    public void batHit() {
        for (Player player : batData.keySet()) {
            Vector direction = defaultDirections.get(player);
            if (direction == null) continue;
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                bat.setVelocity(direction.clone().multiply(batSpeed));

                for (var data : UtilEntity.getNearbyEntities(player, bat.getLocation(), 2, EntityProperty.ENEMY)) {
                    LivingEntity other = data.get();

                    if (other instanceof Bat) continue;
                    if (!hitPlayer(bat.getLocation(), other)) continue;

                    if (other instanceof Player) {
                        championsManager.getEffects().addEffect(other, EffectTypes.SHOCK, 800L);
                    }

                    final CustomDamageEvent event = new CustomDamageEvent(other,
                            player,
                            null,
                            DamageCause.CUSTOM,
                            batDamage,
                            false,
                            getName());
                    UtilDamage.doCustomDamage(event);

                    doParticles(bat.getLocation());

                    if (!event.isCancelled()) {
                        Vector vector = bat.getLocation().getDirection();
                        final VelocityData velocityData = new VelocityData(vector, knockbackStrength, 0.2d, 7.5d, true);
                        UtilVelocity.velocity(other, player, velocityData);

                        bat.getWorld().playSound(bat.getLocation(), Sound.ENTITY_BAT_HURT, 0.1F, 0.7F);
                    }

                    bat.remove();
                }
            }
        }
    }

    public void applyBatPull() {
        for (Player player : leadsAttached.keySet()) {
            if (leadsAttached.get(player)) {
                ArrayList<BatData> bats = batData.get(player);
                if (bats != null && !bats.isEmpty()) {
                    // Filter out dead bats and compute the average position
                    Vector averagePosition = new Vector(0, 0, 0);
                    int aliveBatCount = 0;
                    for (BatData batData : bats) {
                        Bat bat = batData.getBat();
                        if (bat != null && !bat.isDead()) {
                            averagePosition.add(bat.getLocation().toVector());
                            aliveBatCount++;
                        }
                    }

                    if (aliveBatCount > 0) {
                        // Compute the average position
                        averagePosition.multiply(1.0 / aliveBatCount);

                        // Pull player towards the average position
                        Vector direction = averagePosition.subtract(player.getLocation().toVector()).normalize();
                        VelocityData velocityData = new VelocityData(direction, pullSpeed, false, 0.0D, 0.4D, 0.6D, false);
                        UtilVelocity.velocity(player, player, velocityData, VelocityType.CUSTOM);

                        // Apply NO_FALL effect after a short delay
                        UtilServer.runTaskLater(champions, () -> {
                            championsManager.getEffects().addEffect(player, player, EffectTypes.NO_FALL, getName(), (int) fallDamageLimit,
                                    50L, true, true, UtilBlock::isGrounded);
                        }, 3L);
                    }
                }
            }
        }
    }


    @UpdateEvent(delay = 500)
    public void onUpdate() {
        Iterator<Entry<Player, ArrayList<BatData>>> iterator = batData.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<Player, ArrayList<BatData>> data = iterator.next();
            Player player = data.getKey();
            int level = getLevel(player);

            ListIterator<BatData> batIt = data.getValue().listIterator();
            while (batIt.hasNext()) {
                BatData batData = batIt.next();
                Bat bat = batData.getBat();

                if (bat == null || bat.isDead()) {
                    batIt.remove();
                    continue;
                }

                if (UtilTime.elapsed(batData.getTimer(), (long) getBatLifespan(level) * 1000)) {
                    doParticles(bat.getLocation());
                    bat.remove();
                    batIt.remove();
                }

                if (leadsAttached.containsKey(player) && leadsAttached.get(player) && UtilTime.elapsed(leadAttachTimes.get(player), 100L)) {
                    // Detach leads and stop applying velocity
                    leadsAttached.put(player, false);
                    for (BatData bData : data.getValue()) {
                        Bat b = bData.getBat();
                        if (b != null && !b.isDead()) {
                            b.setLeashHolder(null);
                        }
                    }
                }
            }

            if (data.getValue().isEmpty()) {
                iterator.remove();
                leadsAttached.remove(data.getKey());
                defaultDirections.remove(data.getKey());
                batData.remove(data.getKey());
                leadAttachTimes.remove(data.getKey());
            }
        }
    }




    @Override
    public void activate(Player player, int level) {
        if (!batData.containsKey(player)) {
            batData.put(player, new ArrayList<>());
        }
        if (!batData.get(player).isEmpty()) {
            // Second activation: attach leads and apply velocity
            double proposedHealth = player.getHealth() -  getHealthReduction(level);

            if (proposedHealth <= 0) {
                UtilMessage.simpleMessage(player, getClassType().getName(), "You do not have enough health to use <green>%s %d<gray>", getName(), level);
                return;
            }
            player.setHealth(proposedHealth);
            leadsAttached.put(player, true);
            leadAttachTimes.put(player, System.currentTimeMillis());
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                if (bat != null && !bat.isDead()) {
                    bat.setLeashHolder(player);
                    applyBatPull();
                }
            }
        } else {
            // First activation: spawn bats without leads
            leadsAttached.put(player, false);
            defaultDirections.put(player, player.getLocation().getDirection());
            ArrayList<BatData> bats = new ArrayList<>();

            ArrayList<Location> points = generateFilledCirclePoints(player.getLocation().add(0, 2, 0), player.getLocation().getDirection(), 2, getNumBats(level));

            for (Location point : points) {
                Bat bat = player.getWorld().spawn(point, Bat.class);
                bat.setHealth(1);
                bat.setMetadata("PlayerSpawned", new FixedMetadataValue(champions, true));
                bat.setVelocity(player.getLocation().getDirection().multiply(batSpeed));
                bats.add(new BatData(bat, System.currentTimeMillis(), player.getLocation()));
            }

            batData.put(player, bats);
            active.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void handleBatDamage(CustomDamageEvent event) {
        if (!(event.getDamagee() instanceof Bat bat)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        for (ArrayList<BatData> bats : batData.values()) {
            for (BatData batData : bats) {
                if (batData.getBat().equals(bat)) {
                    System.out.println("here");
                    event.setCancelled(true);

                    doParticles(bat.getLocation());
                    bat.remove();
                }
            }
        }
    }

    public void doParticles(Location location){
        Particle.SQUID_INK.builder()
                .location(location)
                .receivers(30)
                .extra(0)
                .count(10)
                .offset(0, 0, 0)
                .spawn();
    }

    @Override
    public void loadSkillConfig() {
        batLifespan = getConfig("batLifespan", 1.5, Double.class);
        batLifespanIncreasePerLevel = getConfig("batLifespanIncreasePerLevel", 0.0, Double.class);
        batDamage = getConfig("batDamage", 1.0, Double.class);
        numberOfBats = getConfig("numberOfBats", 10, Integer.class);
        pullSpeed = getConfig("pullSpeed", 2.0, Double.class);
        batSpeed = getConfig("batSpeed", 0.6, Double.class);
        numberOfBatsIncreasePerLevel = getConfig("numberOfBatsIncreasePerLevel", 5, Integer.class);
        baseHealthReduction = getConfig("baseHealthReduction", 2.0, Double.class);
        healthReductionDecreasePerLevel = getConfig("healthReductionDecreasePerLevel", 0.0, Double.class);
        fallDamageLimit = getConfig("fallDamageLimit", 10.0, Double.class);
        knockbackStrength = getConfig("knockbackStrength", 0.8, Double.class);
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Data
    private static class BatData {
        private final Bat bat;
        private final long timer;
        private final Location loc;
    }

    private ArrayList<Location> generateFilledCirclePoints(Location center, Vector direction, double radius, int points) {
        ArrayList<Location> circlePoints = new ArrayList<>();
        // Move the center a few blocks in the direction the player is looking
        center = center.add(direction.clone().normalize().multiply(3));
        Vector ortho1 = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector ortho2 = direction.clone().crossProduct(ortho1).normalize();

        for (int i = 0; i < points; i++) {
            double t = 2 * Math.PI * random.nextDouble();
            double r = radius * Math.sqrt(random.nextDouble());
            double x = r * Math.cos(t);
            double y = r * Math.sin(t);

            Location point = center.clone().add(ortho1.clone().multiply(x)).add(ortho2.clone().multiply(y));
            circlePoints.add(point);
        }
        return circlePoints;
    }

}
