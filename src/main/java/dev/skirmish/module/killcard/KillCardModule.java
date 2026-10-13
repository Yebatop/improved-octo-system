package dev.skirmish.module.killcard;

import dev.skirmish.module.Category;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.util.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * KillCard: a PNG card after every kill. Data is gathered on the client thread in {@link #onKill}; rendering,
 * PNG encoding and file IO run on a single background thread ({@link CardExporter}). Instead of the clipboard, the
 * chat line links the file, the folder and the path, and a HUD toast shows a thumbnail.
 */
public final class KillCardModule extends Module {
    public static final String ID = "killcard";

    /** Output size of the 800×420 layout. */
    public enum CardSize {
        X1, X2
    }

    final EnumSetting<CardSize> size = add(new EnumSetting<>("size", CardSize.X2));
    final BoolSetting playersOnly = add(new BoolSetting("players_only", true));
    final BoolSetting showServer = add(new BoolSetting("show_server", true));
    final BoolSetting chatMessage = add(new BoolSetting("chat_message", true));
    final BoolSetting toast = add(new BoolSetting("toast", true));
    final ActionSetting preview = add(new ActionSetting("preview", this::preview));
    final ActionSetting openFolder = add(new ActionSetting("open_folder", this::openFolder));

    private final AtomicInteger cardCounter = new AtomicInteger();
    private @Nullable ExecutorService executor;
    private final KillCardToast cardToast = new KillCardToast(this);

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    public KillCardModule() {
        super(ID, true);
    }

    @Override
    public void onInitialize() {
        dev.skirmish.hud.Hud.get().register(cardToast);
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onKill(Fight fight) {
                if (isEnabled()) {
                    KillCardModule.this.onKill(fight);
                }
            }
        });
    }

    @Override
    public void tick() {
        while (SkirmishKeys.KILLCARD_OPEN_FOLDER.consumeClick()) {
            openFolder();
        }
    }

    static Path cardDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("killcards");
    }

    private void onKill(Fight fight) {
        if (playersOnly.get() && !fight.opponent().player()) {
            log("kill of %s ignored: not a player (setting players_only)", fight.opponent().name());
            return;
        }
        long end = fight.endMs() >= 0 ? fight.endMs() : System.currentTimeMillis();
        EquipmentSnapshot equipment = CombatTracker.get().equipment(fight);
        log("kill received: %s; duration %d ms, hits %d/%d, damage %.1f (%s, %d health drops), totems %d, last damage type %s, equipment %s",
                fight, fight.durationMs(end), fight.hitsDealt(), fight.hitsTaken(), fight.damageDealt(),
                fight.isDamageKnown() ? "known" : "UNKNOWN: server hides health", fight.healthDropsObserved(),
                fight.opponentTotems(), fight.lastDamageTypeDealt(),
                equipment == null ? "not captured" : "captured " + (end - equipment.timeMs()) + " ms before the kill");
        submit(fight, equipment, false, myHealth());
    }

    private static float myHealth() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || !mc.player.isAlive() ? -1f : mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    /** Settings button: re-renders the last kill as a preview, or shows my own gear when there was none yet. */
    private void preview() {
        Minecraft mc = Minecraft.getInstance();
        Fight lastKill = null;
        try {
            for (Fight fight : CombatTracker.get().finishedFights()) {
                if (fight.endReason() == FightEndReason.KILL) {
                    lastKill = fight;
                    break;
                }
            }
        } catch (IllegalStateException e) {
            error("combat tracker unavailable", e);
        }
        if (lastKill != null) {
            log("preview: re-rendering the last kill (%s)", lastKill);
            submit(lastKill, CombatTracker.get().equipment(lastKill), true, myHealth());
        } else if (mc.player != null) {
            log("preview: no kill yet, using my own equipment and zero stats");
            submit(null, EquipmentSnapshot.capture(mc.player, System.currentTimeMillis()), true, myHealth());
        } else {
            log("preview: no kill yet and not in a world, nothing to render");
        }
    }

    private void submit(@Nullable Fight fight, @Nullable EquipmentSnapshot equipment, boolean isPreview, float health) {
        Minecraft mc = Minecraft.getInstance();
        int number = cardCounter.incrementAndGet();
        long gatherStart = System.nanoTime();
        KillCardData data;
        try {
            data = gather(mc, fight, equipment, isPreview, number, health);
        } catch (Throwable t) {
            error("card #" + number + ": collecting data failed", t);
            chat(Component.translatable("skirmish.killcard.chat.failed", String.valueOf(t.getMessage())).withStyle(ChatFormatting.RED));
            return;
        }
        log("card #%d: data gathered on the client thread in %.2f ms (scale %.0fx, faces %s/%s)", number,
                (System.nanoTime() - gatherStart) / 1e6, data.scale(), data.stats().killerFace() != null ? "yes" : "no",
                data.stats().victimFace() != null ? "yes" : "no");

        Path directory = cardDirectory();
        boolean announce = chatMessage.get();
        try {
            executor().execute(() -> {
                CardExporter.Outcome outcome = CardExporter.export(data, directory);
                report(number, outcome);
                mc.execute(() -> announce(outcome, directory, announce));
            });
        } catch (RejectedExecutionException e) {
            error("card #" + number + ": card thread rejected the job", e);
        }
    }

    private KillCardData gather(Minecraft mc, @Nullable Fight fight, @Nullable EquipmentSnapshot equipment, boolean isPreview,
                                int number, float health) {
        String me = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        java.util.UUID myId = mc.player != null ? mc.player.getUUID() : null;
        long end = fight == null ? System.currentTimeMillis() : fight.endMs() >= 0 ? fight.endMs() : System.currentTimeMillis();
        int[] myFace = FaceReader.face(mc, myId);
        CardStats stats = fight == null
                ? new CardStats(me, me, health, true, 0, 0, 0, 0, 0, time(end), server(), true, myFace, myFace)
                : new CardStats(me, fight.opponent().name(), health, fight.isDamageKnown(), fight.damageDealt(), fight.hitsDealt(),
                fight.attackAttempts(), fight.opponentTotems(), fight.durationMs(end), time(end), server(), isPreview,
                myFace, FaceReader.face(mc, fight.opponent().uuid()));

        List<CardItem> gear = new ArrayList<>(6);
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            CardItem item = equipment == null ? CardItem.EMPTY : IconExtractor.extract(mc, equipment.get(slot));
            gear.add(item);
            if (!item.isEmpty()) {
                log("card #%d: icon %s %s x%d: %s%s", number, slot.getName(), item.itemId(), item.count(), item.iconInfo(),
                        item.icon() == null ? " -> empty tile" : "");
            }
        }
        if (equipment == null) {
            log("card #%d: no equipment snapshot for this fight, all slots empty", number);
        }
        Language language = Language.getInstance();
        return new KillCardData(stats, CardText.load(language::getOrDefault, stats.totemsPopped()), gear,
                size.get() == CardSize.X2 ? 2.0 : 1.0);
    }

    private @Nullable String server() {
        return showServer.get() ? ServerContext.serverDisplayName() : null;
    }

    private static ZonedDateTime time(long epochMs) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    /** Card thread. */
    private void report(int number, CardExporter.Outcome outcome) {
        if (outcome.error() != null) {
            error("card #" + number + " failed after render " + outcome.renderMs() + " ms, encode " + outcome.encodeMs()
                    + " ms, save " + outcome.saveMs() + " ms", outcome.error());
            return;
        }
        log("card #%d: rendered %dx%d in %d ms (font %s), PNG %d KiB encoded in %d ms, saved in %d ms: %s", number,
                outcome.width(), outcome.height(), outcome.renderMs(), outcome.font(), outcome.pngBytes() / 1024,
                outcome.encodeMs(), outcome.saveMs(), outcome.file());
        for (String warning : outcome.warnings()) {
            log("card #%d: icon drawn as placeholder: %s", number, warning);
        }
    }

    /** Client thread: toast preview and the chat line with links to the card, the folder and the path. */
    private void announce(CardExporter.Outcome outcome, Path directory, boolean announceSaved) {
        Path file = outcome.file();
        if (file == null) {
            Throwable error = outcome.error();
            chat(Component.translatable("skirmish.killcard.chat.failed", error == null ? "?" : String.valueOf(error.getMessage()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (toast.get() && outcome.image() != null) {
            try {
                cardToast.show(outcome.image(), file.getFileName().toString());
            } catch (RuntimeException e) {
                error("card preview toast failed", e);
            }
        }
        if (announceSaved) {
            String path = file.toAbsolutePath().toString();
            MutableComponent message = Component.translatable("skirmish.killcard.chat.saved")
                    .append(" ").append(link("skirmish.killcard.chat.open_card", new ClickEvent.OpenFile(file.toAbsolutePath()),
                            Component.literal(file.getFileName().toString())))
                    .append(" ").append(folderLink(directory))
                    .append(" ").append(link("skirmish.killcard.chat.copy_path", new ClickEvent.CopyToClipboard(path),
                            Component.literal(path)));
            chat(message);
        }
    }

    private static MutableComponent link(String key, ClickEvent click, Component hover) {
        return Component.translatable(key).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent folderLink(Path directory) {
        return Component.translatable("skirmish.killcard.chat.open_folder").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(directory.toAbsolutePath()))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(directory.toAbsolutePath().toString()))));
    }

    private void chat(Component message) {
        Minecraft.getInstance().gui.getChat().addMessage(Component.empty()
                .append(Component.literal("[KillCard] ").withStyle(ChatFormatting.GOLD)).append(message));
    }

    private void openFolder() {
        Path directory = cardDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            error("cannot create " + directory, e);
            return;
        }
        log("opening card folder %s", directory.toAbsolutePath());
        Util.getPlatform().openPath(directory);
    }

    private synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Skirmish KillCard");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY + 1);
                return thread;
            });
        }
        return executor;
    }
}
