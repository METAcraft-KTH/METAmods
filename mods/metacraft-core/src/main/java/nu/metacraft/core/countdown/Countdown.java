package nu.metacraft.core.countdown;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import nu.metacraft.core.util.METAcraftCoreData;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Countdown {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static final Codec<Countdown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("date").forGetter(c -> c.date.getTime()),
            Codec.INT.fieldOf("interval").forGetter(c -> c.interval),
            Codec.list(Action.CODEC).fieldOf("actions").forGetter(c -> c.actions),
            Codec.list(UUIDUtil.AUTHLIB_CODEC).fieldOf("entity_uuids").forGetter(c -> c.entityUuids)
    ).apply(instance, Countdown::new));

    private List<UUID> entityUuids;
    private Map<UUID, Display.TextDisplay> entityCache;
    private Date date;
    private int tickCount = 0;
    private int interval;
    private final List<Action> actions = new ArrayList<>();
    private final Set<Action> executedActions = new HashSet<>();
    private METAcraftCoreData parent;

    public record Action(String name, int activationMillis, String command) {
        public static final Codec<Action> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Action::name),
                Codec.INT.fieldOf("activation_millis").forGetter(Action::activationMillis),
                Codec.STRING.fieldOf("command").forGetter(Action::command)
        ).apply(instance, Action::new));
    }

    public Countdown(Date date, List<UUID> entityUuids) {
        this.date = date;
        this.interval = 20;
        this.entityUuids = entityUuids;
        this.entityCache = new HashMap<>(this.entityUuids.size());
    }

    private Countdown(long date, int interval, List<Action> actions, List<UUID> entityUuids) {
        this.date = new Date(date);
        this.interval = interval;
        this.entityUuids = entityUuids;
        this.entityCache = new HashMap<>(this.entityUuids.size());
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
        for (Action action : this.actions) {
            if (this.getMillisLeft() <= action.activationMillis) {
                this.executedActions.add(action);
            } else {
                this.executedActions.remove(action);
            }
        }
        this.markDirty();
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
        this.markDirty();
    }

    public void addAction(Action action) {
        this.actions.add(action);
        this.markDirty();
    }

    public void setParent(METAcraftCoreData parent) {
        this.parent = parent;
    }

    private void markDirty() {
        if (this.parent != null) {
            this.parent.setDirty();
        }
    }

    public List<Action> getActions() {
        return Collections.unmodifiableList(this.actions);
    }

    public boolean removeAction(String name) {
        boolean removed = this.actions.removeIf(action -> action.name().equals(name));
        this.markDirty();
        return removed;
    }

    public void updateEntityUuids(List<UUID> entityUuids) {
        this.entityUuids = entityUuids;
        this.entityCache = new HashMap<>(this.entityUuids.size());
        this.markDirty();
    }

    @Nullable
    public Display.TextDisplay getEntity(MinecraftServer server, UUID uuid) {
        Display.TextDisplay cached = this.entityCache.get(uuid);
        if (cached != null && cached.isAlive()) {
            return cached;
        }
        for (ServerLevel world : server.getAllLevels()) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof Display.TextDisplay textDisplay && entity.isAlive()) {
                this.entityCache.put(uuid, textDisplay);
                return textDisplay;
            }
        }
        return null;
    }

    public long getMillisLeft() {
        return this.date.getTime() - System.currentTimeMillis();
    }

    public String getTimeLeftString() {
        StringBuilder stringBuilder = new StringBuilder();
        long millis = this.getMillisLeft();
        int totalSeconds = (int) (millis / 1000L);
        int totalMinutes = totalSeconds / 60;
        int totalHours = totalMinutes / 60;
        int totalDays = totalHours / 24;
        if (totalDays > 0) {
            stringBuilder.append(totalDays);
            stringBuilder.append(" day");
            if (totalDays != 1) stringBuilder.append('s');
            stringBuilder.append(' ');
        }
        if (totalHours > 0) {
            int hours = totalHours % 24;
            stringBuilder.append(hours);
            stringBuilder.append(" hour");
            if (hours != 1) stringBuilder.append('s');
            stringBuilder.append(' ');
        }
        if (totalMinutes > 0) {
            int minutes = totalMinutes % 60;
            stringBuilder.append(minutes);
            stringBuilder.append(" minute");
            if (minutes != 1) stringBuilder.append('s');
            stringBuilder.append(' ');
        }
        if (totalSeconds > 0) {
            int seconds = totalSeconds % 60;
            stringBuilder.append(seconds);
            stringBuilder.append(" second");
            if (seconds != 1) stringBuilder.append('s');
        } else {
            return "Now!";
        }
        return stringBuilder.toString();
    }

    public void tick(MinecraftServer server) {
        this.tickCount++;
        if (this.tickCount % this.interval != 0) {
            return;
        }
        if (server.getPlayerCount() > 0) {
            Component text = Component.literal(this.getTimeLeftString());
            for (UUID uuid : this.entityUuids) {
                var entity = this.getEntity(server, uuid);
                if (entity != null) {
                    entity.setText(text);
                }
            }
            long millisLeft = this.getMillisLeft();
            for (Action action : this.actions) {
                if (millisLeft <= action.activationMillis && !this.executedActions.contains(action)) {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack().withSuppressedOutput(),
                            action.command
                    );
                    this.executedActions.add(action);
                }
            }
        }
    }
}
