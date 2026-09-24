package dev.skirmish.hud;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;

/**
 * One draggable HUD element (combat panel, target card, waypoint pill, ...). Sizes and drawing are in design px.
 * {@code preview} asks for sample content: the HUD editor shows every enabled element even without live data.
 */
public abstract class HudBlock {
    private final String id;
    private final String nameKey;
    private final Placement defaultPlacement;
    final Anim appear = new Anim("appear_ms");

    protected HudBlock(String id, String nameKey, Placement defaultPlacement) {
        this.id = id;
        this.nameKey = nameKey;
        this.defaultPlacement = defaultPlacement;
    }

    public final String id() {
        return id;
    }

    public final String nameKey() {
        return nameKey;
    }

    public final Placement defaultPlacement() {
        return defaultPlacement;
    }

    /** The owning module is on and the element is not switched off in its settings. */
    public abstract boolean enabled();

    /** Whether the element should be on screen now (drives the appear/hide fade). */
    public abstract boolean shown();

    /** Whether there is (possibly stale) data to draw; lets the element finish fading out. */
    public boolean hasContent() {
        return shown();
    }

    public abstract float width(Ui ui, boolean preview);

    public abstract float height(Ui ui, boolean preview);

    public abstract void render(Ui ui, float x, float y, boolean preview);

    /**
     * Id of a block to sit right under while this one keeps its default place (the schedule under the events list);
     * it takes the other block's place while that one is hidden. Null: own default placement.
     */
    public @org.jspecify.annotations.Nullable String stackUnder() {
        return null;
    }

    /** Called once per frame before measuring, e.g. to refresh cached data. */
    public void update(boolean preview) {
    }
}
