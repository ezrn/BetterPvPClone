package me.mykindos.betterpvp.champions.weapons.impl.legendaries;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.weapons.impl.legendaries.data.Line;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.combat.events.PreCustomDamageEvent;
import me.mykindos.betterpvp.core.combat.weapon.types.ChannelWeapon;
import me.mykindos.betterpvp.core.combat.weapon.types.InteractWeapon;
import me.mykindos.betterpvp.core.combat.weapon.types.LegendaryWeapon;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.energy.EnergyHandler;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.*;
import me.mykindos.betterpvp.core.utilities.math.VelocityData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@BPvPListener
public class WindBlade extends ChannelWeapon implements InteractWeapon, LegendaryWeapon, Listener {

    private static final String ABILITY_NAME = "Wind Dash";
    private double windChargeRadius;
    private double windDamage;
    private double velocityStrength;
    private double lineStartDistance;
    private int particleDuration;
    private double windBurstCooldown;
    public int energyCost;
    public int dashEnergyCost;
    private final EnergyHandler energyHandler;
    private final ChampionsManager championsManager;
    private final ClientManager clientManager;
    private final CooldownManager cooldownManager;
    private final Champions champions;
    private final Map<Player, Long> active = new ConcurrentHashMap<>();
    private final Map<Player, List<List<Line>>> playerLines = new HashMap<>();
    private final Map<Player, List<Integer>> playerLineIndices = new HashMap<>();
    private final Set<Player> trackedPlayers = ConcurrentHashMap.newKeySet();

    @Inject
    public WindBlade(Champions champions, EnergyHandler energyHandler, ChampionsManager championsManager, CooldownManager cooldownManager, ClientManager clientManager) {
        super(champions, "wind_blade");
        this.champions = champions;
        this.energyHandler = energyHandler;
        this.clientManager = clientManager;
        this.cooldownManager = cooldownManager;
        this.championsManager = championsManager;
    }

