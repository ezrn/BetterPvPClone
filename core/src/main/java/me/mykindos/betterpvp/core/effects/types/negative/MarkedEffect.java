package me.mykindos.betterpvp.core.effects.types.negative;

import me.mykindos.betterpvp.core.combat.damagelog.DamageLog;
import me.mykindos.betterpvp.core.combat.damagelog.DamageLogManager;
import me.mykindos.betterpvp.core.effects.Effect;
import me.mykindos.betterpvp.core.effects.VanillaEffectType;
import me.mykindos.betterpvp.core.effects.events.EffectExpireEvent;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
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
    private final double baseExtraDamage = 3.0;
    private final double extraDamageIncreasePerLevel = 3.0;

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

    @Override
    public void onExpire(LivingEntity livingEntity, Effect effect) {
        if (livingEntity instanceof Player player) {
            hide(player, List.of(player), player);
            markedPlayers.remove(player.getUniqueId());
        }
    }

    @Override
    public String getDescription(int level) {
        return "<white>Marked " + UtilFormat.getRomanNumeral(level) + " <reset>makes the target glow and increases the first instance of damage taken by <val>" + baseExtraDamage + (extraDamageIncreasePerLevel * level) + "</val>";
    }
}
