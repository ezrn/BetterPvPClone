package me.mykindos.betterpvp.champions.champions.skills.skills.assassin.axe;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class Dash extends Skill implements InteractSkill, CooldownSkill, Listener {

    private final WeakHashMap<Player, Long> active = new WeakHashMap<>();

    @Inject
    public Dash(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Dash";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{
                "Right click with a axe to activate",
                "",
                "Dash forward, dealing <val>" + (4 + (level)) + "</val> damage to everyone you pass through",
                "Every enemy you dash through will lower the cooldown by <val>" + (level * 0.5) + "</val> seconds",
                "Cooldown: <val>" + getCooldown(level)
        };
    }

    @UpdateEvent
    public void checkCollision() {

        Iterator<Map.Entry<Player, Long>> it = active.entrySet().iterator();
        while (it.hasNext()) {

            Map.Entry<Player, Long> next = it.next();
            Player player = next.getKey();
            if (player.isDead()) {
                it.remove();
                continue;
            }

            if(isCollision(player)) {
                it.remove();
                continue;
            }
        }
    }

    public boolean isCollision(Player player) {
        for (Player other : UtilPlayer.getNearbyEnemies(player, player.getLocation(), 1.5)) {
            if (other.isDead()) continue;

            if (UtilMath.offset(player, other) < 1.5) {

                doDash(player, other);
                return true;

            }
        }
        return false;
    }

    public void doDash(Player player, Player target) {
        UtilMessage.message(player, getClassType().getName(), "You dashed through <val>" + target.getName());

        UtilDamage.doCustomDamage(new CustomDamageEvent(target, player, null, EntityDamageEvent.DamageCause.CUSTOM, (4 + (player.getLevel())), false, "Dash"));
        target.getWorld().playEffect(target.getLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);

        decreaseCooldown(player.getLevel());
    }

    @Override
    public void activate(Player player, int leel) {
        Vector vec = player.getLocation().getDirection();
        vec = player.getLocation().getDirection().multiply(10);
        player.setVelocity(vec);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0F, 1.2F);
        active.put(player, System.currentTimeMillis());

        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation(), 1, null);
            }
        }.runTaskTimer(champions, 0L, 1L);
        new BukkitRunnable() {
            @Override
            public void run() {
                particleTask.cancel();
                player.setVelocity(new Vector(0,0,0));
            }
        }.runTaskLater(champions, 4L);
    }

    public void decreaseCooldown(int level) {
        double reduction = level * 0.5;
        if ((cooldown - reduction) < 0) {
            cooldown = 0;
        } else {
            cooldown -= reduction;
        }
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - ((level - 1));
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
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Override
    public boolean canUseSlowed() {
        return false;
    }
}
