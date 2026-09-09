package metacraft.ovvar.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.impl.PolymerResourcePackMod;
import metacraft.ovvar.Ovvar;
import com.mojang.datafixers.util.Pair;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The combinations of patches the resource pack knows how to draw. Every combination ever
 * sewn is remembered in {@code <world>/ovvar/combos.json}; the pack is built with an equipment
 * definition for each (per chapter and rolled-down variant — tiny files), and rebuilt and
 * re-sent to everyone online when new ones appear. Rebuilds are batched: a combination the
 * preview bits can still show waits {@value #LAZY_MS} ms for company, one they can't is built
 * within {@value #URGENT_MS} ms. Until a client has the new pack the newest patches ride in
 * the dye colour (see {@link metacraft.ovvar.content.Looks#look}).
 */
public final class Combos {
    private Combos() {}

    private static final long URGENT_MS = 2_000, LAZY_MS = 90_000;
    private static final double RESYNC_RANGE = 160;
    private static final String FILE = "ovvar/combos.json";

    /** piece:combo keys. known = requested or loaded; built = in the pack the clients have. */
    private static final Set<String> KNOWN = ConcurrentHashMap.newKeySet();
    private static volatile Set<String> built = Set.of();
    private static volatile Set<String> building = Set.of();
    private static final AtomicLong deadline = new AtomicLong(Long.MAX_VALUE);
    private static final AtomicBoolean dirty = new AtomicBoolean();
    private static volatile boolean generating;
    private static MinecraftServer server;

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            KNOWN.clear();
            KNOWN.addAll(load(file(s)));
            built = Set.of();
            Ovvar.LOGGER.info("[ovvar] {} patch combination(s) known", KNOWN.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save(s));
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            building = Set.copyOf(KNOWN);
            int files = 0;
            for (String key : building) {
                Piece piece = piece(key);
                String combo = combo(key);
                List<Placement> placements = Arrays.stream(combo.split("-")).map(Placement::parse).toList();
                for (Chapter chapter : Chapter.values()) {
                    for (boolean[] v : EquipmentJson.variants(chapter, piece)) {
                        builder.addStringData(EquipmentJson.packPath(chapter, piece, v[0], combo), EquipmentJson.json(chapter, piece, v[0], placements));
                        files++;
                    }
                }
            }
            Ovvar.LOGGER.info("[ovvar] pack: {} combination(s), {} equipment file(s)", building.size(), files);
        });
        PolymerResourcePackUtils.RESOURCE_PACK_FINISHED_EVENT.register(result -> {
            built = building;
            generating = false;
            if (!built.containsAll(KNOWN)) deadline.accumulateAndGet(now() + URGENT_MS, Math::min);
        });
        ServerTickEvents.END_SERVER_TICK.register(Combos::tick);
    }

    /**
     * A player has the pack that was just sent: everything they see that wears an ovve is sent
     * again, so the assets that pack holds are what their client draws (equipment packets are
     * only sent on change, and nothing changed server-side).
     */
    public static void packLoaded(ServerPlayer player) {
        int sent = 0;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RESYNC_RANGE))) {
            if (entity == player) continue;
            List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.getItem() instanceof OvveItem || stack.getItem() instanceof OvveTopItem) slots.add(Pair.of(slot, stack));
            }
            if (slots.isEmpty()) continue;
            player.connection.send(new ClientboundSetEquipmentPacket(entity.getId(), slots));
            sent++;
        }
        player.containerMenu.sendAllDataToRemote();
        player.inventoryMenu.sendAllDataToRemote();
        Ovvar.LOGGER.debug("[ovvar] {} loaded the pack; re-sent {} wearer(s)", player.getName().getString(), sent);
    }

    public static boolean isBuilt(Piece piece, String combo) {
        return combo.isEmpty() || built.contains(key(piece, combo));
    }

    /** Ask for a combination; safe from any thread (item packets are encoded off the server thread). */
    public static void request(Piece piece, String combo, boolean urgent) {
        String key = key(piece, combo);
        if (built.contains(key)) return;
        if (KNOWN.add(key)) dirty.set(true);
        deadline.accumulateAndGet(now() + (urgent ? URGENT_MS : LAZY_MS), Math::min);
    }

    private static void tick(MinecraftServer s) {
        if (dirty.compareAndSet(true, false)) save(s);
        if (now() < deadline.get() || generating) return;
        if (PolymerResourcePackMod.alreadyGeneration) {
            deadline.set(now() + 1_000);   // someone else's build; ours follows
            return;
        }
        deadline.set(Long.MAX_VALUE);
        generating = true;
        Ovvar.LOGGER.info("[ovvar] rebuilding the resource pack for {} new combination(s)", KNOWN.size() - built.size());
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), "polymer generate-pack reload");
    }

    // ---- keys

    private static String key(Piece piece, String combo) {
        return piece.id + ":" + combo;
    }

    private static Piece piece(String key) {
        String id = key.substring(0, key.indexOf(':'));
        for (Piece p : Piece.values()) if (p.id.equals(id)) return p;
        throw new IllegalStateException("bad combo key " + key);
    }

    private static String combo(String key) {
        return key.substring(key.indexOf(':') + 1);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---- persistence

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    private static Set<String> load(Path path) {
        if (!Files.exists(path)) return Set.of();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> out = new TreeSet<>();
            for (var e : root.getAsJsonArray("combos")) {
                String key = e.getAsString();
                // Validate now: a combination that no longer parses (a removed patch) must not reach the pack.
                try {
                    piece(key);
                    Arrays.stream(combo(key).split("-")).forEach(Placement::parse);
                    out.add(key);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    Ovvar.LOGGER.warn("[ovvar] dropping combination {}: {}", key, ex.getMessage());
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static void save(MinecraftServer s) {
        Path path = file(s);
        try {
            Files.createDirectories(path.getParent());
            JsonArray arr = new JsonArray();
            List<String> sorted = new ArrayList<>(KNOWN);
            Collections.sort(sorted);
            sorted.forEach(arr::add);
            JsonObject root = new JsonObject();
            root.add("combos", arr);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + path, e);
        }
    }
}
