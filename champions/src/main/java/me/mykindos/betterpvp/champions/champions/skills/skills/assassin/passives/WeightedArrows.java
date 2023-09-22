package me.mykindos.betterpvp.champions.champions.skills.skills.assassin.passives;


import java.util.Iterator;
import java.util.WeakHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.types.PassiveSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.components.champions.events.PlayerCanUseSkillEvent;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilMath;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;

@Singleton
@BPvPListener
public class WeightedArrows extends Skill implements PassiveSkill, Listener {

    private final WeakHashMap<Arrow, Location> arrows = new WeakHashMap<>();

    private double baseDamage;
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
                "Maximum Damage: <val>" + (3 + (2 * level))};
    }

    @UpdateEvent
    public void update() {
        Iterator<Arrow> it = arrows.keySet().iterator();
        while (it.hasNext()) {
            Arrow next = it.next();
            if (next == null) {
                it.remove();
            } else if (next.isDead()) {
                it.remove();
            } else {
                Location location = next.getLocation().add(new Vector(0, 0.25, 0));
                Particle.SCULK_CHARGE_POP.builder().location(location).receivers(60).extra(0).spawn();

            }
        }

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
        double damage = Math.min(baseDamage + getLevel(damager), 3.0 / (length+0.1) - 4);

        event.setDamage(event.getDamage() + (damage));
        event.setReason(getName());

    }

    @Override
    public Role getClassType() {
        return Role.ASSASSIN;
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE_A;
    }

    public void loadSkillConfig(){
        baseDamage = getConfig("baseDamage", 13.0, Double.class);
    }
}