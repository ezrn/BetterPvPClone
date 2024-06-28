package me.mykindos.betterpvp.core.effects.types.negative;

import me.mykindos.betterpvp.core.combat.damagelog.DamageLog;
import me.mykindos.betterpvp.core.combat.damagelog.DamageLogManager;
import me.mykindos.betterpvp.core.effects.Effect;
import me.mykindos.betterpvp.core.effects.VanillaEffectType;
import me.mykindos.betterpvp.core.effects.events.EffectExpireEvent;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MarkedEffect extends VanillaEffectType {

    private final Map<UUID, Long> markedPlayers = new HashMap<>();
    private final DamageLogManager damageLogManager;
    private final double baseExtraDamage = 2.0;
    private final double extraDamageIncreasePerLevel = 2.0;
    private final double healthRegenOnKill = 5.0;

    public MarkedEffect(DamageLogManager damageLogManager) {
        this.damageLogManager = damageLogManager;
    }

    @Override
    public String getName() {
        return "Marked";
    }

    @Override
    public boolean isNegative() {
        return true;
    }

    @Override
    public PotionEffectType getVanillaPotionType() {
        return PotionEffectType.UNLUCK;
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

    @Override
    public void onReceive(LivingEntity livingEntity, Effect effect) {
        if (livingEntity instanceof Player player) {
            show(player, List.of(player), player);
            markedPlayers.put(player.getUniqueId(), effect.getLength());
        }
    }

    public void onDamage(EntityDamageByEntityEvent event, Effect effect) {
        if (event.getEntity() instanceof Player damagee) {
            double extraDamage = baseExtraDamage + (extraDamageIncreasePerLevel * effect.getAmplifier());
            event.setDamage(event.getDamage() + extraDamage);
            markedPlayers.remove(damagee.getUniqueId());
        }
    }

    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deceased = event.getEntity();

        DamageLog lastDamager = damageLogManager.getLastDamager(event.getEntity());
        if (lastDamager == null) return;
        if (!(lastDamager.getDamager() instanceof Player killer)) return;

        killer.setHealth(Math.min(killer.getHealth() + healthRegenOnKill, killer.getMaxHealth()));
        UtilMessage.simpleMessage(killer, "You have regained <alt>5<alt> health for killing <alt2>" + deceased.getName() + "</alt2>.");
    }

    @Override
    public void onExpire(LivingEntity livingEntity, Effect effect) {
        if (livingEntity instanceof Player player) {
            hide(player, List.of(player), player);
            markedPlayers.remove(player.getUniqueId());
        }
    }
}
