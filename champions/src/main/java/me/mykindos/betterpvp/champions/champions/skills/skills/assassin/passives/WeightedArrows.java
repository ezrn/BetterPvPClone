package me.mykindos.betterpvp.champions.champions.skills.skills.assassin.passives;


import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillWeapons;
import me.mykindos.betterpvp.champions.champions.skills.types.PassiveSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.components.champions.events.PlayerCanUseSkillEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;

@Singleton
@BPvPListener
public class WeightedArrows extends Skill implements PassiveSkill, Listener {


    @Inject
    public WeightedArrows(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Weighted Arrows";
    }

    @Override
    public String[] getDescription(int level) {

        return new String[]{
                "The closer you are to your target, the more damage your arrows deal",
                "Maximum Damage: <val>" + (5 + level) + "0%"};
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if(!(event.getProjectile() instanceof Arrow arrow)) return;

        int level = getLevel(player);
        if(level > 0) {
            PlayerCanUseSkillEvent skillEvent = UtilServer.callEvent(new PlayerCanUseSkillEvent(player, this));
            if(!skillEvent.isCancelled()) {
                arrows.put(arrow, arrow.getLocation());
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(CustomDamageEvent event) {
        if(!(event.getProjectile() instanceof Arrow arrow)) return;
        if(!(event.getDamager() instanceof Player damager)) return;
        if(!arrows.containsKey(arrow)) return;

        Location loc = arrows.remove(arrow);
        double length = UtilMath.offset(loc, event.getDamagee().getLocation());
        double damage = Math.min(baseDamage + getLevel(damager), length / 3.0 - 4);

        event.setDamage(event.getDamage() + (damage));
        event.setReason(getName() + (length > deathMessageThreshold ? " <gray>from <green>" + (int) length + "<gray> blocks" : ""));

    }

    @Override
    public Role getClassType() {
        return Role.ASSASSIN;
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE_A;
    }


}