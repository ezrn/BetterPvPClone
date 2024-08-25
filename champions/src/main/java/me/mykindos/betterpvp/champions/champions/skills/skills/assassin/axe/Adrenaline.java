package me.mykindos.betterpvp.champions.champions.skills.skills.assassin.axe;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.BuffSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.DefensiveSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.MovementSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Singleton
@BPvPListener
public class Adrenaline extends Skill implements InteractSkill, CooldownSkill, Listener, BuffSkill, MovementSkill, DefensiveSkill {

    private final HashMap<UUID, Long> active = new HashMap<>();

    private double baseDuration;

    private double durationIncreasePerLevel;


    private int speedStrength;

    @Inject
    public Adrenaline(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Adrenaline";
    }

    @Override
    public String[] getDescription(int level) {

        return new String[]{
                "Right click with an Axe to activate",
                "",
                "Sprint with great agility, gaining",
                "<effect>Speed " + UtilFormat.getRomanNumeral(speedStrength) + "</effect> for " + getValueString(this::getDuration, level) + " seconds and ",
                "being immune to all effects",
                "",
                "Cooldown: " + getValueString(this::getCooldown, level)
        };
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
        return SkillType.AXE;
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1) * cooldownDecreasePerLevel);
    }


    @UpdateEvent
    public void onUpdate() {
        Iterator<Map.Entry<UUID, Long>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
            } else {
                spawnSkillParticles(player);
                if (entry.getValue() - System.currentTimeMillis() <= 0) {
                    deactivate(player);
                    iterator.remove();
                }
            }
        }
    }

    @UpdateEvent
    private void spawnSkillParticles(Player player) {
        Location loc = player.getLocation();

        Random random = UtilMath.RANDOM;
        double x = loc.getX() + (random.nextDouble() - 0.5) * 0.5;
        double y = loc.getY() + (1 + (random.nextDouble() - 0.5) * 0.9);
        double z = loc.getZ() + (random.nextDouble() - 0.5) * 0.5;
        Location particleLoc = new Location(loc.getWorld(), x, y, z);
        new ParticleBuilder(Particle.WHITE_SMOKE)
                .location(particleLoc)
                .count(1)
                .offset(0.1, 0.1, 0.1)
                .extra(0)
                .receivers(60)
                .spawn();
    }


    @Override
    public void activate(Player player, int level) {
        if (!active.containsKey(player.getUniqueId())) {
            championsManager.getEffects().addEffect(player, EffectTypes.SPEED, getName(), speedStrength, (long) (getDuration(level) * 1000));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_DEATH, 0.5F, 0.5F);
            active.put(player.getUniqueId(), (long) (System.currentTimeMillis() + (getDuration(level) * 1000L)));
        }
    }

    public void deactivate(Player player) {
        UtilMessage.message(player, getClassType().getName(), UtilMessage.deserialize("<green>%s %s</green> has ended.", getName(), getLevel(player)));
        championsManager.getEffects().removeEffect(player, EffectTypes.SPEED, getName());
        championsManager.getEffects().addEffect(player, EffectTypes.SPEED, getName(), 2, 2000);

    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!active.contains(event.getEntity().getUniqueId())) return;

        var effect = event.getNewEffect();
        if (effect == null) return;

        if (effect.getType() == PotionEffectType.SLOWNESS || effect.getType() == PotionEffectType.LEVITATION) {
            event.setCancelled(true);
        }
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Override
    public void loadSkillConfig() {
        baseDuration = getConfig("baseDuration", 0.3, Double.class);
        durationIncreasePerLevel = getConfig("durationIncreasePerLevel", 0.1, Double.class);
        speedStrength = getConfig("speedStrength", 20, Integer.class);
    }
}
