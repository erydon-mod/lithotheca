package com.oliver.lithotheca.client.screen;

import com.oliver.lithotheca.catalogue.BlockCatalogue;
import com.oliver.lithotheca.catalogue.CatalogueEntry;
import com.oliver.lithotheca.catalogue.CatalogueOrder;
import com.oliver.lithotheca.catalogue.FamilyMatchOrder;
import com.oliver.lithotheca.catalogue.RelatedBlocks;
import com.oliver.lithotheca.client.ClientRack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class LithothecaScreen extends Screen {
    private static final int NORMAL_SLOTS = 9;
    private static final int SCROLLBAR_WIDTH = 10;
    private static final int MENU_ROW = 16;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CLEAR_FILTER_WIDTH = 16;
    private static final int NAV_GAP = 8;
    private static final int DONE_WIDTH = 48;
    private static final List<String> FAMILY_SOURCES = List.of("erydon", "themelios", "erydon_themelios", "daedalon");
    private static final Set<String> BUILT_IN = Set.of("erydon", "themelios", "daedalon", "minecraft");
    private static final List<String> SURFACE_FINISHES = List.of("honed", "polished", "ganosis", "");

    private enum Mode { MATCH, NEARBY, BROWSE, HOTBAR }
    private enum Pivot { SHAPES, TEXTURES }
    private enum Facet {
        MATERIAL(CatalogueEntry::material),
        TEXTURE(CatalogueEntry::texture),
        SHAPE(CatalogueEntry::shape),
        SOURCE(CatalogueEntry::source);

        final Function<CatalogueEntry, String> read;

        Facet(Function<CatalogueEntry, String> read) {
            this.read = read;
        }
    }

    private final Screen parent;
    private final Map<Facet, String> filters = new EnumMap<>(Facet.class);
    private final Map<Facet, LithothecaTheme.Button> filterButtons = new EnumMap<>(Facet.class);
    private final Map<Facet, ButtonWidget> clearFilterButtons = new EnumMap<>(Facet.class);
    private final Map<Mode, LithothecaTheme.Button> modeButtons = new EnumMap<>(Mode.class);
    private final Map<Pivot, LithothecaTheme.Button> pivotButtons = new EnumMap<>(Pivot.class);
    private final Map<Identifier, ItemStack> icons = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, ItemStack> eldest) {
            return size() > 256;
        }
    };

    private LithothecaTheme theme;
    private TextFieldWidget search;
    private LithothecaTheme.Button duplicatesButton;
    private Mode mode = Mode.BROWSE;
    private Pivot pivot = Pivot.SHAPES;
    private Facet openFacet;
    private List<String> menuValues = List.of();
    private int menuFirst;
    private int menuSelected;
    private List<CatalogueEntry> catalogue = List.of();
    private Set<Identifier> nearbyIds;
    private List<CatalogueEntry> nearby = List.of();
    private List<CatalogueEntry> hotbar = List.of();
    private List<CatalogueEntry> results = List.of();
    private Identifier targetId;
    private CatalogueEntry target;
    private BlockPos targetPosition;
    private CatalogueEntry preview;
    private boolean cursorPress;
    private int pressedSlot = -1;
    private boolean dragging;
    private double pressX, pressY;
    private double scrollbarGrab = -1;
    private boolean captured;
    private boolean choseMode;
    private boolean hideDuplicates = true;
    private String query = "";
    private Text baseTextureLabel = tr("base_finish");
    private int searchDelay;
    private int resultRow;
    private int keyboardResult;
    private int keyboardSlot;
    private int panelLeft, panelTop, panelWidth, panelHeight, contentLeft, contentWidth;
    private int navY, searchY, searchWidth, filterY, gridTop, rackY, previewX;
    private int resultCell, rackCell;
    private int gridColumns, rackColumns, extraSlots;

    public LithothecaScreen(Screen parent) {
        super(tr("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        theme = new LithothecaTheme(client);
        if (!captured) {
            captured = true;
            catalogue = BlockCatalogue.entries();
            captureTarget();
            if (target != null) setMode(Mode.MATCH);
            ClientRack.refresh();
        }
        layout();
        openFacet = null;
        filterButtons.clear();
        clearFilterButtons.clear();
        modeButtons.clear();
        pivotButtons.clear();
        scrollbarGrab = -1;
        cursorPress = false;
        pressedSlot = -1;
        dragging = false;
        int doneLeft = contentLeft + contentWidth - DONE_WIDTH;
        addDrawableChild(theme.new Button(textRenderer, doneLeft, panelTop + 11,
                DONE_WIDTH, BUTTON_HEIGHT, Text.translatable("gui.done"), button -> close()));

        int pivotLeft = contentLeft + searchWidth + NAV_GAP;
        int pivotWidth = contentWidth - searchWidth - NAV_GAP;
        for (Mode value : Mode.values()) {
            modeButtons.put(value, addControl(contentLeft, contentWidth, value.ordinal(), Mode.values().length,
                    navY, modeLabel(value), () -> {
                        choseMode = true;
                        setMode(value);
                    }));
        }
        for (Pivot value : Pivot.values()) {
            pivotButtons.put(value, addControl(pivotLeft, pivotWidth, value.ordinal(), Pivot.values().length,
                    searchY, tr(value), () -> setPivot(value)));
        }
        search = new TextFieldWidget(textRenderer, contentLeft + 5, searchY + 4,
                searchWidth - 10, BUTTON_HEIGHT - 5, tr("search"));
        search.setDrawsBackground(false);
        search.setEditableColor(LithothecaTheme.CONTROL_TEXT);
        search.setPlaceholder(tr("search"));
        search.setMaxLength(120);
        search.setText(query);
        search.setChangedListener(value -> {
            query = value;
            searchDelay = 2;
        });
        addDrawableChild(search);
        int duplicateWidth = duplicateWidth();
        duplicatesButton = addDrawableChild(theme.new Button(textRenderer, doneLeft - duplicateWidth - 4, panelTop + 11,
                duplicateWidth, BUTTON_HEIGHT, Text.empty(), button -> {
                    hideDuplicates = !hideDuplicates;
                    updateControls();
                    rebuildResults();
                }));

        for (Facet facet : Facet.values()) {
            LithothecaTheme.Button filter = addControl(contentLeft, contentWidth, facet.ordinal(), Facet.values().length,
                    filterY, tr(facet), () -> toggleFacet(facet));
            int right = filter.getX() + filter.getWidth();
            filter.setWidth(filter.getWidth() - CLEAR_FILTER_WIDTH - 2);
            filterButtons.put(facet, filter);
            clearFilterButtons.put(facet, addClearFilterButton(facet, right - CLEAR_FILTER_WIDTH));
        }
        updateControls();
        rebuildResults();
    }

    private LithothecaTheme.Button addControl(int left, int width, int index, int count, int y, Text label, Runnable pressedAction) {
        int columnWidth = (width + 3) / count;
        int x = left + index * columnWidth;
        int buttonWidth = index == count - 1 ? width - index * columnWidth : columnWidth - 3;
        return addDrawableChild(theme.new Button(textRenderer, x, y, buttonWidth, BUTTON_HEIGHT, label, button -> pressedAction.run()));
    }

    private ButtonWidget addClearFilterButton(Facet facet, int x) {
        Text label = tr("clear_filter", tr(facet));
        ButtonWidget clear = new ButtonWidget(x, filterY, CLEAR_FILTER_WIDTH, BUTTON_HEIGHT, label,
                button -> clearFilter(facet), text -> text.get()) {
            @Override
            protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
                theme.control(draw, getX(), getY(), width, height, hovered || isFocused(), false, active);
                int color = active ? LithothecaTheme.CONTROL_TEXT : 0xffa99c85;
                int left = getX() + (width - 5) / 2;
                int top = getY() + (height - 5) / 2;
                for (int offset = 0; offset < 5; offset++) {
                    draw.fill(left + offset, top + offset, left + offset + 1, top + offset + 1, color);
                    draw.fill(left + 4 - offset, top + offset, left + 5 - offset, top + offset + 1, color);
                }
            }
        };
        clear.setTooltip(Tooltip.of(label));
        return addDrawableChild(clear);
    }

    private void clearFilter(Facet facet) {
        filters.remove(facet);
        openFacet = null;
        updateControls();
        rebuildResults();
    }

    private int duplicateWidth() {
        return Math.min(112, Math.max(70, contentWidth - 142));
    }

    private static Text modeLabel(Mode value) {
        return value == Mode.HOTBAR ? tr("hotbar_mode") : tr(value);
    }

    private void layout() {
        panelWidth = Math.min(width - 24, 520);
        panelHeight = Math.min(height - 16, 284);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        contentLeft = panelLeft + 13;
        contentWidth = panelWidth - 26;
        navY = panelTop + 32;
        searchY = panelTop + 50;
        filterY = panelTop + 68;
        int pivotWidth = Math.min(150, contentWidth / 2);
        searchWidth = contentWidth - pivotWidth - NAV_GAP;
        gridTop = panelTop + 97;
        resultCell = panelHeight < 250 ? 23 : 26;
        rackCell = contentWidth < 280 ? 19 : 20;
        int availableGridWidth = contentWidth - Math.min(contentWidth / 3, 144) - SCROLLBAR_WIDTH - 10;
        gridColumns = Math.max(1, availableGridWidth / resultCell);
        previewX = scrollbarX() + SCROLLBAR_WIDTH + 7;
        layoutRack();
    }

    private void layoutRack() {
        extraSlots = ClientRack.capacity();
        rackColumns = Math.max(1, Math.min(totalSlots(), contentWidth / rackCell));
        rackY = panelBottom() - 28 - rackRows() * rackCell;
        keyboardSlot = Math.min(keyboardSlot, totalSlots() - 1);
        resultRow = Math.min(resultRow, maxResultRow());
    }

    private int panelBottom() {
        return panelTop + panelHeight;
    }

    private int rackRows() {
        return (totalSlots() + rackColumns - 1) / rackColumns;
    }

    private int totalSlots() {
        return NORMAL_SLOTS + extraSlots;
    }

    private int visibleRows() {
        return Math.max(0, (rackY - gridTop - 13) / resultCell);
    }

    private void captureTarget() {
        if (client == null || client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        targetPosition = hit.getBlockPos().toImmutable();
        BlockState state = client.world.getBlockState(targetPosition);
        ItemStack picked = state.getBlock().getPickStack(client.world, targetPosition, state);
        if (!picked.isEmpty()) {
            targetId = Registries.ITEM.getId(picked.getItem());
            target = BlockCatalogue.find(targetId);
        }
    }

    private void captureNearby() {
        nearbyIds = new LinkedHashSet<>();
        if (client == null || client.world == null || client.player == null) return;
        BlockPos center = targetPosition == null ? client.player.getBlockPos() : targetPosition;
        for (BlockPos pos : BlockPos.iterate(center.add(-4, -2, -4), center.add(4, 2, 4))) {
            if (!client.world.isChunkLoaded(pos)) continue;
            BlockState state = client.world.getBlockState(pos);
            if (!state.isAir()) nearbyIds.add(Registries.ITEM.getId(state.getBlock().asItem()));
        }
        resolveNearby();
    }

    private void resolveNearby() {
        if (nearbyIds == null) return;
        List<CatalogueEntry> resolved = new ArrayList<>();
        for (Identifier id : nearbyIds) {
            CatalogueEntry entry = BlockCatalogue.find(id);
            if (entry != null) resolved.add(entry);
        }
        nearby = resolved;
    }

    private void setMode(Mode value) {
        mode = value;
        filters.clear();
        if (mode == Mode.MATCH && target != null) {
            if (!inferredMatch()) {
                filters.put(Facet.MATERIAL, target.material());
                filters.put(pivot == Pivot.TEXTURES ? Facet.SHAPE : Facet.TEXTURE,
                        pivot == Pivot.TEXTURES ? target.shape() : target.texture());
            }
            preview = target;
        }
        if (mode == Mode.NEARBY && nearbyIds == null) captureNearby();
        if (mode == Mode.HOTBAR) hotbar = readHotbar();
        if (search != null) {
            query = "";
            search.setText(query);
            updateControls();
            rebuildResults();
        }
    }

    private void setPivot(Pivot value) {
        if (pivot == value) return;
        pivot = value;
        CatalogueEntry reference = preview == null ? target : preview;
        if (!inferredMatch()) {
            filters.remove(value == Pivot.TEXTURES ? Facet.TEXTURE : Facet.SHAPE);
            if (reference != null) {
                Facet fixed = value == Pivot.TEXTURES ? Facet.SHAPE : Facet.TEXTURE;
                filters.put(fixed, fixed.read.apply(reference));
                filters.put(Facet.MATERIAL, reference.material());
            }
        }
        updateControls();
        rebuildResults();
    }

    private void updateControls() {
        for (var entry : modeButtons.entrySet()) {
            LithothecaTheme.Button button = entry.getValue();
            Text label = modeLabel(entry.getKey());
            button.active = entry.getKey() != Mode.MATCH || target != null;
            button.selected = entry.getKey() == mode;
            button.setTooltip(Tooltip.of(entry.getKey() == Mode.MATCH && target == null ? tr("no_target") : label));
        }
        for (var entry : pivotButtons.entrySet()) {
            entry.getValue().selected = entry.getKey() == pivot;
            entry.getValue().setTooltip(Tooltip.of(tr(entry.getKey())));
        }
        for (var entry : filterButtons.entrySet()) {
            Facet facet = entry.getKey();
            LithothecaTheme.Button button = entry.getValue();
            button.selected = filters.containsKey(facet);
            Text label = filters.containsKey(facet)
                    ? tr("facet_value", tr(facet), displayFacetValue(facet, filters.get(facet)))
                    : tr(facet);
            button.setMessage(label);
            button.setTooltip(Tooltip.of(label));
            clearFilterButtons.get(facet).active = filters.containsKey(facet);
        }
        Text duplicateLabel = tr(hideDuplicates ? "hide_duplicates_on" : "hide_duplicates_off");
        duplicatesButton.setMessage(duplicateLabel);
        duplicatesButton.selected = hideDuplicates;
        duplicatesButton.setTooltip(Tooltip.of(duplicateLabel));
    }

    private void rebuildResults() {
        searchDelay = 0;
        resultRow = 0;
        keyboardResult = 0;
        String[] terms = searchTerms();
        refreshBaseTextureLabel(terms);
        List<CatalogueEntry> matching = new ArrayList<>();
        boolean inferred = inferredMatch();
        ToIntFunction<CatalogueEntry> familyScore = mode == Mode.MATCH && target != null && FamilyMatchOrder.supports(target)
                ? FamilyMatchOrder.scorer(target, pivot == Pivot.TEXTURES) : null;
        boolean rankedMatch = inferred || familyScore != null;
        Map<Identifier, Integer> matchScores = new LinkedHashMap<>();
        for (CatalogueEntry entry : candidates()) {
            if (!matchesFilters(entry) || !matchesTerms(entry, terms)) continue;
            if (hiddenDuplicate(entry)) continue;
            if (rankedMatch) {
                int score;
                if (inferred) {
                    score = entry.id().equals(target.id()) ? Integer.MAX_VALUE
                            : RelatedBlocks.score(target, entry, pivot == Pivot.TEXTURES);
                } else {
                    score = familyScore.applyAsInt(entry);
                }
                if (inferred && score <= 0) continue;
                matchScores.put(entry.id(), score);
            }
            matching.add(entry);
        }
        if (mode != Mode.HOTBAR) {
            CatalogueOrder.sort(matching, mode == Mode.MATCH ? target : null, query,
                    rankedMatch ? entry -> matchScores.get(entry.id()) : null);
        }
        results = matching;
        if (preview == null || !results.contains(preview)) preview = results.isEmpty() ? null : results.get(0);
        if (preview != null) keyboardResult = results.indexOf(preview);
        scrollToResult(keyboardResult);
    }

    private boolean inferredMatch() {
        return mode == Mode.MATCH && target != null && target.inferred();
    }

    private List<CatalogueEntry> candidates() {
        return switch (mode) {
            case NEARBY -> nearby;
            case HOTBAR -> hotbar;
            case MATCH, BROWSE -> catalogue;
        };
    }

    private List<CatalogueEntry> readHotbar() {
        Set<CatalogueEntry> entries = new LinkedHashSet<>();
        for (int slot = 0; slot < totalSlots(); slot++) {
            ItemStack stack = ClientRack.stack(slot);
            if (stack.isEmpty()) continue;
            CatalogueEntry entry = BlockCatalogue.find(Registries.ITEM.getId(stack.getItem()));
            if (entry != null) entries.add(entry);
        }
        return List.copyOf(entries);
    }

    private boolean hiddenDuplicate(CatalogueEntry entry) {
        // Hotbar reflects what the builder has stored, including the precise source they chose.
        return mode != Mode.HOTBAR && hideDuplicates && entry.source().equals("themelios") && !entry.duplicateGroup().isEmpty();
    }

    private boolean matchesFilters(CatalogueEntry entry) {
        return matchesOtherFilters(entry, null);
    }

    private boolean matchesOtherFilters(CatalogueEntry entry, Facet ignored) {
        for (var filter : filters.entrySet()) {
            if (filter.getKey() != ignored && !filter.getValue().equals(filter.getKey().read.apply(entry))) return false;
        }
        return true;
    }

    private boolean inContext(CatalogueEntry entry) {
        return !inferredMatch() || entry.id().equals(target.id()) || RelatedBlocks.score(target, entry, pivot == Pivot.TEXTURES) > 0;
    }

    private String[] searchTerms() {
        return query.strip().toLowerCase(Locale.ROOT).split("\\s+");
    }

    private boolean matchesTerms(CatalogueEntry entry, String[] terms) {
        for (String term : terms) {
            if (!entry.searchText().contains(term)) return false;
        }
        return true;
    }

    private void toggleFacet(Facet facet) {
        if (openFacet == facet) {
            openFacet = null;
            return;
        }
        openFacet = facet;
        if (facet == Facet.TEXTURE) refreshBaseTextureLabel(searchTerms());
        menuFirst = 0;
        menuValues = facetValues(facet);
        menuSelected = Math.max(0, menuValues.indexOf(filters.getOrDefault(facet, "")));
        if (menuSelected >= menuRows()) menuFirst = menuSelected - menuRows() + 1;
    }

    private List<String> facetValues(Facet facet) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String[] terms = searchTerms();
        for (CatalogueEntry entry : candidates()) {
            if (hiddenDuplicate(entry) || !matchesOtherFilters(entry, facet) || !matchesTerms(entry, terms)) continue;
            if (inContext(entry)) values.add(facet.read.apply(entry));
        }
        List<String> ordered = new ArrayList<>(values.size() + 1);
        ordered.add("");
        if (facet != Facet.SOURCE) {
            ordered.addAll(values);
            return ordered;
        }
        for (String source : FAMILY_SOURCES) {
            if (values.remove(source)) ordered.add(source);
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (String source : values) names.put(source, display(Facet.SOURCE, source).getString());
        values.stream().sorted(Comparator.<String, String>comparing(names::get, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.naturalOrder())).forEach(ordered::add);
        return ordered;
    }

    private void reconcileFacets() {
        String source = filters.get(Facet.SOURCE);
        List<CatalogueEntry> remaining = candidates().stream()
                .filter(entry -> (source == null || source.equals(entry.source())) && !hiddenDuplicate(entry) && inContext(entry))
                .toList();
        for (Facet facet : Facet.values()) {
            String value = filters.get(facet);
            if (facet == Facet.SOURCE || value == null) continue;
            List<CatalogueEntry> matching = remaining.stream().filter(entry -> value.equals(facet.read.apply(entry))).toList();
            if (matching.isEmpty()) filters.remove(facet);
            else remaining = matching;
        }
    }

    private int menuX() {
        return Math.max(contentLeft, Math.min(filterButtons.get(openFacet).getX(), contentLeft + contentWidth - menuWidth()));
    }

    private int menuY() {
        return filterY + BUTTON_HEIGHT + 2;
    }

    private int menuWidth() {
        return Math.min(contentWidth, 152);
    }

    private int menuRows() {
        return Math.min(10, Math.min(menuValues.size(), Math.max(1, (panelBottom() - menuY() - 5) / MENU_ROW)));
    }

    private void selectFacetValue(int index) {
        String value = menuValues.get(index);
        Facet changed = openFacet;
        if (value.isEmpty()) filters.remove(changed);
        else filters.put(changed, value);
        if (changed == Facet.SOURCE) reconcileFacets();
        openFacet = null;
        updateControls();
        rebuildResults();
    }

    @Override
    public void tick() {
        search.tick();
        if (ClientRack.capacity() != extraSlots) layoutRack();
        List<CatalogueEntry> updated = BlockCatalogue.entries();
        if (catalogue != updated) {
            catalogue = updated;
            CatalogueEntry previousTarget = target;
            target = BlockCatalogue.find(targetId);
            openFacet = null;
            icons.clear();
            resolveNearby();
            if (mode == Mode.HOTBAR) hotbar = readHotbar();
            if (target != null && previousTarget == null && !choseMode && query.isEmpty()) {
                setMode(Mode.MATCH);
            } else {
                if (target == null && mode == Mode.MATCH) {
                    mode = Mode.BROWSE;
                    filters.clear();
                }
                updateControls();
                rebuildResults();
            }
        }
        if (mode == Mode.HOTBAR) {
            List<CatalogueEntry> refreshed = readHotbar();
            if (!refreshed.equals(hotbar)) {
                hotbar = refreshed;
                String source = filters.get(Facet.SOURCE);
                if (source != null && hotbar.stream().noneMatch(entry -> source.equals(entry.source()))) filters.remove(Facet.SOURCE);
                reconcileFacets();
                openFacet = null;
                updateControls();
                rebuildResults();
            }
        }
        if (searchDelay > 0 && --searchDelay == 0) rebuildResults();
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        theme.backdrop(draw, width, height);
        theme.panel(draw, panelLeft, panelTop, panelWidth, panelHeight);
        theme.heading(draw, textRenderer, title, contentLeft, panelTop + 6,
                contentWidth - DONE_WIDTH - duplicateWidth() - 8, 24);
        int divider = contentLeft + searchWidth + NAV_GAP / 2;
        draw.fill(divider, searchY + 1, divider + 1, searchY + BUTTON_HEIGHT - 1, LithothecaTheme.BRONZE);
        theme.control(draw, contentLeft, searchY, searchWidth, BUTTON_HEIGHT,
                search.isFocused() || inside(mouseX, mouseY, contentLeft, searchY, searchWidth, BUTTON_HEIGHT), false, true);
        Text context = switch (mode) {
            case MATCH -> tr(inferredMatch() ? "related_context" : "match_context", icon(target).getName());
            case NEARBY -> tr("nearby_context");
            case BROWSE -> tr("browse_context");
            case HOTBAR -> tr("hotbar_context");
        };
        drawText(draw, theme.branded(tr("results", context, results.size())), contentLeft, gridTop - 10, contentWidth, LithothecaTheme.MUTED);

        renderGrid(draw, mouseX, mouseY);
        renderPreview(draw);
        renderRack(draw, mouseX, mouseY);
        renderFooter(draw);
        super.render(draw, mouseX, mouseY, delta);
        if (openFacet != null) {
            renderFacet(draw, mouseX, mouseY);
        } else if (ClientRack.pickerCursor().isEmpty()) {
            CatalogueEntry hovered = resultAt(mouseX, mouseY);
            if (hovered != null) {
                draw.drawTooltip(textRenderer, List.of(icon(hovered).getName(), sourceLabel(hovered), facetSummary(hovered)), mouseX, mouseY);
            }
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0 && !ClientRack.stack(slot).isEmpty()) {
                draw.drawItemTooltip(textRenderer, ClientRack.stack(slot), mouseX, mouseY);
            }
        }
        ItemStack held = ClientRack.pickerCursor();
        if (!held.isEmpty()) {
            draw.getMatrices().push();
            draw.getMatrices().translate(0, 0, 450);
            draw.drawItem(held, mouseX - 8, mouseY - 8);
            draw.drawItemInSlot(textRenderer, held, mouseX - 8, mouseY - 8);
            draw.getMatrices().pop();
        }
    }

    private void renderFooter(DrawContext draw) {
        int top = panelBottom() - 25;
        Text rackMessage = ClientRack.message();
        ItemStack held = ClientRack.pickerCursor();
        Text message = !held.isEmpty() ? tr("choose_slot", held.getName())
                : rackMessage != null && !rackMessage.getString().isBlank() ? rackMessage : tr("drag_hint");
        var lines = textRenderer.wrapLines(message, contentWidth);
        for (int line = 0; line < Math.min(2, lines.size()); line++) {
            draw.drawText(textRenderer, lines.get(line), contentLeft, top + line * 9, LithothecaTheme.MUTED, false);
        }
    }

    private void renderGrid(DrawContext draw, int mouseX, int mouseY) {
        int trackHeight = trackHeight();
        if (trackHeight <= 0) return;
        theme.inset(draw, contentLeft - 2, gridTop - 2, scrollbarX() + SCROLLBAR_WIDTH - contentLeft + 4, trackHeight + 4);
        int first = resultRow * gridColumns;
        int count = Math.min(visibleRows() * gridColumns, results.size() - first);
        draw.enableScissor(contentLeft, gridTop, contentLeft + gridColumns * resultCell, gridTop + trackHeight);
        try {
            for (int offset = 0; offset < count; offset++) {
                CatalogueEntry entry = results.get(first + offset);
                int x = contentLeft + (offset % gridColumns) * resultCell;
                int y = gridTop + (offset / gridColumns) * resultCell;
                drawCell(draw, x, y, resultCell, entry == preview || inside(mouseX, mouseY, x, y, resultCell - 1, resultCell - 1));
                drawScaledItem(draw, icon(entry), x + 2, y + 2, resultCell - 5);
            }
            if (results.isEmpty()) {
                Text message = tr(mode == Mode.HOTBAR && hotbar.isEmpty() ? "empty_hotbar" : "no_results");
                if (catalogue.isEmpty() && !BlockCatalogue.status().isBlank()) {
                    message = tr(BlockCatalogue.status().startsWith("Preparing") ? "catalogue_preparing" : "catalogue_unavailable");
                }
                drawText(draw, message, contentLeft + 4, gridTop + 6, gridColumns * resultCell - 8, LithothecaTheme.MUTED);
            }
        } finally {
            draw.disableScissor();
        }
        renderScrollbar(draw, mouseX, mouseY);
    }

    private void renderScrollbar(DrawContext draw, int mouseX, int mouseY) {
        int x = scrollbarX();
        int height = trackHeight();
        draw.fill(x, gridTop, x + SCROLLBAR_WIDTH, gridTop + height, 0xff473323);
        draw.drawBorder(x, gridTop, SCROLLBAR_WIDTH, height, 0xff9c7851);
        if (maxResultRow() == 0) return;
        int top = thumbY();
        int thumbHeight = thumbHeight();
        boolean highlighted = scrollbarGrab >= 0 || inside(mouseX, mouseY, x, top, SCROLLBAR_WIDTH, thumbHeight);
        draw.fill(x + 2, top, x + SCROLLBAR_WIDTH - 2, top + thumbHeight, highlighted ? 0xffe4c895 : 0xffb6915d);
        draw.fill(x + 2, top, x + SCROLLBAR_WIDTH - 2, top + 1, 0xffffe3aa);
        draw.fill(x + 3, top + thumbHeight / 2, x + SCROLLBAR_WIDTH - 3, top + thumbHeight / 2 + 1, 0xff795734);
    }

    private int scrollbarX() {
        return contentLeft + gridColumns * resultCell + 2;
    }

    private int trackHeight() {
        return visibleRows() * resultCell;
    }

    private int thumbHeight() {
        int track = trackHeight();
        if (track == 0 || maxResultRow() == 0) return track;
        return Math.min(track - 1, Math.max(8, Math.round(track * (float) visibleRows() / totalResultRows())));
    }

    private int thumbY() {
        int max = maxResultRow();
        if (max == 0) return gridTop;
        return gridTop + Math.round((trackHeight() - thumbHeight()) * (float) resultRow / max);
    }

    private void scrollToPointer(double y) {
        int travel = trackHeight() - thumbHeight();
        if (travel <= 0) return;
        double ratio = (y - gridTop - scrollbarGrab) / travel;
        resultRow = MathHelper.clamp((int) Math.round(ratio * maxResultRow()), 0, maxResultRow());
    }

    private static void drawScaledItem(DrawContext draw, ItemStack item, int x, int y, int size) {
        draw.getMatrices().push();
        try {
            draw.getMatrices().translate(x, y, 0);
            draw.getMatrices().scale(size / 16f, size / 16f, 1);
            draw.drawItem(item, 0, 0);
        } finally {
            draw.getMatrices().pop();
        }
    }

    private void renderPreview(DrawContext draw) {
        if (visibleRows() == 0) return;
        int previewWidth = contentLeft + contentWidth - previewX;
        int previewHeight = trackHeight() + 4;
        theme.inset(draw, previewX, gridTop - 2, previewWidth, previewHeight);
        if (preview == null) return;
        int descriptionHeight = previewHeight >= 76 ? 30 : previewHeight >= 36 ? 10 : 0;
        int size = Math.min(64, Math.min(previewWidth - 10, previewHeight - descriptionHeight - 7));
        if (size < 12) return;
        ItemStack stack = icon(preview);
        draw.enableScissor(previewX + 1, gridTop, previewX + previewWidth - 1, gridTop + previewHeight - 3);
        try {
            drawScaledItem(draw, stack, previewX + (previewWidth - size) / 2, gridTop + 3, size);
            int labelY = gridTop + size + 5;
            if (descriptionHeight > 0) {
                drawText(draw, stack.getName(), previewX + 5, labelY, previewWidth - 10, LithothecaTheme.INK);
            }
            if (descriptionHeight > 10) {
                drawText(draw, sourceLabel(preview), previewX + 5, labelY + 10, previewWidth - 10, LithothecaTheme.MUTED);
                drawText(draw, facetSummary(preview), previewX + 5, labelY + 20, previewWidth - 10, LithothecaTheme.MUTED);
            }
        } finally {
            draw.disableScissor();
        }
    }

    private static Text sourceLabel(CatalogueEntry entry) {
        Text source = display(Facet.SOURCE, entry.source());
        return entry.inferred() ? tr("inferred", source) : source;
    }

    private static Text facetSummary(CatalogueEntry entry) {
        Text texture = entry.texture().equals("Standard") ? surfaceFinishLabel(entry.surfaceFinish())
                : display(Facet.TEXTURE, entry.texture());
        return tr("preview_facets", texture, display(Facet.SHAPE, entry.shape()));
    }

    private void renderRack(DrawContext draw, int mouseX, int mouseY) {
        Text label = tr("hotbar", totalSlots());
        if (!ClientRack.available()) label = tr("hotbar_local", totalSlots());
        drawText(draw, theme.branded(label), contentLeft, rackY - 10, contentWidth, LithothecaTheme.INK);
        int hovered = slotAt(mouseX, mouseY);
        int active = ClientRack.selected();
        boolean carrying = !ClientRack.pickerCursor().isEmpty();
        theme.inset(draw, contentLeft - 2, rackY - 2, rackColumns * rackCell + 3, rackRows() * rackCell + 3);
        for (int slot = 0; slot < totalSlots(); slot++) {
            int x = contentLeft + slot % rackColumns * rackCell;
            int y = rackY + slot / rackColumns * rackCell;
            drawCell(draw, x, y, rackCell, slot == hovered || slot == active || carrying && slot == keyboardSlot);
            ItemStack stack = ClientRack.stack(slot);
            if (stack.isEmpty()) continue;
            int offset = (rackCell - 16) / 2;
            draw.drawItem(stack, x + offset, y + offset);
            draw.drawItemInSlot(textRenderer, stack, x + offset, y + offset);
        }
    }

    private void renderFacet(DrawContext draw, int mouseX, int mouseY) {
        int x = menuX();
        int w = menuWidth();
        int height = menuRows() * MENU_ROW;
        draw.getMatrices().push();
        draw.getMatrices().translate(0, 0, 400);
        int top = menuY();
        draw.fill(x + 2, top + 2, x + w + 2, top + height + 2, 0x99000000);
        draw.fill(x, top, x + w, top + height, 0xfff0e2cb);
        draw.drawBorder(x, top, w, height, LithothecaTheme.BRONZE);
        Text hoveredFinish = null;
        for (int row = 0; row < menuRows(); row++) {
            int y = top + row * MENU_ROW;
            String value = menuValues.get(menuFirst + row);
            boolean family = openFacet == Facet.SOURCE && FAMILY_SOURCES.contains(value);
            if (family) draw.fill(x + 1, y + 1, x + w - 1, y + MENU_ROW - 1, 0xffe7d3ad);
            if (inside(mouseX, mouseY, x, y, w, MENU_ROW) || menuFirst + row == menuSelected) {
                draw.fill(x + 1, y + 1, x + w - 1, y + MENU_ROW - 1, LithothecaTheme.SELECTED);
            }
            if (family) draw.fill(x + 1, y + 2, x + 3, y + MENU_ROW - 2, LithothecaTheme.BRONZE);
            Text label = value.isEmpty() ? tr("all") : displayFacetValue(openFacet, value);
            drawText(draw, family ? theme.branded(label) : label, x + 6, y + 4, w - 12, LithothecaTheme.INK);
            if (openFacet == Facet.TEXTURE && value.equals("Standard")
                    && inside(mouseX, mouseY, x, y, w, MENU_ROW)) hoveredFinish = label;
        }
        if (hoveredFinish != null) draw.drawTooltip(textRenderer, hoveredFinish, mouseX, mouseY);
        draw.getMatrices().pop();
    }

    private void drawCell(DrawContext draw, int x, int y, int size, boolean active) {
        draw.fill(x, y, x + size - 1, y + size - 1, active ? LithothecaTheme.SELECTED : LithothecaTheme.INSET);
        draw.drawBorder(x, y, size - 1, size - 1, active ? 0xff9a682d : LithothecaTheme.MUTED);
    }

    private void drawText(DrawContext draw, Text text, int x, int y, int room, int color) {
        draw.drawText(textRenderer, fit(text, room), x, y, color, false);
    }

    private Text fit(Text text, int room) {
        return LithothecaTheme.fit(textRenderer, text, room);
    }

    private ItemStack icon(CatalogueEntry entry) {
        return entry == null ? ItemStack.EMPTY : icons.computeIfAbsent(entry.id(), id -> entry.stack());
    }

    private CatalogueEntry resultAt(double x, double y) {
        if (!inside(x, y, contentLeft, gridTop, gridColumns * resultCell, trackHeight())) return null;
        int index = (resultRow + ((int) y - gridTop) / resultCell) * gridColumns + ((int) x - contentLeft) / resultCell;
        return index < results.size() ? results.get(index) : null;
    }

    private int slotAt(double x, double y) {
        if (!inside(x, y, contentLeft, rackY, rackColumns * rackCell, rackRows() * rackCell)) return -1;
        int slot = ((int) y - rackY) / rackCell * rackColumns + ((int) x - contentLeft) / rackCell;
        return slot < totalSlots() ? slot : -1;
    }

    private int maxResultRow() {
        return Math.max(0, totalResultRows() - visibleRows());
    }

    private int totalResultRows() {
        return (results.size() + gridColumns - 1) / gridColumns;
    }

    private void scrollToResult(int index) {
        int row = index / gridColumns;
        if (row < resultRow) resultRow = row;
        else if (visibleRows() > 0 && row >= resultRow + visibleRows()) resultRow = row - visibleRows() + 1;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        CatalogueEntry item = resultAt(x, y);
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && item != null && openFacet == null) {
            cursorPress = false;
            dragging = false;
            pressedSlot = -1;
            focusResult(item);
            if (ClientRack.pickerCursor().isEmpty()) takeItem(item);
            else ClientRack.discardPickerCursor();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(x, y, button);
        cursorPress = false;
        dragging = false;
        int slot = openFacet == null ? slotAt(x, y) : -1;
        if (slot >= 0) {
            setFocused(null);
            keyboardSlot = slot;
            if (!ClientRack.pickerCursor().isEmpty() || !ClientRack.stack(slot).isEmpty()) {
                ClientRack.exchangePickerSlot(slot);
                beginCursorPress(x, y, slot);
            }
            return true;
        }
        boolean wasHolding = !ClientRack.pickerCursor().isEmpty();
        if (wasHolding) ClientRack.discardPickerCursor();
        if (openFacet != null) {
            if (inside(x, y, menuX(), menuY(), menuWidth(), menuRows() * MENU_ROW)) {
                selectFacetValue(menuFirst + ((int) y - menuY()) / MENU_ROW);
                return true;
            }
            openFacet = null;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (inside(x, y, contentLeft, searchY, searchWidth, BUTTON_HEIGHT)) {
            setInitialFocus(search);
            return true;
        }
        if (maxResultRow() > 0 && inside(x, y, scrollbarX(), gridTop, SCROLLBAR_WIDTH, trackHeight())) {
            int thumb = thumbY();
            int height = thumbHeight();
            scrollbarGrab = y >= thumb && y < thumb + height ? y - thumb : height / 2.0;
            scrollToPointer(y);
            return true;
        }
        if (item != null) {
            focusResult(item);
            if (!wasHolding) {
                takeItem(item);
                beginCursorPress(x, y, -1);
            }
            return true;
        }
        return wasHolding;
    }

    private void beginCursorPress(double x, double y, int slot) {
        cursorPress = true;
        pressedSlot = slot;
        setFocused(null);
        pressX = x;
        pressY = y;
    }

    private void focusResult(CatalogueEntry item) {
        preview = item;
        keyboardResult = results.indexOf(item);
        setFocused(null);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (scrollbarGrab >= 0) {
                scrollToPointer(y);
                return true;
            }
            if (cursorPress) {
                dragging |= Math.abs(x - pressX) >= 4 || Math.abs(y - pressY) >= 4;
                return true;
            }
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(x, y, button);
        if (scrollbarGrab >= 0) {
            scrollToPointer(y);
            scrollbarGrab = -1;
            return true;
        }
        if (!cursorPress) return super.mouseReleased(x, y, button);
        cursorPress = false;
        int destination = slotAt(x, y);
        if (dragging && !ClientRack.pickerCursor().isEmpty()) {
            if (destination >= 0 && destination != pressedSlot) ClientRack.exchangePickerSlot(destination);
            else if (destination < 0) ClientRack.discardPickerCursor();
        }
        dragging = false;
        pressedSlot = -1;
        return true;
    }

    private void takeItem(CatalogueEntry item) {
        ClientRack.takePickerItem(item.id());
        keyboardSlot = MathHelper.clamp(ClientRack.selected(), 0, totalSlots() - 1);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        int step = (int) Math.signum(amount);
        if (openFacet != null) {
            menuFirst = MathHelper.clamp(menuFirst - step, 0, menuValues.size() - menuRows());
            return true;
        }
        if (inside(x, y, contentLeft, gridTop, scrollbarX() + SCROLLBAR_WIDTH - contentLeft, trackHeight())) {
            resultRow = MathHelper.clamp(resultRow - step, 0, maxResultRow());
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean carrying = !ClientRack.pickerCursor().isEmpty();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (openFacet != null || carrying)) {
            openFacet = null;
            ClientRack.discardPickerCursor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            setInitialFocus(search);
            return true;
        }
        if (getFocused() == search) {
            if (keyCode != GLFW.GLFW_KEY_ENTER) return super.keyPressed(keyCode, scanCode, modifiers);
            if (searchDelay > 0) rebuildResults();
            setFocused(null);
        }
        if (openFacet != null) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
                int step = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                menuSelected = MathHelper.clamp(menuSelected + step, 0, menuValues.size() - 1);
                if (menuSelected < menuFirst) menuFirst = menuSelected;
                else if (menuSelected >= menuFirst + menuRows()) menuFirst = menuSelected - menuRows() + 1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                selectFacetValue(menuSelected);
                return true;
            }
        }
        int step = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> carrying ? -rackColumns : -gridColumns;
            case GLFW.GLFW_KEY_DOWN -> carrying ? rackColumns : gridColumns;
            default -> 0;
        };
        if (step != 0 && (carrying || !results.isEmpty())) {
            if (!carrying) {
                keyboardResult = MathHelper.clamp(keyboardResult + step, 0, results.size() - 1);
                preview = results.get(keyboardResult);
                scrollToResult(keyboardResult);
            } else {
                keyboardSlot = MathHelper.clamp(keyboardSlot + step, 0, totalSlots() - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && getFocused() == null) {
            if (carrying) ClientRack.exchangePickerSlot(keyboardSlot);
            else if (preview != null) takeItem(preview);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    private void refreshBaseTextureLabel(String[] terms) {
        Set<String> finishes = new LinkedHashSet<>();
        for (CatalogueEntry entry : candidates()) {
            if (entry.texture().equals("Standard") && !hiddenDuplicate(entry)
                    && matchesOtherFilters(entry, Facet.TEXTURE) && matchesTerms(entry, terms) && inContext(entry)) {
                finishes.add(entry.surfaceFinish());
            }
        }
        var label = Text.empty();
        for (String finish : SURFACE_FINISHES) {
            if (!finishes.contains(finish)) continue;
            if (!label.getSiblings().isEmpty()) label.append(" / ");
            label.append(surfaceFinishLabel(finish));
        }
        baseTextureLabel = label.getSiblings().isEmpty() ? tr("base_finish") : label;
        LithothecaTheme.Button button = filterButtons.get(Facet.TEXTURE);
        if (button != null && "Standard".equals(filters.get(Facet.TEXTURE))) {
            Text text = tr("facet_value", tr(Facet.TEXTURE), baseTextureLabel);
            button.setMessage(text);
            button.setTooltip(Tooltip.of(text));
        }
    }

    private Text displayFacetValue(Facet facet, String value) {
        return facet == Facet.TEXTURE && value.equals("Standard") ? baseTextureLabel : display(facet, value);
    }

    private static Text surfaceFinishLabel(String finish) {
        return switch (finish) {
            case "honed" -> Text.translatableWithFallback("option.erydon.polish.honed", "Honed");
            case "polished" -> Text.translatableWithFallback("option.erydon.polish.polished", "Polished");
            case "ganosis" -> Text.translatableWithFallback("option.erydon.polish.mirror", "Ganosis");
            default -> tr("base_finish");
        };
    }

    private static Text display(Facet facet, String value) {
        if (facet == Facet.SOURCE && value.equals("erydon_themelios")) return tr("source.themelios");
        if (facet == Facet.SOURCE && BUILT_IN.contains(value)) return tr("source." + value);
        if (value.equals("Unclassified")) return tr("unclassified");
        if (facet == Facet.TEXTURE && value.equals("Standard")) return tr("base_finish");
        if (facet == Facet.SOURCE) {
            return FabricLoader.getInstance().getModContainer(value)
                .map(mod -> Text.literal(mod.getMetadata().getName()))
                .orElseGet(() -> Text.literal(value));
        }
        return Text.literal(value);
    }

    private static Text tr(Enum<?> value) {
        return tr(value.name().toLowerCase(Locale.ROOT));
    }

    private static Text tr(String key, Object... args) {
        return Text.translatable("screen.lithotheca." + key, args);
    }
}
