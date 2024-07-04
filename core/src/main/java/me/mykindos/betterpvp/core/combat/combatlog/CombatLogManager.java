package me.mykindos.betterpvp.core.combat.combatlog;

import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.framework.manager.Manager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;

import java.util.Optional;

@Singleton
public class CombatLogManager extends Manager<CombatLog> {

    public void createCombatLog(Player player, long expiry) {
        addObject(player.getUniqueId(), new CombatLog(player, expiry));
    }

    public Optional<CombatLog> getCombatLogBySkeleton(LivingEntity target) {
        return getObjects().values().stream().filter(cl -> cl.getCombatLogSkeleton().getUniqueId().equals(target.getUniqueId())).findFirst();
    }
}
