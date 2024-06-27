package me.mykindos.betterpvp.champions.champions.skills.skills.assassin.bow;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.PrepareArrowSkill;
import me.mykindos.betterpvp.core.combat.damagelog.DamageLog;
import me.mykindos.betterpvp.core.combat.damagelog.DamageLogManager;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
@BPvPListener
public class MarkedForDeath extends PrepareArrowSkill {

    private double baseDuration;
    private double durationIncreasePerLevel;
    private double extraDamage;
    private double extraDamageIncreasePerLevel;
    private final DamageLogManager damageLogManager;

    private final Map<UUID, Long> markedPlayers = new HashMap<>();
    private final Map<UUID, Double> damageModifiers = new HashMap<>();

    @Inject
    public MarkedForDeath(Champions champions, ChampionsManager championsManager, DamageLogManager damageLogManager) {
        super(champions, championsManager);
        this.damageLogManager = damageLogManager;
    }

    @Override
    public String getName() {
        return "Marked for Death";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Left click with a Bow to prepare",
                "",
                "Your next arrow will mark players for death",
                "for <val>" + getDuration(level) + "</val> seconds, causing their next",
                "instance of damage to be increased by <val>" + getExtraDamage(level) + "</val>",
                "and making them glow",
                "",
                "If the marked player dies within this duration,",
                "you will regain <stat>5</stat> health points",
                "",
                "Cooldown: <val>" + getCooldown(level)
        };
    }

    public double getDuration(int level) {
        return baseDuration + ((level - 1) * durationIncreasePerLevel);
    }

    public double getExtraDamage(int level) {
        return extraDamage + ((level - 1) * extraDamageIncreasePerLevel);
    }

    @Override
    public Role getClassType() {
        return Role.ASSASSIN;
    }

    @Override
    public SkillType getType() {
        return SkillType.BOW;
    }

    @Override
    public void onHit(Player damager, LivingEntity target, int level) {
        if (!(target instanceof Player damagee)) return;

        UtilMessage.simpleMessage(damager, getClassType().getName(), "You hit <yellow>%s</yellow> with <green>%s %s</green>.", target.getName(), getName(), level);
        UtilMessage.simpleMessage(damagee, getClassType().getName(), "<alt2>%s</alt2> hit you with <alt>%s %s</alt>.", damager.getName(), getName(), level);

        long duration = (long) (getDuration(level) * 1000L);
        markedPlayers.put(damagee.getUniqueId(), System.currentTimeMillis() + duration);
        damageModifiers.put(damagee.getUniqueId(), getExtraDamage(level));

        show(damager, Collections.singletonList(damager), damagee);
        champions.getServer().getScheduler().runTaskLater(champions, () -> {
            markedPlayers.remove(damagee.getUniqueId());
            hide(damager, Collections.singletonList(damager), damagee);
        }, duration / 50); // Convert milliseconds to ticks
    }

    @Override
    public void displayTrail(Location location) {
        new ParticleBuilder(Particle.SPELL_MOB)
                .location(location)
                .count(1)
                .offset(0.1, 0.1, 0.1)
                .extra(0)
                .receivers(60)
                .spawn();
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }

    @Override
    public void activate(Player player, int level) {
        active.add(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 2.5F, 2.0F);
    }

    @Override
    public Action[] getActions() {
        return SkillActions.LEFT_CLICK;
    }

    @Override
    public void loadSkillConfig() {
        baseDuration = getConfig("baseDuration", 4.0, Double.class);
        durationIncreasePerLevel = getConfig("durationIncreasePerLevel", 1.0, Double.class);
        extraDamage = getConfig("extraDamage", 2.0, Double.class);
        extraDamageIncreasePerLevel = getConfig("extraDamageIncreasePerLevel", 2.0, Double.class);
    }

    @EventHandler
    public void onCustomDamage(CustomDamageEvent event) {
        if (event.getDamager() == null) return;
        if (!(event.getDamagee() instanceof Player player)) return;

        UUID playerUUID = player.getUniqueId();
        if (markedPlayers.containsKey(playerUUID) && damageModifiers.containsKey(playerUUID)) {
            long endTime = markedPlayers.get(playerUUID);
            if (System.currentTimeMillis() <= endTime) {
                double extraDamage = damageModifiers.get(playerUUID);
                event.setDamage(event.getDamage() + extraDamage);
                damageModifiers.remove(playerUUID);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();
        UUID deceasedUUID = deceased.getUniqueId();
        if (markedPlayers.containsKey(deceasedUUID)) {

            DamageLog lastDamager = damageLogManager.getLastDamager(event.getEntity());
            if (lastDamager == null) return;
            if (!(lastDamager.getDamager() instanceof Player killer)) return;
            int level = getLevel(killer);
            if (level <= 0) return;

            long endTime = markedPlayers.get(deceasedUUID);
            if (System.currentTimeMillis() <= endTime) {
                killer.setHealth(Math.min(killer.getHealth() + 5.0, killer.getMaxHealth()));
                UtilMessage.simpleMessage(killer, "You have regained <alt>5<alt> health for killing a marked player.");
                markedPlayers.remove(deceasedUUID);
                damageModifiers.remove(deceasedUUID);
            }
        }
    }


    private void show(Player player, List<Player> allies, Player target) {
        UtilPlayer.setGlowing(player, target, true);
        for (Player ally : allies) {
            UtilPlayer.setGlowing(ally, target, true);
        }
    }

    private void hide(Player player, List<Player> allies, Player target) {
        UtilPlayer.setGlowing(player, target, false);
        for (Player ally : allies) {
            UtilPlayer.setGlowing(ally, target, false);
        }
    }
}
