package com.whitefog.tests.hud;

import java.util.ArrayList;
import java.util.List;

import com.whitefog.client.hud.DarkHudLayout;
import com.whitefog.client.hud.DarkWarningPolicy;
import com.whitefog.client.hud.DarknessBar;
import com.whitefog.client.hud.HudSourceDisplay;
import com.whitefog.darkness.SnapshotRevisionGate;
import com.whitefog.darkness.goal.DarkGoalService;
import com.whitefog.darkness.light.LightTieBreak;
import com.whitefog.darkness.light.PortableLightPolicy;

/**
 * Deterministic stage 1.9 acceptance sandbox for the compact light/goal HUD.
 *
 * <p>It compiles production PURE classes read-only (DarkGoalService, SnapshotRevisionGate,
 * LightTieBreak, client DarkHudLayout) and exercises the required behaviour. It is
 * <b>logic-only, NOT pixel/runtime proof</b>: it never renders and never touches a world.</p>
 */
public final class SelfTest {
    private static int tests;

    /** Deterministic monospace metric: 6 logical pixels per character. */
    private static final DarkHudLayout.WidthMeasure MONO = text -> text.length() * 6;

    public static void main(String[] args) {
        long start = System.nanoTime();
        try {
            revisionOrder();
            reconnectAfterClear();
            tieBreak();
            fuelCeilSeconds();
            goalOrdering();
            goalIdsAndMonotonicFlags();
            layoutGeometry();
            goalVisibility();
            fixedZonesNoOverlap();
            warningPlacement();
            textWrapping();
            ellipsizeFallback();
            animationMath();
            paletteAlpha();
            // Regression suite for the two reported HUD bugs + unified style.
            offhandSourceResolution();
            offhandValiditySemantics();
            goalEdgeContract();
            goalWrapTwoLines();
            workPanelSharedStyle();
            stage19CompactHudDisabled();
            workPanelAlwaysVisiblePolicy();
            singleRootRegistrationAndKeySeparation();
            exposureBarNormalized();
            workPanelDarknessBarLayout();
            goalTopRightPolicy();
            warningReasonPolicy();
            localizationKeys();
            System.out.println("HUD_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=SUCCESS");
        } catch (AssertionError error) {
            System.err.println("HUD_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=FAILURE");
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Snapshot revision order (requirements: 3,2,3,4 accepted as 3 and 4)
    // ------------------------------------------------------------------

    private static void revisionOrder() {
        SnapshotRevisionGate gate = new SnapshotRevisionGate();
        List<Long> accepted = new ArrayList<>();
        long[] stream = {3L, 2L, 3L, 4L};
        boolean[] expected = {true, false, true, true};
        for (int i = 0; i < stream.length; i++) {
            boolean ok = gate.shouldAccept(stream[i]);
            check(ok == expected[i], "revision " + stream[i] + " acceptance is " + expected[i]);
            if (ok) {
                accepted.add(stream[i]);
            }
        }
        check(accepted.equals(List.of(3L, 3L, 4L)),
                "accepted sequence is [3,3,4] (equal revision accepted as heartbeat)");
        check(gate.lastRevision() == 4L, "lastRevision is 4 after the stream");
        check(gate.shouldAccept(4L), "equal revision 4 is accepted as heartbeat");
        check(!gate.shouldAccept(3L), "lower revision 3 is rejected after 4");
    }

    private static void reconnectAfterClear() {
        SnapshotRevisionGate gate = new SnapshotRevisionGate();
        check(gate.shouldAccept(9L), "first session accepts revision 9");
        gate.reset();
        check(!gate.hasData(), "reset clears data");
        check(gate.shouldAccept(1L), "new session after clear accepts revision 1");
        check(gate.lastRevision() == 1L, "lastRevision is 1 after reconnect");
    }

    // ------------------------------------------------------------------
    // Nearest source ties
    // ------------------------------------------------------------------

    private static void tieBreak() {
        check(LightTieBreak.better(1, 2, 3, 2, 2, 3), "smaller x wins the tie");
        check(LightTieBreak.better(2, 1, 3, 2, 2, 3), "equal x, smaller y wins");
        check(LightTieBreak.better(2, 2, 1, 2, 2, 2), "equal x/y, smaller z wins");
        check(!LightTieBreak.better(2, 2, 3, 2, 2, 2), "larger z does not win");
        check(!LightTieBreak.better(2, 2, 2, 2, 2, 2), "identical position does not replace best");
        check(LightTieBreak.better(-1, 0, 0, 0, 0, 0), "negative x wins the tie");
    }

    // ------------------------------------------------------------------
    // Fuel conversion ceil(ticks/20)
    // ------------------------------------------------------------------

    private static void fuelCeilSeconds() {
        check(DarkHudLayout.ceilSeconds(1) == 1, "1 tick -> 1s");
        check(DarkHudLayout.ceilSeconds(19) == 1, "19 ticks -> 1s");
        check(DarkHudLayout.ceilSeconds(20) == 1, "20 ticks -> 1s");
        check(DarkHudLayout.ceilSeconds(21) == 2, "21 ticks -> 2s");
        check(DarkHudLayout.ceilSeconds(0) == 0, "0 ticks -> 0s");
        check(DarkHudLayout.ceilSeconds(-5) == 0, "negative ticks -> 0s");
        check(DarkHudLayout.ceilSeconds(12_000) == 600, "12000 ticks -> 600s");
    }

    // ------------------------------------------------------------------
    // Goal ordering and idempotency
    // ------------------------------------------------------------------

    private static void goalOrdering() {
        boolean[] flags = DarkGoalService.defaultFlags();
        check(flags.length == DarkGoalService.goalCount(), "default flags cover every goal");
        check(DarkGoalService.firstIncomplete(flags).equals(DarkGoalService.ID_FIND_OBSERVATORY),
                "first incomplete defaults to find_observatory");

        check(DarkGoalService.markCompleted(flags, DarkGoalService.ID_ACTIVATE_POSTS),
                "marking a later goal before the first succeeds");
        check(DarkGoalService.isCompleted(flags, DarkGoalService.ID_ACTIVATE_POSTS),
                "the later goal flag is preserved");
        check(DarkGoalService.firstIncomplete(flags).equals(DarkGoalService.ID_FIND_OBSERVATORY),
                "active goal does not skip the first incomplete goal");
        check(!DarkGoalService.markCompleted(flags, DarkGoalService.ID_ACTIVATE_POSTS),
                "re-marking the same goal is idempotent");

        check(DarkGoalService.markCompleted(flags, DarkGoalService.ID_FIND_OBSERVATORY),
                "marking the first goal succeeds");
        check(DarkGoalService.firstIncomplete(flags).equals(DarkGoalService.ID_BUILD_BEACON),
                "active goal advances to build_beacon once the first two are complete");
        check(DarkGoalService.completedCount(flags) == 2, "two goals completed");

        DarkGoalService.markCompleted(flags, DarkGoalService.ID_BUILD_BEACON);
        DarkGoalService.markCompleted(flags, DarkGoalService.ID_ENDURE_DARKNESS);
        DarkGoalService.markCompleted(flags, DarkGoalService.ID_COMPLETE);
        check(DarkGoalService.firstIncomplete(flags).equals(DarkGoalService.ID_COMPLETE),
                "active goal is complete only when every goal is done");
        check(DarkGoalService.completedCount(flags) == DarkGoalService.goalCount(),
                "all five goals are completed");
    }

    private static void goalIdsAndMonotonicFlags() {
        check(DarkGoalService.isKnown(DarkGoalService.ID_FIND_OBSERVATORY), "find_observatory is known");
        check(DarkGoalService.isKnown("white_fog:activate_posts"), "activate_posts is known");
        check(DarkGoalService.isKnown("white_fog:build_beacon"), "build_beacon is known");
        check(DarkGoalService.isKnown("white_fog:endure_darkness"), "endure_darkness is known");
        check(DarkGoalService.isKnown("white_fog:complete"), "complete is known");
        check(!DarkGoalService.isKnown("white_fog:bogus"), "unknown goal is rejected");
        check(!DarkGoalService.isKnown(null), "null goal is rejected");
        check(DarkGoalService.indexOf("white_fog:bogus") == -1, "unknown goal index is -1");

        boolean[] flags = DarkGoalService.defaultFlags();
        int before = DarkGoalService.completedCount(flags);
        check(!DarkGoalService.markCompleted(flags, "white_fog:bogus"), "unknown goal mark returns false");
        check(!DarkGoalService.markCompleted(flags, null), "null goal mark returns false");
        check(DarkGoalService.completedCount(flags) == before, "unknown marks leave flags unchanged");
        check(!DarkGoalService.markCompleted(null, DarkGoalService.ID_COMPLETE), "null flags are rejected");

        check(DarkGoalService.normalizeGoalId("white_fog:bogus").equals(DarkGoalService.DEFAULT_GOAL),
                "unknown stored goal falls back to find_observatory");
        check(DarkGoalService.normalizeGoalId(null).equals(DarkGoalService.DEFAULT_GOAL),
                "null stored goal falls back to find_observatory");
        check(DarkGoalService.normalizeGoalId("white_fog:build_beacon").equals("white_fog:build_beacon"),
                "known stored goal is preserved");

        check(DarkGoalService.shortName("white_fog:find_observatory").equals("find_observatory"),
                "short name strips the namespace");
        check(DarkGoalService.nbtKey("white_fog:find_observatory").equals("goal_completed_find_observatory"),
                "NBT key is derived from the short name");
    }

    // ------------------------------------------------------------------
    // Layout geometry
    // ------------------------------------------------------------------

    private static void layoutGeometry() {
        check(DarkHudLayout.HEIGHT == 18 && DarkHudLayout.PAD == 4 && DarkHudLayout.ICON == 12
                        && DarkHudLayout.TEXT_GAP == 4 && DarkHudLayout.BAR_HEIGHT == 2
                        && DarkHudLayout.ROW_GAP == 4 && DarkHudLayout.MAX_TEXT_WIDTH == 240,
                "logical constants match the ticket");
        check(DarkHudLayout.SCALE == 0.5f, "scale is 0.5");
        check(DarkHudLayout.OUTER_MARGIN == 4, "outer margin is 4 real pixels");
        check(DarkHudLayout.STATUS_BOTTOM_CLEARANCE == 44, "status bottom clearance is 44");
        check(DarkHudLayout.GOAL_MAX_WIDTH == 120, "goal max width is 120 real pixels");

        check(DarkHudLayout.rowStepLogical() == 22, "logical row step is HEIGHT+ROW_GAP");
        check(DarkHudLayout.rowHeightReal() == 9, "real row height is 18*0.5");
        check(DarkHudLayout.rowGapReal() == 2, "real row gap is 4*0.5");
        check(DarkHudLayout.barHeightReal() == 1, "real bar height is at least 1");
        check(DarkHudLayout.iconReal() == 6, "real icon box is 12*0.5");

        check(DarkHudLayout.realHeight(4) == 42, "four-row status panel is 42 real pixels tall");
        check(DarkHudLayout.realPanelWidth(0) == 12, "empty text panel is 12 real pixels wide");
        check(DarkHudLayout.realPanelWidth(100) == 62, "text 100 -> logical 124 -> 62 real");
        check(DarkHudLayout.realPanelWidth(1000) == 132,
                "text capped at 240 logical -> (24+240)*0.5 = 132 real");
        check(DarkHudLayout.goalTextMaxLogical() == 232, "goal text logical cap is 232");
        check(DarkHudLayout.goalPanelWidth(1000) == 120, "goal panel is capped at 120 real pixels");
        check(DarkHudLayout.goalPanelHeight(2) == 20, "two-line goal panel is 20 real pixels");
        check(DarkHudLayout.statusTop(720, 4) == 634, "status top at 720p with four rows");
        check(DarkHudLayout.statusX() == 4, "status x is 4");
    }

    private static void goalVisibility() {
        check(DarkHudLayout.goalVisible(320, 180), "goal visible at the minimum 320x180");
        check(!DarkHudLayout.goalVisible(319, 180), "goal hidden below 320 wide");
        check(!DarkHudLayout.goalVisible(320, 179), "goal hidden below 180 tall");
        check(DarkHudLayout.goalVisible(1280, 720), "goal visible at 1280x720");
        check(DarkHudLayout.goalVisible(1920, 1080), "goal visible at 1920x1080");
    }

    private static void fixedZonesNoOverlap() {
        int panelWidth = DarkHudLayout.realPanelWidth(120);
        int goalX = DarkHudLayout.goalX(320, DarkHudLayout.goalPanelWidth(120));
        int goalRight = goalX + DarkHudLayout.goalPanelWidth(120);
        check(goalX == 320 - 4 - DarkHudLayout.goalPanelWidth(120), "goal panel is right-aligned");
        check(goalRight == 320 - 4, "goal panel right edge respects the outer margin");

        int goalBottom = DarkHudLayout.goalY() + DarkHudLayout.goalPanelHeight(2);
        int statusTop = DarkHudLayout.statusTop(180, DarkHudLayout.STATUS_ROWS);
        check(goalBottom <= statusTop, "goal (top-right) never overlaps status (bottom-left) at 320x180");
        check(goalX > DarkHudLayout.statusX() + panelWidth,
                "goal and status occupy different horizontal zones");
    }

    private static void warningPlacement() {
        check(DarkHudLayout.warningX(100, 20) == 40, "warning is horizontally centered");
        check(DarkHudLayout.warningY(100) == 68, "warning y is height/2 + 18");
        check(DarkHudLayout.warningY(180) == 108, "warning y scales with the screen height");
    }

    private static void textWrapping() {
        String[] shortLine = DarkHudLayout.wrapGoal("AB", 60, MONO);
        check(shortLine.length == 1 && shortLine[0].equals("AB"), "short goal stays on one line");

        String[] wrapped = DarkHudLayout.wrapGoal("AAAA BBBB CCCC DDDD EEEE", 60, MONO);
        check(wrapped.length == 2, "long goal wraps into exactly two lines");
        check(wrapped[0].equals("AAAA BBBB"), "first line keeps whole words that fit");
        check(wrapped[1].endsWith("…"), "second line is ellipsized");
        check(MONO.width(wrapped[1]) <= 60, "second line respects the wrap width");

        String[] singleWord = DarkHudLayout.wrapGoal("AAAAAAAAAA", 30, MONO);
        check(singleWord.length == 1, "an unbreakable word stays on one line");
        check(singleWord[0].endsWith("…"), "unbreakable word is ellipsized");
        check(MONO.width(singleWord[0]) <= 30, "ellipsized word respects the width");
    }

    private static void ellipsizeFallback() {
        check(DarkHudLayout.ellipsize("", 100, MONO).isEmpty(), "empty text ellipsizes to empty");
        check(DarkHudLayout.ellipsize("AB", 0, MONO).isEmpty(), "non-positive width yields empty");
        check(DarkHudLayout.ellipsize("AB", 100, MONO).equals("AB"), "text that fits is unchanged");
        check(DarkHudLayout.ellipsize("ABCDEFGH", 30, MONO).endsWith("…"), "long text gets an ellipsis");
        String[] array = DarkHudLayout.wrapGoal(null, 60, MONO);
        check(array.length == 1 && array[0].isEmpty(), "null goal text yields one empty line");
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    private static void animationMath() {
        check(DarkHudLayout.clampDt(-1.0) == 0.0, "negative dt clamps to 0");
        check(DarkHudLayout.clampDt(0.0) == 0.0, "zero dt stays 0");
        check(DarkHudLayout.clampDt(0.016) == 0.016, "60 FPS dt passes through");
        check(DarkHudLayout.clampDt(0.5) == DarkHudLayout.MAX_DT_SECONDS, "long frame clamps to 0.05");
        check(DarkHudLayout.MAX_DT_SECONDS == 0.05, "max dt is 0.05s");

        double step = DarkHudLayout.APPROACH_SPEED * DarkHudLayout.clampDt(0.05);
        check(darkEquals(DarkHudLayout.approach(0.0, 1.0, step), 0.4), "approach moves by 8*dt per frame");
        check(darkEquals(DarkHudLayout.approach(0.0, 1.0, 0.0), 0.0), "zero speed does not move");
        check(darkEquals(DarkHudLayout.approach(1.0, 0.0, 0.4), 0.6), "approach moves down by maxDelta");
        check(darkEquals(DarkHudLayout.approach(0.9, 1.0, 0.4), 1.0), "approach snaps when within maxDelta");
        check(darkEquals(DarkHudLayout.approach(0.5, 0.5, 0.4), 0.5), "approach keeps equal values");
        check(DarkHudLayout.approachAlpha(0, 255, 100.0) == 100, "alpha approach is shared math");
        check(DarkHudLayout.approachAlpha(0, 255, 255.0) == 255, "alpha snaps within maxDelta");
    }

    private static void paletteAlpha() {
        check(DarkHudLayout.COLOR_BACKGROUND == 0xCC18212B, "background is #18212B at ~80% alpha");
        check(DarkHudLayout.COLOR_ACCENT == 0xFFA8D8A8, "accent palette color");
        check(DarkHudLayout.COLOR_DANGER == 0xFFC77878, "danger palette color");
        check(DarkHudLayout.COLOR_TEXT == 0xFFDBE5E9, "text palette color");
        check(DarkHudLayout.withAlpha(0xFFA8D8A8, 128) == 0x80A8D8A8, "withAlpha replaces only alpha");
        check(DarkHudLayout.clampAlpha(-5) == 0, "alpha clamps at 0");
        check(DarkHudLayout.clampAlpha(300) == 255, "alpha clamps at 255");
    }

    // ------------------------------------------------------------------
    // Offhand source display (regression: charged burning torch in offhand
    // was not shown by the compact status panel)
    // ------------------------------------------------------------------

    private static void offhandSourceResolution() {
        // No placed source + valid burning offhand torch -> offhand becomes the displayed source.
        HudSourceDisplay.Resolved torch = HudSourceDisplay.resolve(false, -1, false, -1, true, 0, 12_000);
        check(torch.origin() == HudSourceDisplay.Origin.OFFHAND,
                "offhand becomes the displayed source when no placed source exists");
        check(torch.kindOrdinal() == 0, "offhand torch resolves to kind ordinal 0");
        check(torch.fuelSeconds() == 600, "offhand remaining 12000 -> ceil(12000/20) = 600s");
        check(torch.lit(), "offhand source is reported lit");

        HudSourceDisplay.Resolved soul = HudSourceDisplay.resolve(false, -1, false, -1, true, 1, 19);
        check(soul.origin() == HudSourceDisplay.Origin.OFFHAND, "offhand soul torch becomes displayed source");
        check(soul.kindOrdinal() == 1, "offhand soul torch kind ordinal 1");
        check(soul.fuelSeconds() == 1, "remaining 19 ticks -> ceil 1s");
        check(HudSourceDisplay.resolve(false, -1, false, -1, true, 0, 1).fuelSeconds() == 1,
                "remaining 1 tick -> ceil 1s");
        check(HudSourceDisplay.resolve(false, -1, false, -1, true, 0, 20).fuelSeconds() == 1,
                "remaining 20 ticks -> 1s");
        check(HudSourceDisplay.resolve(false, -1, false, -1, true, 0, 21).fuelSeconds() == 2,
                "remaining 21 ticks -> 2s");

        // Placed source has defined priority and wins over a valid offhand.
        HudSourceDisplay.Resolved placed = HudSourceDisplay.resolve(true, 2, true, 500, true, 0, 12_000);
        check(placed.origin() == HudSourceDisplay.Origin.PLACED, "placed source wins over a valid offhand");
        check(placed.kindOrdinal() == 2, "placed kind ordinal is preserved");
        check(placed.fuelSeconds() == 25, "placed remaining 500 -> 25s");

        // Empty / unlit / corrupt offhand must not claim a source.
        check(!HudSourceDisplay.resolve(false, -1, false, -1, false, 0, 12_000).present(),
                "an invalid offhand does not claim a source");
        check(!HudSourceDisplay.resolve(false, -1, false, -1, true, 0, 0).present(),
                "a zero-fuel offhand does not claim a source");
        check(!HudSourceDisplay.resolve(false, -1, false, -1, true, -1, 0).present(),
                "a zero-fuel, unknown-kind offhand does not claim a source");
        check(!HudSourceDisplay.resolve(false, -1, false, -1, false, -1, -1).present(),
                "neither placed nor offhand -> no source");
        check(HudSourceDisplay.NONE.origin() == HudSourceDisplay.Origin.NONE, "NONE is the absent source");
        check(HudSourceDisplay.NONE.fuelSeconds() == 0, "absent source exposes zero fuel seconds");

        // A placed source reported with no remaining fuel (negative) is treated as absent,
        // so the offhand can still light the panel.
        HudSourceDisplay.Resolved fallback = HudSourceDisplay.resolve(false, -1, false, -1, true, 1, 8_000);
        check(fallback.origin() == HudSourceDisplay.Origin.OFFHAND, "offhand lights the panel when no placed fuel");
    }

    private static void offhandValiditySemantics() {
        // Reuse production PortableLightPolicy (compiled read-only) — no divergent formula.
        check(PortableLightPolicy.kindForItemId("minecraft:torch") == PortableLightPolicy.Kind.TORCH,
                "torch is a recognized portable light");
        check(PortableLightPolicy.kindForItemId("minecraft:wall_torch") == PortableLightPolicy.Kind.TORCH,
                "wall torch maps to the torch kind");
        check(PortableLightPolicy.kindForItemId("minecraft:soul_torch") == PortableLightPolicy.Kind.SOUL_TORCH,
                "soul torch is a recognized portable light");
        check(PortableLightPolicy.kindForItemId("minecraft:soul_wall_torch") == PortableLightPolicy.Kind.SOUL_TORCH,
                "soul wall torch maps to the soul torch kind");
        check(PortableLightPolicy.kindForItemId("minecraft:lantern") == null,
                "lantern is not a portable light");
        check(PortableLightPolicy.kindForItemId("minecraft:campfire") == null,
                "campfire is not a portable light");
        check(PortableLightPolicy.kindForItemId(null) == null, "null item id is not a portable light");

        check(PortableLightPolicy.active(true, false, true, 5), "valid burning offhand is active");
        check(!PortableLightPolicy.active(true, false, false, 5), "unlit offhand is inactive");
        check(!PortableLightPolicy.active(true, false, true, 0), "empty offhand is inactive");
        check(!PortableLightPolicy.active(true, true, true, 5), "corrupt count>1 offhand is inactive");
        check(!PortableLightPolicy.active(false, false, true, 5), "absent offhand is inactive");
        check(PortableLightPolicy.isCorrupt(10, 2), "a charged stack with count>1 is corrupt");
        check(!PortableLightPolicy.isCorrupt(10, 1), "a charged stack with count==1 is not corrupt");
        check(!PortableLightPolicy.isCorrupt(null, 2), "an empty component is not corrupt");
        check(PortableLightPolicy.normalize(99_999, 12_000) == 12_000, "component value clamps to capacity");
        check(PortableLightPolicy.normalize(null, 12_000) == 0, "null component normalizes to 0");
    }

    // ------------------------------------------------------------------
    // Goal boundary contract (regression: goal text overran the panel right edge)
    // ------------------------------------------------------------------

    private static void goalEdgeContract() {
        // Root cause: the goal has no icon, but the old text X used PAD + ICON + TEXT_GAP while
        // the wrap/panel width did not reserve that space -> the text right edge exceeded the panel.
        int maxContent = DarkHudLayout.goalTextMaxLogical();
        int panelLogicalAtMax = DarkHudLayout.goalPanelLogicalWidth(maxContent);
        int oldTextX = DarkHudLayout.PAD + DarkHudLayout.ICON + DarkHudLayout.TEXT_GAP;
        check(oldTextX + maxContent > panelLogicalAtMax,
                "regression proof: the old icon+gap text X exceeded the goal panel logical width");
        check(DarkHudLayout.goalTextX() == DarkHudLayout.PAD,
                "goal text X is PAD only (no unused icon/gap)");
        check(DarkHudLayout.goalTextX() + maxContent <= panelLogicalAtMax,
                "fix: goal text X + max content fits the goal panel logical width");

        String[] goals = {
                "Найди обсерваторию",
                "Переживи ночь",
                "Активируй все четыре фонарных поста обсерватории до наступления финала и построй маяк",
                "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW"
        };
        int[][] screens = { { 320, 180 }, { 1280, 720 }, { 1920, 1080 } };
        for (int[] screen : screens) {
            for (String goal : goals) {
                String[] lines = DarkHudLayout.wrapGoal(goal, DarkHudLayout.goalTextMaxLogical(), MONO);
                check(lines.length <= DarkHudLayout.GOAL_MAX_LINES,
                        "goal wraps to at most two lines at " + screen[0] + "x" + screen[1]);
                int content = 0;
                for (String line : lines) {
                    content = Math.max(content, MONO.width(line));
                }
                check(content <= DarkHudLayout.goalTextMaxLogical(),
                        "wrapped goal content obeys the text cap");
                int panelLogical = DarkHudLayout.goalPanelLogicalWidth(content);
                check(DarkHudLayout.goalTextX() + content <= panelLogical,
                        "text X + content fits the panel logical width");
                int panelReal = DarkHudLayout.goalPanelWidth(content);
                check(panelReal <= DarkHudLayout.GOAL_MAX_WIDTH, "goal panel is capped at 120 real px");
                int goalLeft = DarkHudLayout.goalX(screen[0], panelReal);
                int panelRight = goalLeft + panelReal;
                check(panelRight <= screen[0] - DarkHudLayout.OUTER_MARGIN,
                        "goal panel respects the right screen margin");
                for (String line : lines) {
                    int textRight = goalLeft
                            + Math.round((DarkHudLayout.goalTextX() + MONO.width(line)) * DarkHudLayout.SCALE);
                    check(textRight <= panelRight,
                            "goal text stays inside the panel at " + screen[0] + "x" + screen[1]);
                }
            }
        }
    }

    private static void goalWrapTwoLines() {
        // Long localized text must wrap to exactly two lines and the second line obeys the width.
        String longGoal = "Активируй все четыре фонарных поста обсерватории до наступления финала и построй маяк";
        String[] lines = DarkHudLayout.wrapGoal(longGoal, DarkHudLayout.goalTextMaxLogical(), MONO);
        check(lines.length == 2, "a long localized goal wraps into two lines");
        check(lines[1].endsWith("…"), "the wrapped second line ends with an ellipsis");
        check(MONO.width(lines[0]) <= DarkHudLayout.goalTextMaxLogical(),
                "the first wrapped line obeys the available width");
        check(MONO.width(lines[1]) <= DarkHudLayout.goalTextMaxLogical(),
                "the ellipsized second line obeys the available width");

        String oneWord = "Неразрывноесловокотороеникакневлезаетдаженет";
        String[] single = DarkHudLayout.wrapGoal(oneWord, DarkHudLayout.goalTextMaxLogical(), MONO);
        check(MONO.width(oneWord) > DarkHudLayout.goalTextMaxLogical(),
                "the unbreakable localized word really exceeds the width cap");
        check(single.length == 1, "an unbreakable localized word stays on one line");
        check(single[0].endsWith("…"), "an unbreakable localized word is ellipsized");
        check(MONO.width(single[0]) <= DarkHudLayout.goalTextMaxLogical(),
                "the ellipsized unbreakable word obeys the available width");
    }

    // ------------------------------------------------------------------
    // Unified style: the G work panel shares the status/goal geometry + palette
    // ------------------------------------------------------------------

    private static void workPanelSharedStyle() {
        check(DarkHudLayout.WORK_PANEL_ROWS == 7,
                "the G work panel has seven rows (title + darkness bar + five info rows)");
        check(DarkHudLayout.workPanelX() == DarkHudLayout.OUTER_MARGIN,
                "the G work panel uses the shared outer margin");
        check(DarkHudLayout.workPanelTextMaxLogical() == DarkHudLayout.MAX_TEXT_WIDTH,
                "the G work panel uses the shared max text width");
        check(DarkHudLayout.workPanelTop(720, DarkHudLayout.WORK_PANEL_ROWS)
                        == DarkHudLayout.statusTop(720, DarkHudLayout.WORK_PANEL_ROWS),
                "the G work panel shares the status bottom clearance");
        check(DarkHudLayout.workPanelHeight() == DarkHudLayout.realHeight(DarkHudLayout.WORK_PANEL_ROWS),
                "the G work panel height follows the shared row geometry");
        check(DarkHudLayout.workPanelWidth(0) == DarkHudLayout.realPanelWidth(0),
                "the G work panel uses the shared content-sized width");
        check(!DarkHudLayout.statusVisibleWithWorkPanel(true),
                "the compact status hides while the G work panel is visible (no overlap)");
        check(DarkHudLayout.statusVisibleWithWorkPanel(false),
                "the compact status shows when the G work panel is hidden");

        // One shared palette for status / goal / warning / work panel.
        check(DarkHudLayout.COLOR_BACKGROUND == 0xCC18212B
                        && DarkHudLayout.COLOR_ACCENT == 0xFFA8D8A8
                        && DarkHudLayout.COLOR_DANGER == 0xFFC77878
                        && DarkHudLayout.COLOR_TEXT == 0xFFDBE5E9,
                "all panels share the single DarkHudLayout palette");
        check(DarkHudLayout.PAD == 4 && DarkHudLayout.ROW_GAP == 4 && DarkHudLayout.HEIGHT == 18
                        && DarkHudLayout.SCALE == 0.5f,
                "all panels share PAD / ROW_GAP / HEIGHT / SCALE conventions");
    }

    // ------------------------------------------------------------------
    // Stage 1.9 display-mode change (user request): compact HUD hidden,
    // the G work panel is shown permanently and G stays a separate action.
    // ------------------------------------------------------------------

    private static void stage19CompactHudDisabled() {
        check(!DarkHudLayout.shouldShowCompactStageHud(),
                "stage 1.9 compact HUD is disabled by the user request");
        check(!DarkHudLayout.compactStageHudVisible(false, true),
                "compact HUD stays hidden even with server data in normal gameplay");
        check(!DarkHudLayout.compactStageHudVisible(false, false),
                "compact HUD stays hidden without a snapshot");
        check(!DarkHudLayout.compactStageHudVisible(true, true),
                "compact HUD stays hidden when the gameplay HUD is hidden");
    }

    private static void workPanelAlwaysVisiblePolicy() {
        check(DarkHudLayout.workPanelAlwaysVisible(),
                "the G work panel is always visible in normal gameplay");
        check(DarkHudLayout.workPanelVisible(false),
                "the G work panel is visible regardless of the G key (no key argument exists)");
        check(!DarkHudLayout.workPanelVisible(true),
                "the G work panel is hidden only when the whole gameplay HUD is hidden");
    }

    // ------------------------------------------------------------------
    // User request: darkness/exposure bar inside the permanent work panel
    // ------------------------------------------------------------------

    private static void exposureBarNormalized() {
        check(darkEquals(DarkHudLayout.exposureNormalized(0), 0.0), "exposure 0 -> bar 0.0");
        check(darkEquals(DarkHudLayout.exposureNormalized(50), 0.5), "exposure 50 -> bar 0.5");
        check(darkEquals(DarkHudLayout.exposureNormalized(100), 1.0), "exposure 100 -> bar 1.0");
        check(darkEquals(DarkHudLayout.exposureNormalized(-25), 0.0), "negative exposure clamps to 0");
        check(darkEquals(DarkHudLayout.exposureNormalized(250), 1.0), "exposure above 100 clamps to 1");

        DarknessBar bar = new DarknessBar();
        check(darkEquals(bar.value(), 0.0), "the bar starts empty");
        bar.updateAnimations(50, DarkHudLayout.MAX_DT_SECONDS);
        check(darkEquals(bar.value(), 0.4), "bar approaches by APPROACH_SPEED*dt per frame");
        bar.updateAnimations(50, DarkHudLayout.MAX_DT_SECONDS);
        check(darkEquals(bar.value(), 0.5), "bar snaps once within the approach step");
        bar.updateAnimations(100, DarkHudLayout.MAX_DT_SECONDS);
        check(darkEquals(bar.value(), 0.9), "bar rises toward exposure 1.0");
        bar.updateAnimations(100, DarkHudLayout.MAX_DT_SECONDS);
        check(darkEquals(bar.value(), 1.0), "bar reaches 1.0");
        bar.updateAnimations(-10, DarkHudLayout.MAX_DT_SECONDS);
        check(darkEquals(bar.value(), 0.6), "bar falls toward 0.0");
        check(bar.filledWidth(100) == Math.round(92 * 0.6), "filled width uses the shared track width");
        bar.reset();
        check(darkEquals(bar.value(), 0.0), "reset empties the bar");
    }

    private static void workPanelDarknessBarLayout() {
        check(DarkHudLayout.WORK_PANEL_ROWS == 7, "the work panel has seven rows (title + bar + five)");
        check(DarkHudLayout.darknessBarRow() == 1, "the darkness bar sits in row 1");
        int rowStart = DarkHudLayout.darknessBarRow() * DarkHudLayout.rowStepLogical();
        int barY = DarkHudLayout.darknessBarLogicalY();
        check(barY >= rowStart, "the bar is inside its own row");
        check(barY + DarkHudLayout.BAR_HEIGHT <= rowStart + DarkHudLayout.HEIGHT,
                "the bar does not spill into the next row");
        check(barY + DarkHudLayout.BAR_HEIGHT <= DarkHudLayout.logicalHeight(DarkHudLayout.WORK_PANEL_ROWS),
                "the bar is inside the panel logical height");
        check(DarkHudLayout.darknessBarTrackWidth(100) == 92, "track width = panel width - 2*PAD");
        check(DarkHudLayout.darknessBarTrackWidth(4) >= 1, "track width is at least one pixel");

        int[] heights = { 180, 720, 1080 };
        for (int height : heights) {
            int panelTop = DarkHudLayout.workPanelTop(height, DarkHudLayout.WORK_PANEL_ROWS);
            int barBottomReal = panelTop + Math.round(
                    (DarkHudLayout.darknessBarLogicalY() + DarkHudLayout.BAR_HEIGHT) * DarkHudLayout.SCALE);
            check(barBottomReal <= height - DarkHudLayout.STATUS_BOTTOM_CLEARANCE,
                    "the bar stays above the hotbar zone at height " + height);
        }

        // The permanent work panel (bottom-left) and the goal (top-right) never overlap at 320x180.
        int workRight = DarkHudLayout.workPanelX()
                + DarkHudLayout.workPanelWidth(DarkHudLayout.MAX_TEXT_WIDTH);
        int goalLeft = DarkHudLayout.goalX(320, DarkHudLayout.goalPanelWidth(DarkHudLayout.goalTextMaxLogical()));
        check(workRight < goalLeft, "the work panel and the goal occupy different horizontal zones");
    }

    // ------------------------------------------------------------------
    // User request: the current-goal panel is back in the top-right corner
    // ------------------------------------------------------------------

    private static void goalTopRightPolicy() {
        check(DarkHudLayout.stageGoalVisible(false, true, 320, 180), "goal visible with data at 320x180");
        check(DarkHudLayout.stageGoalVisible(false, true, 1280, 720), "goal visible at 1280x720");
        check(DarkHudLayout.stageGoalVisible(false, true, 1920, 1080), "goal visible at 1920x1080");
        check(!DarkHudLayout.stageGoalVisible(true, true, 320, 180),
                "goal hidden when the whole gameplay HUD is hidden");
        check(!DarkHudLayout.stageGoalVisible(false, false, 320, 180), "goal hidden without a server snapshot");
        check(!DarkHudLayout.stageGoalVisible(false, true, 319, 180), "goal hidden below 320 wide");
        check(!DarkHudLayout.stageGoalVisible(false, true, 320, 179), "goal hidden below 180 tall");

        int[][] screens = { { 320, 180 }, { 1280, 720 }, { 1920, 1080 } };
        for (int[] screen : screens) {
            int panelReal = DarkHudLayout.goalPanelWidth(DarkHudLayout.goalTextMaxLogical());
            int left = DarkHudLayout.goalX(screen[0], panelReal);
            check(left + panelReal <= screen[0] - DarkHudLayout.OUTER_MARGIN,
                    "goal right edge keeps the 4px margin at " + screen[0] + "x" + screen[1]);
            check(DarkHudLayout.goalY() == DarkHudLayout.OUTER_MARGIN, "goal top margin is 4px");
            check(left > DarkHudLayout.workPanelX(),
                    "goal starts right of the work panel at " + screen[0] + "x" + screen[1]);
        }
    }

    // ------------------------------------------------------------------
    // User request: warnings are action-bar messages, not a HUD window
    // ------------------------------------------------------------------

    private static void warningReasonPolicy() {
        check(DarkWarningPolicy.reasonFor(0) == DarkWarningPolicy.REASON_NONE, "exposure 0 -> NONE");
        check(DarkWarningPolicy.reasonFor(74) == DarkWarningPolicy.REASON_NONE, "exposure 74 -> NONE");
        check(DarkWarningPolicy.reasonFor(75) == DarkWarningPolicy.REASON_FIND_LIGHT, "exposure 75 -> FIND_LIGHT");
        check(DarkWarningPolicy.reasonFor(89) == DarkWarningPolicy.REASON_FIND_LIGHT, "exposure 89 -> FIND_LIGHT");
        check(DarkWarningPolicy.reasonFor(90) == DarkWarningPolicy.REASON_DRAIN, "exposure 90 -> DRAIN");
        check(DarkWarningPolicy.reasonFor(100) == DarkWarningPolicy.REASON_DRAIN, "exposure 100 -> DRAIN");
        check(DarkWarningPolicy.COOLDOWN_TICKS == 40, "visual cooldown is 40 ticks");

        DarkWarningPolicy policy = new DarkWarningPolicy();
        check(policy.update(0, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "NONE is never emitted");
        check(policy.update(74, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "exposure below the threshold emits nothing");

        // Change to FIND_LIGHT starts the 40-tick cooldown; the first emission is on tick 40.
        check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "the change tick emits nothing");
        for (int i = 0; i < 38; i++) {
            check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                    "no notification during the cooldown (tick " + (i + 2) + ")");
        }
        check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_FIND_LIGHT,
                "tick 40 emits FIND_LIGHT once");
        check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "the same reason is not re-emitted (no spam)");
        check(policy.update(89, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "staying in FIND_LIGHT emits nothing");

        // Rising to DRAIN changes the reason -> a fresh 40-tick cooldown, then one emission.
        check(policy.update(95, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "the DRAIN change tick emits nothing");
        for (int i = 0; i < 38; i++) {
            check(policy.update(95, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                    "DRAIN waits for its cooldown (tick " + (i + 2) + ")");
        }
        check(policy.update(95, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_DRAIN,
                "tick 40 emits DRAIN once");
        check(policy.update(95, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "DRAIN does not spam");

        // Falling back to NONE emits nothing and re-arms for a later FIND_LIGHT.
        for (int i = 0; i < 40; i++) {
            check(policy.update(0, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                    "NONE never notifies (tick " + (i + 1) + ")");
        }
        check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_NONE,
                "the re-entry change tick emits nothing");
        for (int i = 0; i < 38; i++) {
            policy.update(80, DarkHudLayout.MAX_DT_SECONDS);
        }
        check(policy.update(80, DarkHudLayout.MAX_DT_SECONDS) == DarkWarningPolicy.REASON_FIND_LIGHT,
                "re-entering FIND_LIGHT notifies again");

        // Frame-rate independence: at 60 FPS the emission still needs ~40 ticks (2 seconds).
        DarkWarningPolicy fps = new DarkWarningPolicy();
        double frame = 1.0 / 60.0;
        int emitted = DarkWarningPolicy.REASON_NONE;
        for (int i = 0; i < 119; i++) {
            emitted = fps.update(80, frame);
            check(emitted == DarkWarningPolicy.REASON_NONE,
                    "60fps: nothing before 40 ticks (frame " + (i + 1) + ")");
        }
        for (int i = 119; i < 130 && emitted == DarkWarningPolicy.REASON_NONE; i++) {
            emitted = fps.update(80, frame);
        }
        check(emitted == DarkWarningPolicy.REASON_FIND_LIGHT, "60fps: emits after the 40-tick cooldown");

        DarkWarningPolicy reset = new DarkWarningPolicy();
        reset.update(95, DarkHudLayout.MAX_DT_SECONDS);
        reset.reset();
        check(reset.activeReason() == DarkWarningPolicy.REASON_NONE, "reset clears the active reason");
        check(reset.cooldownTicksRemaining() == 0, "reset clears the cooldown");
    }

    private static void singleRootRegistrationAndKeySeparation() {
        // no second registration: exactly one HudElementRegistry.addLast across client sources
        int registrations = 0;
        String registrationsFile = null;
        for (java.nio.file.Path file : clientJavaFiles()) {
            String text = read(file);
            int index = text.indexOf("HudElementRegistry.addLast");
            while (index >= 0) {
                registrations++;
                registrationsFile = file.toString().replace('\\', '/');
                index = text.indexOf("HudElementRegistry.addLast", index + 1);
            }
        }
        check(registrations == 1, "exactly one HudElementRegistry.addLast root registration exists");
        check(registrationsFile != null && registrationsFile.endsWith("hud/WhiteFogHud.java"),
                "the single root registration lives in WhiteFogHud.java");

        // the compact stage-1.9 widgets remain constructed (data/classes kept for the future)
        String hud = read(java.nio.file.Path.of("src", "client", "java", "com", "whitefog", "client", "hud",
                "WhiteFogHud.java"));
        check(hud.contains("new LightWidget") && hud.contains("new ExposureWidget")
                        && hud.contains("new GoalWidget") && hud.contains("new WarningWidget"),
                "the compact stage-1.9 widgets are still constructed for future use");
        check(hud.contains("stageGoalVisible"),
                "the HUD root shows the goal via the pure top-right visibility policy");
        check(hud.contains("workPanelVisible") || hud.contains("workPanelAlwaysVisible"),
                "the HUD root uses the always-visible work-panel policy");

        // warnings are NOT a HUD window anymore (user request): no rectangle, action-bar instead
        check(!hud.contains("warningWidget.render") && !hud.contains("warningWidget.updateAnimations")
                        && !hud.contains("warningWidget.setVisible"),
                "the root no longer renders/updates the warning widget");
        check(!hud.contains("warningX(") && !hud.contains("warningY("),
                "the root draws no warning rectangle (no warningX/warningY usage)");
        check(hud.contains("warningPolicy.update") && hud.contains("sendOverlayMessage"),
                "the root updates the pure warning policy and sends a LocalPlayer overlay message");
        check(hud.contains("REASON_FIND_LIGHT") && hud.contains("REASON_DRAIN"),
                "the root maps the warning reasons to localized components");
        boolean anyWarningRender = false;
        for (java.nio.file.Path file : clientJavaFiles()) {
            String text = read(file);
            if (text.contains("warningWidget.render") || text.contains("warningWidget.updateAnimations")
                    || text.contains("warningWidget.setVisible")) {
                anyWarningRender = true;
            }
        }
        check(!anyWarningRender, "no client source renders/updates the warning widget");

        // the G work panel is not key-gated: the root must not read the key
        check(!hud.contains("lightPanelKey"),
                "the HUD root does not read the G key (panel visibility is not key-gated)");

        // G remains a separate quick action: consumeClick + C2S refuel payload untouched
        String client = read(java.nio.file.Path.of("src", "client", "java", "com", "whitefog", "client",
                "WhiteFogClient.java"));
        check(client.contains("consumeClick()"), "G remains a separate consumeClick action");
        check(client.contains("LightRefuelPayload"), "G still sends the C2S refuel payload");

        // gameplay hide conditions remain in the single root
        check(hud.contains("isDeadOrDying") && hud.contains("isSpectator")
                        && hud.contains("hud.isHidden") && hud.contains("gui.screen() != null"),
                "the gameplay hide conditions (death/spectator/F1/Screen) remain in the root");
    }

    private static List<java.nio.file.Path> clientJavaFiles() {
        List<java.nio.file.Path> files = new ArrayList<>();
        java.nio.file.Path clientRoot = java.nio.file.Path.of("src", "client", "java");
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(clientRoot)) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
        } catch (java.io.IOException e) {
            throw new AssertionError("cannot walk client sources", e);
        }
        return files;
    }

    private static void localizationKeys() {
        String ru = read(java.nio.file.Path.of("src", "main", "resources", "assets", "white_fog", "lang", "ru_ru.json"));
        String en = read(java.nio.file.Path.of("src", "main", "resources", "assets", "white_fog", "lang", "en_us.json"));
        String[] keys = {
                "hud.white_fog.source.held",
                "hud.white_fog.source.kind.torch",
                "hud.white_fog.source.kind.soul_torch",
                "hud.white_fog.source.none",
                "hud.white_fog.fuel",
                "hud.white_fog.dark",
                "hud.white_fog.warning.find_light",
                "hud.white_fog.warning.drain",
                "goal.white_fog.find_observatory",
                "goal.white_fog.activate_posts",
                "goal.white_fog.build_beacon",
                "goal.white_fog.endure_darkness",
                "goal.white_fog.complete"
        };
        for (String key : keys) {
            check(ru.contains("\"" + key + "\""), "ru_ru localizes " + key);
            check(en.contains("\"" + key + "\""), "en_us localizes " + key);
        }
        check(!ru.contains("minecraft:torch"), "ru_ru does not expose raw item ids");
        check(!en.contains("minecraft:torch"), "en_us does not expose raw item ids");
        check(containsCyrillic(ru), "ru_ru is Cyrillic-localized");
        check(!containsCyrillic(en), "en_us is not Cyrillic-localized");
    }

    private static String read(java.nio.file.Path path) {
        try {
            return java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("cannot read " + path, e);
        }
    }

    private static boolean containsCyrillic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0400' && c <= '\u04FF') {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean darkEquals(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }

    private static void check(boolean value, String message) {
        tests++;
        if (!value) {
            throw new AssertionError(message);
        }
        System.out.println("PASS " + tests + " " + message);
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
