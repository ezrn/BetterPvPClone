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
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import me.mykindos.betterpvp.core.utilities.UtilVelocity;
import me.mykindos.betterpvp.core.utilities.events.EntityProperty;
import me.mykindos.betterpvp.core.utilities.math.VelocityData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class Swarm extends ChannelSkill implements CooldownSkill, InteractSkill, Listener {

    private final WeakHashMap<Player, ArrayList<BatData>> batData = new WeakHashMap<>();
    private final WeakHashMap<Player, Boolean> leadsAttached = new WeakHashMap<>();

    private double batLifespan;
    private double batDamage;
    private int numberOfBats;

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
                "Hold right click with an Axe to activate",
                "",
                "Release a swarm of bats which",
                "damage and knock back any enemies",
                "they come in contact with",
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

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }

    @UpdateEvent(delay = 100)
    public void batHit() {
        for (Player player : batData.keySet()) {
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                Vector rand = new Vector((Math.random() - 0.5D) / 3.0D, (Math.random() - 0.5D) / 3.0D, (Math.random() - 0.5D) / 3.0D);
                bat.setVelocity(batData.getLoc().getDirection().clone().multiply(0.5D).add(rand));

                for (var data : UtilEntity.getNearbyEntities(player, bat.getLocation(), 3, EntityProperty.ENEMY)) {
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

                    if (!event.isCancelled()) {
                        Vector vector = bat.getLocation().getDirection();
                        final VelocityData velocityData = new VelocityData(vector, 0.4d, 0.2d, 7.5d, true);
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
                    for (BatData batData : bats) {
                        Bat bat = batData.getBat();
                        Vector direction = bat.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                        player.setVelocity(direction.multiply(0.35));
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
            ListIterator<BatData> batIt = data.getValue().listIterator();
            while (batIt.hasNext()) {
                BatData bat = batIt.next();

                if (bat.getBat() == null || bat.getBat().isDead()) {
                    batIt.remove();
                    continue;
                }

                if (UtilTime.elapsed(bat.getTimer(), (long) batLifespan * 1000)) {
                    bat.getBat().remove();
                    batIt.remove();
                }
            }

            if (data.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void activate(Player player, int level) {
        if (leadsAttached.containsKey(player) && leadsAttached.get(player)) {
            // Detach leads and stop applying velocity
            leadsAttached.put(player, false);
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                bat.setLeashHolder(null);
            }
        } else if (batData.containsKey(player) && !batData.get(player).isEmpty()) {
            // Second activation: attach leads and apply velocity
            leadsAttached.put(player, true);
            for (BatData batData : batData.get(player)) {
                Bat bat = batData.getBat();
                bat.setLeashHolder(player);
            }
        } else {
            // First activation: spawn bats without leads
            leadsAttached.put(player, false);
            ArrayList<BatData> bats = new ArrayList<>();
            for (int i = 0; i < numberOfBats; i++) {
                Bat bat = player.getWorld().spawn(player.getLocation().add(0, 0.5, 0), Bat.class);
                bat.setHealth(1);
                bat.setMetadata("PlayerSpawned", new FixedMetadataValue(champions, true));
                bat.setVelocity(player.getLocation().getDirection().multiply(2));
                bats.add(new BatData(bat, System.currentTimeMillis(), player.getLocation()));
            }
            batData.put(player, bats);
            active.add(player.getUniqueId());
        }
    }

    @Override
    public void loadSkillConfig() {
        batLifespan = getConfig("batLifespan", 5.0, Double.class);
        batDamage = getConfig("batDamage", 1.0, Double.class);
        numberOfBats = getConfig("numberOfBats", 10, Integer.class); // default number of bats to spawn in a wave
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
}
