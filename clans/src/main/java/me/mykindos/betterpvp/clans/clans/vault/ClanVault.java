package me.mykindos.betterpvp.clans.clans.vault;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.leveling.ClanPerkManager;
import me.mykindos.betterpvp.clans.clans.leveling.perk.model.ClanVaultSlot;
import me.mykindos.betterpvp.clans.clans.menus.ClanMenu;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.menu.Windowed;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Represents a virtual inventory in which a clan can place items.
 */
@Setter
@Getter
public final class ClanVault {

    private final Clan clan;
    private final @NotNull Map<Integer, @NotNull ItemStack> contents;
    private String lockedBy;

    public ClanVault(Clan clan, @NotNull Map<Integer, @NotNull ItemStack> contents) {
        this.clan = clan;
        this.contents = contents;
    }

    public boolean hasPermission(Player player) {
        return clan.getMember(player.getUniqueId()).hasRank(ClanMember.MemberRank.ADMIN);
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    public int getSize() {
        final Clans clans = JavaPlugin.getPlugin(Clans.class);
        final int baseSize = clans.getConfig().getOrSaveInt("clans.clan.vault.base-size", 9);
        return baseSize + ClanPerkManager.getInstance().getPerks(clan).stream()
                .filter(ClanVaultSlot.class::isInstance)
                .map(ClanVaultSlot.class::cast)
                .mapToInt(ClanVaultSlot::getSlots)
                .sum();
    }

    @SneakyThrows
    public static @NotNull ClanVault of(Clan clan, String data) {
        final Int2ObjectOpenHashMap<ItemStack> map = new Int2ObjectOpenHashMap<>();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack[] items = new ItemStack[dataInput.readInt()];

        // Read the serialized inventory
        for (int i = 0; i < items.length; i++) {
            items[i] = (ItemStack) dataInput.readObject();
        }

        dataInput.close();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                map.put(i, items[i]);
            }
        }
        return new ClanVault(clan, map);
    }

    @SneakyThrows
    public String serialize() {
        final ItemStack[] items = new ItemStack[getSize()];
        for (Map.Entry<Integer, ItemStack> entry : contents.entrySet()) {
            items[entry.getKey()] = entry.getValue();
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        // Write the size of the inventory
        dataOutput.writeInt(items.length);

        // Save every element in the list
        for (ItemStack item : items) {
            dataOutput.writeObject(item);
        }

        // Serialize that array
        dataOutput.close();
        return Base64Coder.encodeLines(outputStream.toByteArray());
    }

    public static @NotNull ClanVault create(Clan clan) {
        return new ClanVault(clan, new Int2ObjectOpenHashMap<>());
    }

    public void show(Player player, Windowed previous) throws IllegalStateException {
        Preconditions.checkState(!isLocked(), "Clan vault is locked");
        lockedBy = player.getName();
        new GuiClanVault(player, this, previous).show(player).addCloseHandler(() -> {
            lockedBy = null;
            JavaPlugin.getPlugin(Clans.class).getInjector().getInstance(ClanRepository.class).updateClanVault(clan);
        });
    }

    public void show(Player player) throws IllegalStateException {
        show(player, new ClanMenu(player, clan, clan));
    }
}
