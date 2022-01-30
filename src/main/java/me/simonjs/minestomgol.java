package me.simonjs;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.*;
import net.minestom.server.instance.*;
import net.minestom.server.instance.batch.ChunkBatch;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.HashSet;
import java.util.List;

public class minestomgol {
    public static boolean paused = true;

    public static class Tuple {
        public int x;
        public int y;

        public Tuple(int x, int y) {
            this.x = x;
            this.y = y;
        }

        //https://stackoverflow.com/a/19885087
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + x;
            result = prime * result + y;
            return result;
        }

        //https://stackoverflow.com/a/19885087
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Tuple other = (Tuple) obj;
            if (x != other.x)
                return false;
            if (y != other.y)
                return false;
            return true;
        }
    }

    public static HashSet<Tuple> grid = new HashSet<>();

    private static class WorldGenerator implements ChunkGenerator {
        @Override
        public void generateChunkData(ChunkBatch batch, int chunkX, int chunkZ) {
            for (byte x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                for (byte z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    batch.setBlock(x, 100, z, Block.WHITE_TERRACOTTA);
                }
            }
        }

        @Override
        public List<ChunkPopulator> getPopulators() {
            return null;
        }
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setChunkGenerator(new WorldGenerator());

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
            event.setSpawningInstance(instanceContainer);

            Player player = event.getPlayer();
            player.setAllowFlying(true);

            PlayerInventory inventory = player.getInventory();
            inventory.setItemStack(4, ItemStack.of(Material.LIME_DYE));

            player.setRespawnPoint(new Pos(0, 120, 0));
        });

        globalEventHandler.addListener(PlayerStartDiggingEvent.class, event -> {
            Block block = event.getBlock();
            Instance instance = event.getInstance();
            Point pos = event.getBlockPosition();

            event.setCancelled(true);

            if (block.name().equals("minecraft:white_terracotta")) {
                instance.setBlock(pos, Block.BLACK_TERRACOTTA);
                grid.add(new Tuple(pos.blockX(), pos.blockZ()));
            } else if (block.name().equals("minecraft:black_terracotta")) {
                instance.setBlock(pos, Block.WHITE_TERRACOTTA);
                grid.remove(new Tuple(pos.blockX(), pos.blockZ()));
            }
        });

        globalEventHandler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().getMaterial().name().equals("minecraft:lime_dye")) {
                paused = !paused;
            }
        });

        globalEventHandler.addListener(PlayerTickEvent.class, event -> {
            if (!paused) {
                Instance instance = event.getInstance();

                long start = System.currentTimeMillis();

                HashSet<Tuple> gridCopy = new HashSet<>();
                gridCopy.addAll(grid);

                HashSet<Tuple> squaresToCheck = new HashSet<>();

                for (Tuple g : gridCopy) {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            Tuple neighbor = new Tuple(g.x + x, g.y + y);

                            if (!squaresToCheck.contains(neighbor)) {
                                squaresToCheck.add(neighbor);
                            }
                        }
                    }
                }

                for (Tuple g : squaresToCheck) {
                    int neighbors = 0;
                    boolean alive = gridCopy.contains(g);

                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            if (!(x == 0 && y == 0)) {
                                Tuple neighbor = new Tuple(g.x + x, g.y + y);

                                if (gridCopy.contains(neighbor)) {
                                    neighbors += 1;
                                }
                            }
                        }
                    }

                    if (alive) {
                        if (!(neighbors == 2 || neighbors == 3)) {
                            instance.setBlock(g.x, 100, g.y, Block.WHITE_TERRACOTTA);
                            grid.remove(g);
                        }
                    } else {
                        if (neighbors == 3) {
                            instance.setBlock(g.x, 100, g.y, Block.BLACK_TERRACOTTA);
                            grid.add(g);
                        }
                    }
                }

                long finish = System.currentTimeMillis();
                long timeElapsed = finish - start;

                event.getPlayer().sendActionBar(Component.text(String.format("Frame took %dms", timeElapsed)));
            }
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
