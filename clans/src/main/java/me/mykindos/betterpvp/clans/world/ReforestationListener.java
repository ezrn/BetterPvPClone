package me.mykindos.betterpvp.clans.world;

import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Listener;

import javax.inject.Inject;
import java.util.*;

@Singleton
@BPvPListener
public class ReforestationListener implements Listener {

    private final ClanManager clanManager;

    @Inject
    public ReforestationListener(ClanManager clanManager) {
        this.clanManager = clanManager;
    }

    @UpdateEvent(delay = 30000)
    public void plantTrees() {
        int attempts = 0;
        int maxAttempts = 5;

        while (attempts < maxAttempts) {
            Chunk chunk = findLoadedChunk();
            if (chunk == null) {
                return;
            }

            Location plantLocation = getPlantableLocationInChunk(chunk);
            if (plantLocation != null) {
                plantLocation.getBlock().setType(Material.OAK_SAPLING);
                return;
            }
            attempts++;
        }
    }

    public Chunk findLoadedChunk() {
        List<Chunk> wildernessChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            Chunk[] loadedChunks = world.getLoadedChunks();
            for (Chunk chunk : loadedChunks) {
                int centerX = (chunk.getX() << 4) + 8;
                int centerZ = (chunk.getZ() << 4) + 8;
                Location chunkLocation = new Location(world, centerX, world.getHighestBlockYAt(centerX, centerZ), centerZ);

                Optional<Clan> clanOptional = this.clanManager.getClanByLocation(chunkLocation);
                if (clanOptional.isEmpty() || clanOptional.get().getTerritory().isEmpty()) {
                    wildernessChunks.add(chunk);
                }
            }
        }
        if (!wildernessChunks.isEmpty()) {
            Random random = new Random();
            return wildernessChunks.get(random.nextInt(wildernessChunks.size()));
        }
        return null;
    }

    public Location getPlantableLocationInChunk(Chunk chunk) {
        List<Location> plantableLocations = new ArrayList<>();
        Random random = new Random();

        int minY = 45;
        int maxY = 70;

        List<Integer> xCoords = new ArrayList<>();
        List<Integer> zCoords = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            xCoords.add(i);
            zCoords.add(i);
        }
        Collections.shuffle(xCoords);
        Collections.shuffle(zCoords);

        for (int x : xCoords) {
            for (int z : zCoords) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material blockType = block.getType();
                    if (blockType == Material.GRASS_BLOCK || blockType == Material.DIRT) {
                        Block aboveBlock = block.getRelative(BlockFace.UP);
                        if (aboveBlock.isEmpty() && aboveBlock.getRelative(BlockFace.UP).isEmpty()) {
                            plantableLocations.add(aboveBlock.getLocation());
                        }
                    }
                }
            }
        }

        if (!plantableLocations.isEmpty()) {
            return plantableLocations.get(random.nextInt(plantableLocations.size()));
        }
        return null;
    }
}
