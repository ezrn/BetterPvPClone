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
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
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

    private double batLifespanIncreasePerLevel;
    private double batLifespan;
    private double batDamage;
    private int numberOfBats;
    private double pullSpeed;
    private double batSpeed;
    private double minBatSpeed;
    private int numberOfBatsIncreasePerLevel;
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
                "Release a swarm of " + getValueString(this::getNumBats, level) + "bats which will",
                "fly for " + getValueString(this::getBatLifespan, level) + "seconds, damaging, <effect>Shocking</effect>",
                "and knocking back any enemies they hit",
                "",
                "Right click again to attach yourself to",
                "the bats, pulling you along with them.",
                "",
                "Taking damage will remove your bats",
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
            Vector direction = leadsAttached.get(player) ? player.getLocation().getDirection() : defaultDirections.get(player);
            if (direction == null) continue;
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                bat.setVelocity(direction.clone().multiply(batSpeed)); // Use current or default direction with configured bat speed

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
                        final VelocityData velocityData = new VelocityData(vector, 1.2d, 0.2d, 7.5d, true);
                        UtilVelocity.velocity(other, player, velocityData);

                        bat.getWorld().playSound(bat.getLocation(), Sound.ENTITY_BAT_HURT, 0.1F, 0.7F);
                    }

                    bat.remove();
                }
            }
        }
    }

    @UpdateEvent
    public void applyBatPull() {

        for (Player player : leadsAttached.keySet()) {
            if (leadsAttached.get(player)) {
                ArrayList<BatData> bats = batData.get(player);
                if (bats != null && !bats.isEmpty()) {
                    float pitch = player.getLocation().getPitch();
                    System.out.println("pitch: " + pitch);

                    double adjustedPullSpeed = pullSpeed;
                    if (pitch < 0) {
                        double pitchFactor = pitch / -90; // Calculate factor based on pitch, 0 at 0 pitch, 1 at -90 pitch
                        System.out.println("pitchFactor: " + pitchFactor);
                        adjustedPullSpeed = minBatSpeed + (pullSpeed - minBatSpeed) * (1 - pitchFactor); // Adjust pull speed
                    }

                    System.out.println("adjustedPullSpeed: " + adjustedPullSpeed);

                    for (BatData batData : bats) {
                        Bat bat = batData.getBat();
                        Vector direction = bat.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        player.setVelocity(direction.multiply(adjustedPullSpeed)); // Use adjusted pull speed
                    }
                }
            }
        }
    }





    @UpdateEvent(delay = 500)
    public void destroyBats() {
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
            }

            if (data.getValue().isEmpty()) {
                iterator.remove();
                leadsAttached.remove(data.getKey());
                defaultDirections.remove(data.getKey());
                batData.remove(data.getKey());
            }
        }
    }

    @Override
    public void activate(Player player, int level) {
        if (!batData.containsKey(player)) {
            batData.put(player, new ArrayList<>());
        }

        if (leadsAttached.containsKey(player) && leadsAttached.get(player)) {
            // Detach leads and stop applying velocity
            leadsAttached.put(player, false);
            defaultDirections.put(player, player.getLocation().getDirection());
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                if (bat != null && !bat.isDead()) {
                    bat.setLeashHolder(null);
                }
            }
        } else if (!batData.get(player).isEmpty()) {
            // Second activation: attach leads and apply velocity
            leadsAttached.put(player, true);
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                if (bat != null && !bat.isDead()) {
                    bat.setLeashHolder(player);
                }
            }
        } else {
            // First activation: spawn bats without leads
            leadsAttached.put(player, false);
            defaultDirections.put(player, player.getLocation().getDirection());
            ArrayList<BatData> bats = new ArrayList<>();

            ArrayList<Location> points = generateFilledCirclePoints(player.getLocation(), player.getLocation().getDirection(), 2, numberOfBats);

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
        if (!(event.getDamager() instanceof Player)) return;
        for (ArrayList<BatData> bats : batData.values()) {
            for (BatData batData : bats) {
                if (batData.getBat().equals(bat)) {
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

    @EventHandler
    public void removeBatsOnDamage(CustomDamageEvent event) {
        if (!(event.getDamagee() instanceof Player player)) return;

        // Check if the player has bats
        if (batData.containsKey(player)) {
            ArrayList<BatData> bats = batData.get(player);

            // Remove all bats
            for (BatData batData : bats) {
                Bat bat = batData.getBat();
                if (bat != null && !bat.isDead()) {

                    doParticles(bat.getLocation());
                    bat.remove();
                }
            }

            // Clear the player's bat data
            batData.remove(player);
            leadsAttached.remove(player);
            defaultDirections.remove(player);
        }
    }

    @Override
    public void loadSkillConfig() {
        batLifespan = getConfig("batLifespan", 3.0, Double.class);
        batLifespanIncreasePerLevel = getConfig("batLifespanIncreasePerLevel", 0.5, Double.class);
        batDamage = getConfig("batDamage", 3.0, Double.class);
        numberOfBats = getConfig("numberOfBats", 10, Integer.class);
        pullSpeed = getConfig("pullSpeed", 0.5, Double.class);
        batSpeed = getConfig("batSpeed", 0.6, Double.class);
        minBatSpeed = getConfig("minBatSpeed", 0.3, Double.class);
        numberOfBatsIncreasePerLevel = getConfig("numberOfBatsIncreasePerLevel", 5, Integer.class);
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
        direction = direction.normalize();
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