    @Override
    public List<Component> getLore(ItemMeta itemMeta) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Long ago, a race of cloud dwellers", NamedTextColor.WHITE));
        lore.add(Component.text("terrorized the skies. A remnant of", NamedTextColor.WHITE));
        lore.add(Component.text("their tyranny, this airy blade is", NamedTextColor.WHITE));
        lore.add(Component.text("the last surviving memoriam from", NamedTextColor.WHITE));
        lore.add(Component.text("their final battle against the Titans.", NamedTextColor.WHITE));
        lore.add(Component.text(""));
        lore.add(UtilMessage.deserialize("<white>Deals <yellow>%.1f Damage <white>with attack", baseDamage));
        lore.add(UtilMessage.deserialize("<yellow>Right-Click <white>to use <green>%s<green>", ABILITY_NAME));
        lore.add(UtilMessage.deserialize("<yellow>Left-Click <white>to use <green>Wind Burst<green>"));
        lore.add(UtilMessage.deserialize("<yellow>Crouch <white>to use <green>Glide<green>"));
        return lore;
    }

    @Override
    public void activate(Player player) {
        UtilMessage.simpleMessage(player, "Wind Blade", "You used <green>Wind Dash<gray>.");
        Vector vec = player.getLocation().getDirection().normalize().multiply(velocityStrength);
        VelocityData velocityData = new VelocityData(vec, velocityStrength, false, 0.0D, 0.25D, 0.6D, false);
        player.setVelocity(velocityData.getVector());

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= particleDuration) {
                    this.cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 1, 0.5, 0.5, 0.5, 0.1);
                ticks++;
            }
        }.runTaskTimer(champions, 0, 1);

        UtilSound.playSound(player.getWorld(), player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.5F, 2.0F);

        active.put(player, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isHoldingWeapon(player) && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) && championsManager.getCooldowns().use(player, "Wind Burst", windBurstCooldown, false)) {
            if (!energyHandler.use(player, ABILITY_NAME, energyCost, true)) {
                return;
            }
            drawLines(player);
            UtilSound.playSound(player.getWorld(), player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2F, 2.0F);
        }
    }

    private void drawLines(Player player) {
        Location origin = player.getEyeLocation();

        Vector mainDirection = origin.getDirection().normalize();
        Vector leftDirection = mainDirection.clone().rotateAroundY(Math.toRadians(-30)).normalize();
        Vector rightDirection = mainDirection.clone().rotateAroundY(Math.toRadians(30)).normalize();

        Location mainOrigin = origin.clone().add(mainDirection.clone().multiply(lineStartDistance));
        Location leftOrigin = origin.clone().add(leftDirection.clone().multiply(lineStartDistance));
        Location rightOrigin = origin.clone().add(rightDirection.clone().multiply(lineStartDistance));

        List<Location> mainLine = getLinePoints(mainOrigin, mainDirection);
        List<Location> leftLine = getLinePoints(leftOrigin, leftDirection);
        List<Location> rightLine = getLinePoints(rightOrigin, rightDirection);

        playerLines.computeIfAbsent(player, k -> new ArrayList<>()).add(Arrays.asList(new Line(mainLine), new Line(leftLine), new Line(rightLine)));
        playerLineIndices.computeIfAbsent(player, k -> new ArrayList<>()).add(0);
    }

    private List<Location> getLinePoints(Location origin, Vector direction) {
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Location point = origin.clone().add(direction.clone().multiply(i * 0.5));
            if (!UtilBlock.airFoliage(point.getBlock())) {
                break;
            }
            points.add(point);
        }
        return points;
    }

    @UpdateEvent
    public void spawnParticles() {
        for (Player player : playerLines.keySet()) {
            List<List<Line>> setsOfLines = playerLines.get(player);
            List<Integer> indices = playerLineIndices.get(player);

            Iterator<List<Line>> setIterator = setsOfLines.iterator();
            Iterator<Integer> indexIterator = indices.iterator();

            while (setIterator.hasNext() && indexIterator.hasNext()) {
                List<Line> lines = setIterator.next();
                int index = indexIterator.next();

                if (index >= 20) {
                    setIterator.remove();
                    indexIterator.remove();
                    continue;
                }

                for (Line line : lines) {
                    if (index < line.getPoints().size()) {
                        Location point = line.getPoints().get(index);
                        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);

                        for (LivingEntity target : UtilEntity.getNearbyEnemies(player, point, windChargeRadius)) {
                            CustomDamageEvent cde = new CustomDamageEvent(target, player, null, EntityDamageEvent.DamageCause.CUSTOM, windDamage, false, "Wind Burst");
                            cde.setDamageDelay(0);
                            UtilDamage.doCustomDamage(cde);
                            Vector knockback = point.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5);
                            target.setVelocity(knockback);
                            UtilSound.playSound(target.getWorld(), target.getLocation(), Sound.ENTITY_PUFFER_FISH_STING, 0.8F, 1.5F);
                        }
                    }
                }
                indices.set(indices.indexOf(index), index + 1);
            }
        }
    }

    @UpdateEvent
    public void checkCollision() {
        Iterator<Entry<Player, Long>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Player, Long> next = it.next();
            Player player = next.getKey();
            if (player.isDead()) {
                it.remove();
                continue;
            }

            final Location midpoint = UtilPlayer.getMidpoint(player).clone();
            final Optional<LivingEntity> hit = UtilEntity.interpolateCollision(midpoint,
                            midpoint.clone().add(player.getVelocity().normalize().multiply(0.5)),
                            (float) 0.6,
                            ent -> UtilEntity.IS_ENEMY.test(player, ent))
                    .map(RayTraceResult::getHitEntity).map(LivingEntity.class::cast);

            if (hit.isPresent()) {
                it.remove();
                doWindBladeCollision(player, hit.get());
                continue;
            }

            if (UtilBlock.isGrounded(player) && UtilTime.elapsed(next.getValue(), 750L)) {
                it.remove();
            }
        }
    }

    private void doWindBladeCollision(Player player, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 3));
        UtilSound.playSound(player.getWorld(), player.getLocation(), Sound.ENTITY_PUFFER_FISH_STING, 0.8F, 1.5F);
        UtilMessage.simpleMessage(player, "Wind Blade", "You hit an enemy with <green>Flight<gray>.");
        UtilSound.playSound(player.getWorld(), player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1, 2);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(PreCustomDamageEvent event) {
        if (!enabled) {
            return;
        }

        CustomDamageEvent cde = event.getCustomDamageEvent();
        if (cde.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(cde.getDamager() instanceof Player damager)) return;
        if (isHoldingWeapon(damager)) {
            cde.setDamage(baseDamage);
            cde.setRawDamage(baseDamage);
        }
    }

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (!enabled) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (isHoldingWeapon(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (isHoldingWeapon(player)) {
            trackedPlayers.add(player);
        }
    }

    @UpdateEvent
    public void checkSneaking() {
        for (Player player : trackedPlayers) {
            if (isHoldingWeapon(player)) {
                if (player.isSneaking()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
                    if (!UtilBlock.isGrounded(player)) {
                        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation(), 1, 0.2, 0.2, 0.2, 0);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.0F);

                    }
                } else {
                    player.removePotionEffect(PotionEffectType.SLOW_FALLING);
                }
            } else {
                trackedPlayers.remove(player);
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    @Override
    public boolean canUse(Player player) {
        if (UtilBlock.isInLiquid(player)) {
            if (!activeUsageNotifications.contains(player.getUniqueId())) {
                UtilMessage.simpleMessage(player, getSimpleName(), String.format("You cannot use <green>%s <gray>while in water", ABILITY_NAME));
                activeUsageNotifications.add(player.getUniqueId());
            }
            return false;
        }
        activeUsageNotifications.remove(player.getUniqueId());
        return true;
    }

    @Override
    public double getEnergy() {
        return dashEnergyCost;
    }

    @Override
    public void loadWeaponConfig() {
        velocityStrength = getConfig("velocityStrength", 1.2, Double.class);
        windChargeRadius = getConfig("windChargeRadius", 2.0, Double.class);
        windDamage = getConfig("windDamage", 1.0, Double.class);
        lineStartDistance = getConfig("lineStartDistance", 1.0, Double.class);
        particleDuration = getConfig("particleDuration", 10, Integer.class);
        windBurstCooldown = getConfig("windBurstCooldown", 0.75, Double.class);
        energyCost = getConfig("energyCost", 15, Integer.class);
        dashEnergyCost = getConfig("dashEnergyCost", 40, Integer.class);
    }
}
