package me.mykindos.betterpvp.champions.champions.skills.skills.ranger.bow;

import com.destroystokyo.paper.ParticleBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.ChargeData;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import me.mykindos.betterpvp.core.utilities.model.display.DisplayComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class Overcharge extends Skill implements InteractSkill, Listener {

    private final WeakHashMap<Player, ChargeData> charging = new WeakHashMap<>();
    private final WeakHashMap<Arrow, ArrowData> bonus = new WeakHashMap<>();
    private final Map<Player, Long> activationTimes = new WeakHashMap<>();

    private final DisplayComponent actionBarComponent = ChargeData.getActionBar(this,
            charging,
            gamer -> true);

    private double baseMaxDamage;
    private double maxDamageIncreasePerLevel;
    private double baseCharge;
    private double chargeIncreasePerLevel;

    @Inject
    public Overcharge(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Overcharge";
    }

    @Override
    public String[] getDescription(int level) {

        return new String[]{
                "Hold right click with a Bow to use",
                "",
                "Draw back harder on your bow, gaining <val>" + getChargePerSecond(level),
                "charge per second",
                "",
                "At <stat>100%</stat> charge you will deal <val>" + getMaxDamage(level) + "</val> extra damage",
        };
    }


    public double getMaxDamage(int level) {
        return baseMaxDamage + ((level-1) * maxDamageIncreasePerLevel);
    }

    @Override
    public Role getClassType() {
        return Role.RANGER;
    }


    @Override
    public void trackPlayer(Player player, Gamer gamer) {
        gamer.getActionBar().add(900, actionBarComponent);
    }

    @Override
    public void invalidatePlayer(Player player, Gamer gamer) {
        gamer.getActionBar().remove(actionBarComponent);
    }

    @Override
    public void activate(Player player, int level) {
        final ChargeData chargeData = new ChargeData((float) getChargePerSecond(level) / 100);
        charging.put(player, chargeData);
        activationTimes.put(player, System.currentTimeMillis()); // Record activation time
    }

    private double getChargePerSecond(int level) {
        return baseCharge + (chargeIncreasePerLevel * (level - 1));
    }

    @EventHandler
    public void onPlayerShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        ChargeData chargeData = charging.remove(player);
        Bukkit.getLogger().info("removing player from charging");
        if (chargeData != null) {
            double chargePercentage = chargeData.getCharge();
            int level = getLevel(player); // Assuming there's a method to get the player's level
            double extraDamage = getMaxDamage(level) * chargePercentage;
            bonus.put(arrow, new ArrowData(extraDamage, level));
            Bukkit.getLogger().info("[Overcharge] Player " + player.getName() + " shot an arrow with extra damage: " + extraDamage);
        } else {
            Bukkit.getLogger().info("[Overcharge] Player " + player.getName() + " shot an arrow without charging.");
        }
    }


    @UpdateEvent
    public void createRedDustParticles() {
        List<Arrow> toRemove = new ArrayList<>();

        bonus.forEach((arrow, arrowData) -> {
            if (arrow.isValid() && !arrow.isDead() && !arrow.isOnGround()) {
                double baseSize = 0.3;
                double finalSize = baseSize * arrowData.extraDamage;

                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 0, 0), (float)finalSize);
                arrow.getWorld().spawnParticle(Particle.REDSTONE, arrow.getLocation(), 1, 0.01, 0.01, 0.01, 0, redDust);
            } else {
                toRemove.add(arrow);
            }
        });

        toRemove.forEach(bonus::remove);
    }



    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(CustomDamageEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        ArrowData arrowData = bonus.remove(arrow);
        if (arrowData != null) {
            double extraDamage = arrowData.extraDamage;
            event.setDamage(event.getDamage() + extraDamage);
            Bukkit.getLogger().info("[Overcharge] Applying extra damage: " + extraDamage);
            event.addReason(getName());
        }
    }

    @UpdateEvent
    public void updateCharge() {
        List<Player> toRemove = new ArrayList<>();
        Iterator<Player> iterator = charging.keySet().iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            ChargeData charge = charging.get(player);
            Long activationTime = activationTimes.get(player);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            int level = getLevel(player);
            if (level <= 0) {
                Bukkit.getLogger().info("level is < 0");
                iterator.remove();
                continue;
            }

            if (!checkHolding(player)) {
                toRemove.add(player);
                iterator.remove();
                continue;
            }

            if (UtilBlock.isInLiquid(player)) {
                iterator.remove();
                continue;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - activationTime >= 250) {
                if (!checkHolding(player)) {
                    iterator.remove();
                    continue;
                }

                charge.tick();
                charge.tickSound(player);
            }

            for (Player p : toRemove) {
                charging.remove(p);
            }
        }
    }

    /*public boolean checkHolding(Player player){
        Material mainhand = player.getInventory().getItemInMainHand().getType();
        if (mainhand == Material.BOW && player.getActiveItem().getType() == Material.AIR) {
            return true;
        }

        if (mainhand == Material.CROSSBOW && player.getActiveItem().getType() == Material.AIR) {
            CrossbowMeta meta = (CrossbowMeta) player.getInventory().getItemInMainHand().getItemMeta();
            if (!meta.hasChargedProjectiles()) {
                return true;
            }
            return true;
        }
        return false;
    }*/

    public boolean checkHolding(Player player) {
        Material mainhand = player.getInventory().getItemInMainHand().getType();
        return mainhand == Material.BOW || mainhand == Material.CROSSBOW;
    }


    @Override
    public SkillType getType() {
        return SkillType.BOW;
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }


    @Data
    @AllArgsConstructor
    private static class Shoot {
        private final ChargeData data;
        private final int level;

    }

    private static class ArrowData {
        final double extraDamage;
        final int level;

        ArrowData(double extraDamage, int level) {
            this.extraDamage = extraDamage;
            this.level = level;
        }
    }

    @Override
    public boolean displayWhenUsed() {
        return false;
    }

    public void loadSkillConfig() {
        baseMaxDamage = getConfig("baseMaxDamage", 2.0, Double.class);
        maxDamageIncreasePerLevel = getConfig("maxDamageIncreasePerLevel", 1.0, Double.class);
        baseCharge = getConfig("baseCharge", 40.0, Double.class);
        chargeIncreasePerLevel = getConfig("chargeIncreasePerLevel", 10.0, Double.class);
    }
}
