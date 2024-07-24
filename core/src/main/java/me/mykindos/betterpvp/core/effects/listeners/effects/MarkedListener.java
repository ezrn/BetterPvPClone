package me.mykindos.betterpvp.core.effects.listeners.effects;

import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.effects.Effect;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.effects.EffectType;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.effects.events.EffectExpireEvent;
import me.mykindos.betterpvp.core.effects.events.EffectReceiveEvent;
import me.mykindos.betterpvp.core.effects.types.negative.MarkedEffect;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@BPvPListener
@Singleton
public class MarkedListener implements Listener {

    private final double baseExtraDamage = 3.0;
    private final double extraDamageIncreasePerLevel = 3.0;
    private final EffectManager effectManager;
    private final Map<LivingEntity, Long> markedEntities = new HashMap<>();

    @Inject
    public MarkedListener(EffectManager effectManager) {
        this.effectManager = effectManager;
    }

    private void show(Player player, List<Player> allies, LivingEntity target) {
        System.out.println("showing player");
        UtilPlayer.setGlowing(player, target, true);
        for (Player ally : allies) {
            UtilPlayer.setGlowing(ally, target, true);
        }
    }

    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        System.out.println("running on damage");
        System.out.println(markedEntities);
        if(!markedEntities.containsKey(event.getDamagee())) return;
        System.out.println("got past markedEntities check");
        Optional<Effect> effectOptional = effectManager.getEffect(event.getDamagee(), EffectTypes.MARKED);
        System.out.println("effectOptional: " + effectOptional);
        effectOptional.ifPresent(effect -> {
            event.getDamagee().getWorld().playSound(event.getDamagee().getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0F, 0.5F);
            System.out.println("got in here");
            double extraDamage = baseExtraDamage + (extraDamageIncreasePerLevel * effect.getAmplifier());
            event.setDamage(event.getDamage() + extraDamage);
            System.out.println("increasing damage");
            markedEntities.remove(event.getDamagee());
        });
    }

    private void hide(Player player, List<Player> allies, LivingEntity target) {
        UtilPlayer.setGlowing(player, target, false);
        for (Player ally : allies) {
            UtilPlayer.setGlowing(ally, target, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectApply(EffectReceiveEvent event){
        Effect effect = event.getEffect();
        if(effect.getEffectType().equals(EffectTypes.MARKED)) {
            LivingEntity livingEntity = event.getTarget();
            System.out.println("applier: " + effect.getApplier());
            if (!(effect.getApplier() instanceof Player player)) return;
            System.out.println("receiving potion effect");
            show(player, List.of(player), livingEntity);
            markedEntities.put(livingEntity, effect.getLength());
            System.out.println(markedEntities);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectExpire(EffectExpireEvent event){
        Optional<Effect> effectOptional = effectManager.getEffect(event.getTarget(), EffectTypes.MARKED);
        System.out.println("effectOptional: " + effectOptional);
        effectOptional.ifPresent(effect -> {
            LivingEntity livingEntity = event.getTarget();
            if (!(effect.getApplier() instanceof Player player)) return;
            System.out.println("removing potion effect");
            hide(player, List.of(player), livingEntity);
            markedEntities.remove(livingEntity);
        });
    }

}
