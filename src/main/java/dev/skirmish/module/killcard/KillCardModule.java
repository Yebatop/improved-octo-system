package dev.skirmish.module.killcard;

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
import dev.skirmish.setting.NumberSetting;
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
 * PNG encoding, file IO and the clipboard run on a single background thread ({@link CardExporter}).
 */
public final class KillCardModule extends Module {
    public static final String ID = "killcard";

    final EnumSetting<CardTheme> theme = add(new EnumSetting<>("theme", CardTheme.MIDNIGHT));
    final NumberSetting scale = add(new NumberSetting("scale", 1.0, 1.0, 2.0, 0.5).unit("x"));
    final BoolSetting playersOnly = add(new BoolSetting("players_only", true));
    final BoolSetting showServer = add(new BoolSetting("show_server", true));
    final BoolSetting copyToClipboard = add(new BoolSetting("copy_to_clipboard", true));
    final BoolSetting chatMessage = add(new BoolSetting("chat_message", true));
    final ActionSetting preview = add(new ActionSetting("preview", this::preview));
    final ActionSetting openFolder = add(new ActionSetting("open_folder", this::openFolder));

    private final AtomicInteger cardCounter = new AtomicInteger();
    private @Nullable ExecutorService executor;

    public KillCardModule() {
        super(ID, true);
    }

    @Override
    public void onInitialize() {
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
        submit(fight, equipment, false);
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
            submit(lastKill, CombatTracker.get().equipment(lastKill), true);
        } else if (mc.player != null) {
            log("preview: no kill yet, using my own equipment and zero stats");
            submit(null, EquipmentSnapshot.capture(mc.player, System.currentTimeMillis()), true);
        } else {
            log("preview: no kill yet and not in a world, nothing to render");
        }
    }

    private void submit(@Nullable Fight fight, @Nullable EquipmentSnapshot equipment, boolean isPreview) {
        Minecraft mc = Minecraft.getInstance();
        int number = cardCounter.incrementAndGet();
        long gatherStart = System.nanoTime();
        KillCardData data;
        try {
            data = gather(mc, fight, equipment, isPreview, number);
        } catch (Throwable t) {
            error("card #" + number + ": collecting data failed", t);
            chat(Component.translatable("skirmish.killcard.chat.failed", String.valueOf(t.getMessage())).withStyle(ChatFormatting.RED));
            return;
        }
        log("card #%d: data gathered on the client thread in %.2f ms (theme %s, scale %.1f)", number,
                (System.nanoTime() - gatherStart) / 1e6, data.theme(), data.scale());

        Path directory = cardDirectory();
        boolean clipboard = copyToClipboard.get();
        boolean announce = chatMessage.get();
        try {
            executor().execute(() -> {
                CardExporter.Outcome outcome = CardExporter.export(data, directory, clipboard);
                report(number, outcome);
                mc.execute(() -> announce(outcome, directory, announce));
            });
        } catch (RejectedExecutionException e) {
            error("card #" + number + ": card thread rejected the job", e);
        }
    }

    private KillCardData gather(Minecraft mc, @Nullable Fight fight, @Nullable EquipmentSnapshot equipment, boolean isPreview, int number) {
        String me = mc.player != null ? mc.player.getGameProfile().name() : mc.getUser().getName();
        long end = fight == null ? System.currentTimeMillis() : fight.endMs() >= 0 ? fight.endMs() : System.currentTimeMillis();
        CardStats stats = fight == null
                ? new CardStats(me, me, true, 0, 0, 0, 0, 0, time(end), server(), true)
                : new CardStats(me, fight.opponent().name(), fight.isDamageKnown(), fight.damageDealt(), fight.hitsDealt(),
                fight.hitsTaken(), fight.opponentTotems(), fight.durationMs(end), time(end), server(), isPreview);

        List<CardItem> gear = new ArrayList<>(6);
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            CardItem item = equipment == null ? CardItem.EMPTY : IconExtractor.extract(mc, equipment.get(slot));
            gear.add(item);
            if (!item.isEmpty()) {
                log("card #%d: icon %s %s x%d: %s%s", number, slot.getName(), item.itemId(), item.count(), item.iconInfo(),
                        item.icon() == null ? " -> placeholder with the item name" : "");
            }
        }
        if (equipment == null) {
            log("card #%d: no equipment snapshot for this fight, all slots empty", number);
        }
        Language language = Language.getInstance();
        return new KillCardData(stats, CardText.load(language::getOrDefault), theme.get(), gear, scale.get());
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
        ImageClipboard.Result clipboard = outcome.clipboard();
        if (clipboard == null) {
            log("card #%d: clipboard copy disabled in settings", number);
        } else if (clipboard.success()) {
            log("card #%d: copied to clipboard via %s in %d ms (%s)", number, clipboard.method(), clipboard.millis(), clipboard.detail());
        } else {
            DebugLog.log(ID, String.format(Locale.ROOT, "card #%d: clipboard copy FAILED via %s after %d ms: %s (os=%s, headless=%s)",
                    number, clipboard.method(), clipboard.millis(), clipboard.detail(), System.getProperty("os.name"),
                    System.getProperty("java.awt.headless")));
        }
    }

    /** Client thread. */
    private void announce(CardExporter.Outcome outcome, Path directory, boolean announceSaved) {
        Path file = outcome.file();
        if (file == null) {
            Throwable error = outcome.error();
            chat(Component.translatable("skirmish.killcard.chat.failed", error == null ? "?" : String.valueOf(error.getMessage()))
                    .withStyle(ChatFormatting.RED));
            return;
        }
        ImageClipboard.Result clipboard = outcome.clipboard();
        if (announceSaved) {
            MutableComponent fileLink = Component.literal(file.getFileName().toString()).withStyle(style -> style
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("skirmish.killcard.chat.open_card"))));
            MutableComponent message = Component.translatable("skirmish.killcard.chat.saved", fileLink)
                    .append(" ").append(folderLink(directory));
            if (clipboard != null && clipboard.success()) {
                message.append(" ").append(Component.translatable("skirmish.killcard.chat.copied").withStyle(ChatFormatting.GREEN));
            }
            chat(message);
        }
        if (clipboard != null && !clipboard.success()) {
            chat(Component.translatable("skirmish.killcard.chat.clipboard_failed", clipboard.method() + ": " + clipboard.detail())
                    .withStyle(ChatFormatting.YELLOW));
        }
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
