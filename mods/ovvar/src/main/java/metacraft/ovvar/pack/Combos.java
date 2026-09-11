package metacraft.ovvar.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import eu.pb4.polymer.autohost.api.ResourcePackDataProvider;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.impl.PolymerResourcePackMod;
import metacraft.ovvar.Ovvar;
import metacraft.ovvar.content.Chapter;
import metacraft.ovvar.content.OvveItem;
import metacraft.ovvar.content.OvveTopItem;
import metacraft.ovvar.content.Piece;
import metacraft.ovvar.content.Placement;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
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
import java.util.*;

import org.jspecify.annotations.NonNull;
import org.pcollections.TreePVector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The combinations of patches the resource pack knows how to draw. Every combination ever
 * sewn is remembered in {@code <world>/ovvar/combos.json}; the pack is built with an equipment
 * definition for each (per chapter and rolled-down variant — tiny files) and rebuilt when new
 * ones appear. Rebuilds are batched: a combination the preview bits can still show waits
 * {@value #LAZY_MS} ms for company, one they can't is built within {@value #URGENT_MS} ms.
 *
 * Each build is a generation, and each player is on the generation they last loaded. A push is
 * a loading screen, so nobody gets one they did not cause: the pack is pushed to a player only
 * when their own sewing outgrew what the dye colour can show of it ({@link #claim}, at once when
 * the build is done), or when they ask with {@code /ovvar reload}. Everyone else keeps the pack
 * they have and sees what it holds plus the newest patches in the dye colour (see
 * {@link metacraft.ovvar.content.Looks#look}); whoever joins gets the current pack.
 */
public final class Combos {
    private Combos() {}

    private static final long URGENT_MS = 2_000, LAZY_MS = 90_000, PUSH_GAP_MS = 5_000;
    private static final double RESYNC_RANGE = 160;
    private static final String FILE = "ovvar/combos.json";

    /** piece:combo keys. known = requested or loaded; BUILT = per generation, what that pack holds. */
    private static final Set<KeyedCombo> KNOWN = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Set<KeyedCombo>> BUILT = new ConcurrentHashMap<>();
    private static volatile int generation;
    private static volatile Set<KeyedCombo> building = Set.of();
    private static final AtomicLong deadline = new AtomicLong(Long.MAX_VALUE);
    private static final AtomicBoolean dirty = new AtomicBoolean();
    private static volatile boolean generating;
    private static MinecraftServer server;

    private static final Codec<List<KeyedCombo>> COMBOS_CONFIG_CODEC = KeyedCombo.KEY_CODEC.listOf().fieldOf(
            "combos"
    ).codec();

    /** Per player: the generation pushed to them, the one they confirmed loaded, who is owed a push, when they last got one. */
    private static final Map<UUID, Integer> PUSHED = new ConcurrentHashMap<>(), LOADED = new ConcurrentHashMap<>();
    private static final Set<UUID> NEEDS_PUSH = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_PUSH = new ConcurrentHashMap<>();
    /** Who sewed a combination the pack does not hold yet and cannot be shown it otherwise: they get the pack once it is built. */
    private static final Map<KeyedCombo, Set<UUID>> CLAIMED_BY = new ConcurrentHashMap<>();
    /** Who asked to reload while a build was pending: they get the pack once it is built. */
    private static final Set<UUID> RELOADING = ConcurrentHashMap.newKeySet();

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            KNOWN.clear();
            KNOWN.addAll(load(file(s)));
            BUILT.clear();
            generation = 0;
            Ovvar.LOGGER.info("[ovvar] {} patch combination(s) known", KNOWN.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(Combos::save);
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder -> {
            building = Set.copyOf(KNOWN);
            int files = 0;
            for (KeyedCombo key : building) {
                Piece piece = key.piece();
                Combo combo = key.combo();
                List<Placement> placements = combo.placements();
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
            Set<KeyedCombo> snapshot = building;
            server.execute(() -> built(snapshot));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            // The pack they got while connecting is the current one.
            UUID id = handler.player.getUUID();
            PUSHED.put(id, generation);
            LOADED.put(id, generation);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            UUID id = handler.player.getUUID();
            PUSHED.remove(id);
            LOADED.remove(id);
            NEEDS_PUSH.remove(id);
            LAST_PUSH.remove(id);
            RELOADING.remove(id);
            for (Set<UUID> claimers : CLAIMED_BY.values()) claimers.remove(id);
        });
        ServerTickEvents.END_SERVER_TICK.register(Combos::tick);
    }

    // ---- what a player can draw

    /** Does the pack a player has hold this combination? {@code player} null: the current pack. */
    public static boolean isBuilt(Piece piece, Combo combo, UUID player) {
        if (combo.isEmpty()) return true;
        KeyedCombo key = key(piece, combo);
        Set<KeyedCombo> current = BUILT.getOrDefault(generation, Set.of());
        if (player == null) return current.contains(key);
        return BUILT.getOrDefault(LOADED.getOrDefault(player, generation), current).contains(key);
    }

    /** Ask for a combination; safe from any thread (item packets are encoded off the server thread). */
    public static void request(Piece piece, Combo combo, boolean urgent) {
        KeyedCombo key = key(piece, combo);
        if (BUILT.getOrDefault(generation, Set.of()).contains(key)) return;
        if (KNOWN.add(key)) dirty.set(true);
        if (generating && building.contains(key)) return;   // the build under way has it
        deadline.accumulateAndGet(now() + (urgent ? URGENT_MS : LAZY_MS), Math::min);
    }

    /**
     * A player sewed a half into a combination and cannot be shown all of it without the pack
     * (server thread): they get the pack as soon as it holds the combination — now, if it
     * already does — since a sewing session is nowhere near a fight.
     */
    public static void claim(ServerPlayer player, Piece piece, Combo combo) {
        KeyedCombo key = key(piece, combo);
        if (BUILT.getOrDefault(generation, Set.of()).contains(key)) {
            if (LOADED.getOrDefault(player.getUUID(), 0) < generation) NEEDS_PUSH.add(player.getUUID());
            return;
        }
        request(piece, combo, true);
        CLAIMED_BY.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
    }

    /**
     * {@code /ovvar reload}: the current pack to the player if theirs is older, after a build if
     * one is pending. Returns what to tell them.
     */
    public static String reload(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean pending = generating || !BUILT.getOrDefault(generation, Set.of()).containsAll(KNOWN);
        if (pending) {
            RELOADING.add(id);
            deadline.accumulateAndGet(now(), Math::min);
            return "Building the latest pack; it will be sent to you in a moment";
        }
        if (PUSHED.getOrDefault(id, 0) >= generation) return "You already have the latest pack";
        NEEDS_PUSH.add(id);
        return "Sending you the latest pack";
    }

    // ---- building and pushing

    private static void tick(MinecraftServer s) {
        if (dirty.compareAndSet(true, false)) save(s);
        pushWhereNeeded(s);
        if (now() < deadline.get() || generating) return;
        if (PolymerResourcePackMod.alreadyGeneration) {
            deadline.set(now() + 1_000);   // someone else's build; ours follows
            return;
        }
        deadline.set(Long.MAX_VALUE);
        if (BUILT.getOrDefault(generation, Set.of()).containsAll(KNOWN)) return;   // a request raced the last build
        generating = true;
        Ovvar.LOGGER.info("[ovvar] rebuilding the resource pack for {} new combination(s)",
                KNOWN.size() - BUILT.getOrDefault(generation, Set.of()).size());
        PolymerResourcePackMod.generateAndCall(s, false, message -> Ovvar.LOGGER.info("[ovvar] {}", message.getString()), result -> {});
    }

    /** A build finished (server thread): a new generation; players who asked for its combinations get it. */
    private static void built(Set<KeyedCombo> combos) {
        generation++;
        BUILT.put(generation, combos);
        generating = false;
        int online = Integer.MAX_VALUE;
        for (int g : LOADED.values()) online = Math.min(online, g);
        for (int g : PUSHED.values()) online = Math.min(online, g);
        int keep = online;
        BUILT.keySet().removeIf(g -> g < keep && g < generation);
        for (var e : CLAIMED_BY.entrySet()) {
            if (combos.contains(e.getKey())) NEEDS_PUSH.addAll(e.getValue());
        }
        CLAIMED_BY.keySet().removeIf(combos::contains);
        if (combos.containsAll(KNOWN)) {
            NEEDS_PUSH.addAll(RELOADING);
            RELOADING.clear();
        } else {
            deadline.accumulateAndGet(now() + URGENT_MS, Math::min);
        }
        Ovvar.LOGGER.info("[ovvar] pack generation {}: {} combination(s); {} player(s) to update", generation, combos.size(), NEEDS_PUSH.size());
    }

    private static void pushWhereNeeded(MinecraftServer s) {
        if (NEEDS_PUSH.isEmpty()) return;
        for (UUID id : List.copyOf(NEEDS_PUSH)) {
            ServerPlayer player = s.getPlayerList().getPlayer(id);
            if (player == null || PUSHED.getOrDefault(id, 0) >= generation) {
                NEEDS_PUSH.remove(id);
                continue;
            }
            if (now() - LAST_PUSH.getOrDefault(id, 0L) < PUSH_GAP_MS) continue;   // one reload at a time
            push(player);
            NEEDS_PUSH.remove(id);
        }
    }

    /** The same push Polymer's {@code /polymer generate-pack reload} does, for one player. */
    private static void push(ServerPlayer player) {
        var provider = ResourcePackDataProvider.getActive();
        var context = player.connection.getPacketContext();
        if (!provider.isReady(context)) return;
        for (var info : provider.getProperties(context)) {
            player.connection.send(new ClientboundResourcePackPushPacket(info.id(), info.url(), info.hash(),
                    PolymerResourcePackUtils.isRequired(), Optional.empty()));
        }
        PUSHED.put(player.getUUID(), generation);
        LAST_PUSH.put(player.getUUID(), now());
        Ovvar.LOGGER.info("[ovvar] sent pack generation {} to {}", generation, player.getName().getString());
    }

    /**
     * A player has the pack that was just sent: everything they see that wears an ovve is sent
     * again, so the assets that pack holds are what their client draws (equipment packets are
     * only sent on change, and nothing changed server-side).
     */
    public static void packLoaded(ServerPlayer player) {
        UUID id = player.getUUID();
        LOADED.put(id, PUSHED.getOrDefault(id, generation));
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
        Ovvar.LOGGER.debug("[ovvar] {} loaded pack generation {}; re-sent {} wearer(s)", player.getName().getString(), LOADED.get(id), sent);
    }

    // ---- keys

    private static KeyedCombo key(Piece piece, Combo combo) {
        return new KeyedCombo(piece, combo);
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

    private static Collection<KeyedCombo> load(Path path) {
        if (!Files.exists(path)) return Set.of();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return COMBOS_CONFIG_CODEC.parse(JsonOps.INSTANCE, root).resultOrPartial(Ovvar.LOGGER::warn).orElse(List.of());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static void save(MinecraftServer s) {
        Path path = file(s);
        try {
            Files.createDirectories(path.getParent());
            List<KeyedCombo> sorted = new ArrayList<>(KNOWN);
            Collections.sort(sorted);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(path, gson.toJson(COMBOS_CONFIG_CODEC.encodeStart(JsonOps.INSTANCE, sorted).getOrThrow()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + path, e);
        }
    }

    public record Combo(List<Placement> placements) {

        public static final Codec<Combo> KEY_CODEC = Codec.STRING.comapFlatMap(
                key -> Arrays.stream(key.split("-")).map(placement -> Placement.CODEC.parse(JavaOps.INSTANCE, placement)).reduce(
                        DataResult.success(TreePVector.<Placement>empty()),
                        (lhs, rhs) -> lhs.flatMap(l -> rhs.map(l::plus)),
                        (lhs, rhs) -> lhs.flatMap(l -> rhs.map(l::plusAll))
                ).map(Combo::new),
		        Combo::key
        );

        public String key() {
            return String.join("-", placements.stream().sorted().map(Placement::key).toList());
        }

        public boolean isEmpty() {
            return placements.isEmpty();
        }
    }

    public record KeyedCombo(Piece piece, Combo combo) implements Comparable<KeyedCombo> {

        public static final Codec<KeyedCombo> KEY_CODEC = Codec.STRING.comapFlatMap(
                key -> {
                    int colon = key.indexOf(":");
                    if (colon < 0) {
                        return DataResult.error(() -> "bad combo key " + key);
                    }
                    return Piece.CODEC.parse(JavaOps.INSTANCE, key.substring(0, colon)).flatMap(
                            piece -> Combo.KEY_CODEC.parse(JavaOps.INSTANCE, key.substring(colon+1)).map(
                                    combo -> new KeyedCombo(piece, combo)
                            )
                    );
                },
                KeyedCombo::key
        );

        public String key() {
            return piece.id + ":" + combo.key();
        }

        @Override
        public int compareTo(Combos.@NonNull KeyedCombo keyedCombo) {
            return key().compareTo(keyedCombo.key());
        }
    }
}
