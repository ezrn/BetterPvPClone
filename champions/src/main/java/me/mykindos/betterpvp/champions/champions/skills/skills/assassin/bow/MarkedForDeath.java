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
import me.mykindos.betterpvp.core.effects.EffectTypes;
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
    private int markedStrengthIncreasePerLevel;
    private int markedStrength;

    @Inject
    public MarkedForDeath(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
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
                "instance of damage to be increased by <val>" + (2 * level) + "</val>",
                "and making them glow",
                "",
                "If the marked player dies within this duration,",
                "you will regain <stat>5</stat> health points",
                "",
                "Cooldown: <val>" + getCooldown(level)
        };
    }

    public int getAmplifier(int level){
        return markedStrength * ((level - 1) * markedStrengthIncreasePerLevel);
    }

    public double getDuration(int level) {
        return baseDuration + ((level - 1) * durationIncreasePerLevel);
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
        championsManager.getEffects().addEffect(damagee, damager, EffectTypes.MARKED, getAmplifier(level), duration);
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
        durationIncreasePerLevel = getConfig("durationIncreasePerLevel", 2.0, Double.class);
        markedStrength = getConfig("markedStrength", 1, Integer.class);
        markedStrengthIncreasePerLevel = getConfig("markedStrengthIncreasePerLevel", 1, Integer.class);
    }
}
