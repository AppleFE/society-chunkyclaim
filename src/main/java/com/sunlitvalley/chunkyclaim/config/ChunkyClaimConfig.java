package com.sunlitvalley.chunkyclaim.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunlitvalley.chunkyclaim.SocietyChunkyClaimMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class ChunkyClaimConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("society-chunkyclaim.json");

    private static volatile ConfigData current;

    private ChunkyClaimConfig() {
    }

    public static synchronized ConfigData initialize(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server");
        if (!Files.exists(PATH)) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            ConfigData defaults = ConfigData.defaults(
                    overworld.dimension().location().toString(),
                    spawn.getX(),
                    spawn.getY(),
                    spawn.getZ()
            );
            validate(defaults);
            write(defaults);
            current = defaults;
            return defaults;
        }
        return reload();
    }

    public static synchronized ConfigData reload() throws IOException {
        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
            validate(loaded);
            current = loaded;
            return loaded;
        } catch (JsonParseException exception) {
            throw new IOException("Invalid " + PATH.getFileName() + ": " + exception.getMessage(), exception);
        }
    }

    public static ConfigData get() {
        ConfigData value = current;
        if (value == null) {
            throw new IllegalStateException(SocietyChunkyClaimMod.MOD_ID + " config is not initialized");
        }
        return value;
    }

    public static synchronized void setTicketItem(ItemStack heldItem) throws IOException {
        if (heldItem.isEmpty()) {
            throw new IllegalArgumentException("Ticket item cannot be empty");
        }

        ConfigData updated = get().copy();
        ItemStack template = heldItem.copy();
        template.setCount(1);
        updated.ticketItemSnbt = template.save(new CompoundTag()).toString();
        validate(updated);
        write(updated);
        current = updated;
    }

    public static ItemStack ticketItem() {
        String snbt = get().ticketItemSnbt;
        if (snbt == null || snbt.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack stack = ItemStack.of(TagParser.parseTag(snbt));
            stack.setCount(1);
            return stack;
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Configured claim ticket is invalid", exception);
        }
    }

    public static boolean isTicket(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack template = ticketItem();
        return !template.isEmpty() && ItemStack.isSameItemSameTags(template, stack);
    }

    public static Path path() {
        return PATH;
    }

    private static void validate(ConfigData data) throws IOException {
        if (data == null) {
            throw new IOException("Configuration root cannot be null");
        }
        if (data.nearbyClaimExclusionBlocks < 0) {
            throw new IOException("nearbyClaimExclusionBlocks must be at least 0");
        }
        if (data.spawnExclusionBlocks < 0) {
            throw new IOException("spawnExclusionBlocks must be at least 0");
        }
        if (data.inviteExpirationSeconds < 1) {
            throw new IOException("inviteExpirationSeconds must be at least 1");
        }
        if (data.protectionMessageCooldownMillis < 0) {
            throw new IOException("protectionMessageCooldownMillis must be at least 0");
        }
        if (data.spawn == null || ResourceLocation.tryParse(data.spawn.dimension) == null) {
            throw new IOException("spawn.dimension must be a valid dimension id");
        }
        if (data.ticketItemSnbt != null && !data.ticketItemSnbt.isBlank()) {
            try {
                ItemStack item = ItemStack.of(TagParser.parseTag(data.ticketItemSnbt));
                if (item.isEmpty()) {
                    throw new IOException("ticketItemSnbt must describe a non-empty item");
                }
            } catch (CommandSyntaxException exception) {
                throw new IOException("ticketItemSnbt must be valid SNBT", exception);
            }
        }
    }

    private static void write(ConfigData data) throws IOException {
        Files.createDirectories(PATH.getParent());
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        try {
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class ConfigData {
        public int nearbyClaimExclusionBlocks;
        public SpawnPoint spawn;
        public int spawnExclusionBlocks;
        public int inviteExpirationSeconds;
        public int protectionMessageCooldownMillis;
        public String ticketItemSnbt;

        public static ConfigData defaults(String dimension, int x, int y, int z) {
            ConfigData data = new ConfigData();
            data.nearbyClaimExclusionBlocks = 200;
            data.spawn = new SpawnPoint(dimension, x, y, z);
            data.spawnExclusionBlocks = 300;
            data.inviteExpirationSeconds = 120;
            data.protectionMessageCooldownMillis = 1_500;
            data.ticketItemSnbt = "";
            return data;
        }

        private ConfigData copy() {
            ConfigData data = new ConfigData();
            data.nearbyClaimExclusionBlocks = nearbyClaimExclusionBlocks;
            data.spawn = new SpawnPoint(spawn.dimension, spawn.x, spawn.y, spawn.z);
            data.spawnExclusionBlocks = spawnExclusionBlocks;
            data.inviteExpirationSeconds = inviteExpirationSeconds;
            data.protectionMessageCooldownMillis = protectionMessageCooldownMillis;
            data.ticketItemSnbt = ticketItemSnbt;
            return data;
        }
    }

    public static final class SpawnPoint {
        public String dimension;
        public int x;
        public int y;
        public int z;

        public SpawnPoint() {
        }

        public SpawnPoint(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
