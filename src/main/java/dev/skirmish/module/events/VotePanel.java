package dev.skirmish.module.events;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * «Голосование» card while a Lite event vote runs on your server: each candidate with its tier, votes, share and a
 * bar, the leader in the accent colour, how long the vote has been on. When it ends the winner stays a minute. The
 * votes come from the public API; voting itself stays yours ({@code /vote}).
 */
final class VotePanel extends HudBlock {
    private final EventsModule module;
    private List<EventRows.Item> items = List.of();
    private String title = "";
    private @Nullable String meta;

    VotePanel(EventsModule module) {
        super("event_vote", "skirmish.hud.element.event_vote", new Placement(0, 0, 0, 0,
                Theme.get().num(EventRows.L + "default_x"), Theme.get().num(EventRows.L + "default_y")));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.voteCard.get();
    }

    @Override
    public boolean shown() {
        return HolyWorld.isConnected() && module.vote(System.currentTimeMillis()) != null;
    }

    @Override
    public String stackUnder() {
        return "events";
    }

    @Override
    public boolean stepsAsideInFight() {
        return true;
    }

    @Override
    public void update(boolean preview) {
        long now = System.currentTimeMillis();
        EventsModule.VoteState vote = module.vote(now);
        if (vote == null && preview) {
            vote = new EventsModule.VoteState(new EventsJson.Voting("", "", List.of(
                    new EventsJson.Candidate("Посылка", 236, Rarity.RARE),
                    new EventsJson.Candidate("Цветочная поляна", 57, Rarity.LEGENDARY))), now - 80_000, 0);
        }
        if (vote == null) {
            items = List.of();
            return;
        }
        List<EventsJson.Candidate> candidates = vote.voting().candidates();
        int total = 0;
        for (EventsJson.Candidate c : candidates) {
            total += c.votes();
        }
        boolean over = vote.endedAt() > 0;
        title = Ui.tr(over ? "skirmish.events.vote.result" : "skirmish.events.vote.title");
        // How long it has been on only when its start was seen (chat, or a poll just before without it).
        meta = over ? null : vote.startedAt() > 0 ? Ui.tr("skirmish.events.vote.on", EventSchedule.clock(now - vote.startedAt()))
                : Ui.tr("skirmish.events.state.running");
        List<EventRows.Item> out = new ArrayList<>();
        if (candidates.isEmpty()) {
            out.add(new EventRows.Note(Ui.tr("skirmish.events.schedule.vote.open")));
        }
        for (int i = 0; i < candidates.size(); i++) {
            EventsJson.Candidate c = candidates.get(i);
            // The list comes sorted by votes: the first is leading (or won); a tie leads no one.
            boolean lead = i == 0 && c.votes() > 0 && (candidates.size() == 1 || c.votes() > candidates.get(1).votes());
            if (over && !lead && i > 0) {
                continue;
            }
            int share = total == 0 ? 0 : Math.round(100f * c.votes() / total);
            String chip = c.rarity() == Rarity.UNKNOWN ? "" : EventRows.chipText(c.rarity(), "");
            String right = over && lead ? Ui.tr("skirmish.events.vote.won") : c.votes() + " · " + share + "%";
            out.add(new EventRows.Row(c.name(), c.rarity(), chip, right, lead ? "accent" : "text_2", null, false));
            if (!over) {
                out.add(new EventRows.Meter(total == 0 ? 0f : (float) c.votes() / total, lead ? "accent" : "text_3"));
            }
        }
        out.add(new EventRows.Note(Ui.tr(over ? "skirmish.events.vote.soon" : "skirmish.events.vote.hint")));
        items = out;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(EventRows.L + "vote_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return EventRows.height(ui, items);
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        EventRows.render(ui, x, y, width(ui, preview), title, meta, items, false);
    }
}
