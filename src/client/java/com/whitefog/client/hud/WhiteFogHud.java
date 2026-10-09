package com.whitefog.client.hud;

import com.whitefog.WhiteFog;
import com.whitefog.client.ClientDarknessState;
import com.whitefog.client.ClientLightState;
import com.whitefog.client.ClientPlayerState;
import com.whitefog.darkness.goal.DarkGoalService;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Единственный корневой HUD мода «Белая Мгла» (этап 1.5/1.6/1.9).
 *
 * <p>Поверх Fabric {@link HudElementRegistry} регистрируется РОВНО ОДИН корневой элемент. Он
 * владеет виджетами — статус-панель ({@link LightWidget} + {@link ExposureWidget}), цель
 * ({@link GoalWidget}), предупреждение ({@link WarningWidget}) и рабочая панель света
 * ({@link LightWorkPanelHud}) — и вызывает их фазовые методы:</p>
 * <ol>
 *     <li>{@code beginFrame}: один раз на кадр считается реальный dt из frame-delta, ограниченный
 *         0..0.05 c, и обновляются анимации виджетов;</li>
 *     <li>фазы {@code renderBackground} → {@code renderIcons} → {@code renderText};</li>
 *     <li>поза гарантированно сбрасывается ({@code pushMatrix}/{@code popMatrix}) после каждого
 *         панельного блока.</li>
 * </ol>
 *
 * <h2>Зоны и приоритеты</h2>
 * <ul>
 *     <li>Work Panel — единственная нижняя панель (слева-снизу), видна постоянно в gameplay;</li>
 *     <li>панель «Текущее задание» — справа-сверху, не шире 120 реальных px; скрывается при ширине
 *         &lt;320 или высоте &lt;180 и без серверного снимка;</li>
 *     <li>предупреждения о тьме — НЕ окно HUD: при смене серверной причины после визуального
 *         cooldown 40 тиков шлётся обычное action-bar сообщение ({@link LocalPlayer#sendOverlayMessage});</li>
 *     <li>весь кастомный HUD скрыт при F1, spectator, смерти и любом открытом {@code Screen}.</li>
 * </ul>
 *
 * <p>Скрытое/без-снимка состояние НЕ показывает выдуманные нули: виджеты не рисуются, пока от
 * сервера не пришёл первый снимок (цель) или пока игрок не в gameplay. Ванильные сердца/голод не
 * скрываются.</p>
 *
 * <h2>Текущий режим отображения (запросы пользователя)</h2>
 * <p>Компактный статус этапа 1.9 (строки «Свет/Тьма/Укрытие/Источник») остаётся <b>выключенным</b>:
 * виджеты по-прежнему конструируются и обновляют анимации, но не рисуются — контракт зафиксирован
 * чистой политикой {@link DarkHudLayout#shouldShowCompactStageHud()}/
 * {@link DarkHudLayout#compactStageHudVisible}. Вместо него постоянно, в обычном gameplay, показывается
 * работа панель света {@link LightWorkPanelHud} (в неё добавлена тонкая полоска тьмы). Отдельная
 * панель «Текущее задание» возвращена в правый верхний угол через {@link GoalWidget} и политику
 * {@link DarkHudLayout#stageGoalVisible}. Предупреждения {@link WarningWidget} больше не рисуются:
 * при {@code exposure >= 75}/{@code >= 90} отправляется action-bar сообщение через чистую политику
 * {@link DarkWarningPolicy} (семантика ROADMAP §1.9 сохранена). Клавиша {@code G} остаётся отдельным
 * быстрым действием заправки ({@code WhiteFogClient} → C2S {@code white_fog:light_refuel}) и НЕ
 * управляет видимостью панели. Скрытие по gameplay-условиям сохранено: нет игрока, смерть,
 * spectator, F1, любой открытый {@code Screen}.</p>
 */
@Environment(EnvType.CLIENT)
public final class WhiteFogHud implements HudElement {
	/** Dev-only ссылка на зарегистрированный корень (для маркера {@code WHITEFOG_HUD_SELFTEST}). */
	private static WhiteFogHud instance;

	/** Base state — зарезервирован под будущий базовый HUD (этап 1.1). */
	@SuppressWarnings("unused")
	private final ClientPlayerState playerState;
	private final ClientDarknessState darknessState;
	private final LightWidget lightWidget;
	private final ExposureWidget exposureWidget;
	private final GoalWidget goalWidget;
	/**
	 * Виджет старого окна предупреждения оставлен сконструированным (класс/данные сохранены), но
	 * НЕ обновляется и НЕ рисуется: предупреждения теперь идут action-bar сообщением через
	 * {@link #warningPolicy}. Прямоугольник HUD для предупреждения не создаётся.
	 */
	@SuppressWarnings("unused")
	private final WarningWidget warningWidget;
	private final LightWorkPanelHud workPanel;
	/** Чистая политика порогов/cooldown предупреждений (значение — серверный exposure). */
	private final DarkWarningPolicy warningPolicy = new DarkWarningPolicy();

	private WhiteFogHud(ClientPlayerState playerState, ClientDarknessState darknessState, ClientLightState lightState) {
		this.playerState = playerState;
		this.darknessState = darknessState;
		this.lightWidget = new LightWidget(darknessState);
		this.exposureWidget = new ExposureWidget(darknessState);
		this.goalWidget = new GoalWidget(darknessState);
		this.warningWidget = new WarningWidget(darknessState);
		this.workPanel = new LightWorkPanelHud(lightState, darknessState);
	}

	/** Регистрирует ЕДИНСТВЕННЫЙ корневой HUD-элемент. */
	public static void register(ClientPlayerState playerState, ClientDarknessState darknessState,
			ClientLightState lightState) {
		WhiteFogHud hud = new WhiteFogHud(playerState, darknessState, lightState);
		instance = hud;
		HudElementRegistry.addLast(WhiteFog.id("root_hud"), hud);
	}

	/** Dev-only доступ к зарегистрированному корню. */
	static WhiteFogHud instance() {
		return instance;
	}

	/** Dev-only число виджетов корня (для self-test). */
	int widgetCount() {
		return 5;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		// beginFrame: один расчёт реального dt на кадр, clamp 0..0.05 c.
		double dt = DarkHudLayout.clampDt(delta.getRealtimeDeltaTicks() / 20.0);

		boolean hasData = this.darknessState.hasData();
		LocalPlayer player = minecraft.player;
		// Единые gameplay-условия скрытия: нет игрока, смерть, spectator, F1, открытый Screen.
		boolean gameHidden = player == null
				|| player.isDeadOrDying()
				|| player.isSpectator()
				|| minecraft.gui.hud.isHidden()
				|| minecraft.gui.screen() != null;
		boolean chatOpen = minecraft.gui.hud.getChat().isChatFocused();

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// Предупреждения о тьме 1.9 (запрос пользователя): НЕ окно HUD и НЕ строка Work Panel,
		// а обычное action-bar сообщение — как «Слишком крепко — нужен инструмент». Политика
		// отдаёт причину ровно один раз после cooldown 40 тиков при смене серверной причины,
		// поэтому сообщение не повторяется каждый кадр. Без gameplay-игрока ничего не шлём.
		if (!gameHidden) {
			int emitted = this.warningPolicy.update(this.darknessState.lightExposure(), dt);
			if (emitted != DarkWarningPolicy.REASON_NONE) {
				player.sendOverlayMessage(warningComponent(emitted));
			}
		}

		// Режим отображения: компактный статус 1.9 выключен (классы/данные сохранены), Work Panel
		// видна постоянно в gameplay. Видимость панели НЕ зависит от удержания клавиши G — ключ G
		// остаётся отдельным действием заправки (consumeClick в WhiteFogClient).
		boolean compactVisible = DarkHudLayout.compactStageHudVisible(gameHidden, hasData);
		boolean statusVisible = compactVisible && !chatOpen;
		// Панель «Текущее задание» возвращена в правый верхний угол отдельным виджетом.
		boolean goalVisible = DarkHudLayout.stageGoalVisible(gameHidden, hasData, width, height);
		boolean panelVisible = DarkHudLayout.workPanelVisible(gameHidden);

		this.lightWidget.setVisible(statusVisible);
		this.exposureWidget.setVisible(statusVisible);
		this.goalWidget.setVisible(goalVisible);
		this.workPanel.setVisible(panelVisible);
		// WarningWidget не обновляется и не рисуется: предупреждение отправляется выше как action-bar.

		// Update animations even when hidden so fade-out completes without jumps.
		this.lightWidget.updateAnimations(dt);
		this.exposureWidget.updateAnimations(dt);
		this.goalWidget.updateAnimations(dt);
		this.workPanel.updateAnimations(dt);

		if (!statusVisible && !goalVisible && !panelVisible) {
			return; // no fabricated zeros; nothing is visible
		}

		// --- Геометрия статус-панели (общая ширина по самому длинному содержимому) ---
		int statusTextWidth = Math.max(this.lightWidget.contentWidth(font),
				this.exposureWidget.contentWidth(font));
		int statusPanelLogical = 2 * DarkHudLayout.PAD + DarkHudLayout.ICON + DarkHudLayout.TEXT_GAP
				+ Math.min(statusTextWidth, DarkHudLayout.MAX_TEXT_WIDTH);
		int statusPanelHeightLogical = DarkHudLayout.logicalHeight(DarkHudLayout.STATUS_ROWS);
		int statusLeft = DarkHudLayout.statusX();
		int statusTop = DarkHudLayout.statusTop(height, DarkHudLayout.STATUS_ROWS);
		this.lightWidget.setPanelSize(statusPanelLogical, statusPanelHeightLogical);
		this.exposureWidget.setPanelSize(statusPanelLogical, statusPanelHeightLogical);

		// --- Геометрия панели цели (правый верхний угол) ---
		String[] goalLines = this.goalWidget.lines(font);
		int goalTextLogical = Math.min(this.goalWidget.contentWidth(font), DarkHudLayout.goalTextMaxLogical());
		int goalPanelLogical = DarkHudLayout.goalPanelLogicalWidth(goalTextLogical);
		int goalPanelReal = DarkHudLayout.goalPanelWidth(goalTextLogical);
		int goalLeft = DarkHudLayout.goalX(width, goalPanelReal);
		int goalTop = DarkHudLayout.goalY();
		int goalPanelHeightLogical = DarkHudLayout.logicalHeight(goalLines.length);
		this.goalWidget.setPanelSize(goalPanelLogical, goalPanelHeightLogical);

		// --- Геометрия рабочей панели света (единственная нижняя панель, общий стиль) ---
		int workTextLogical = Math.min(this.workPanel.contentWidth(font), DarkHudLayout.workPanelTextMaxLogical());
		int workPanelLogical = 2 * DarkHudLayout.PAD + workTextLogical;
		int workPanelHeightLogical = DarkHudLayout.logicalHeight(DarkHudLayout.WORK_PANEL_ROWS);
		int workLeft = DarkHudLayout.workPanelX();
		int workTop = DarkHudLayout.workPanelTop(height, DarkHudLayout.WORK_PANEL_ROWS);
		this.workPanel.setPanelSize(workPanelLogical, workPanelHeightLogical);

		if (statusVisible) {
			withScaledPose(graphics, statusLeft, statusTop, () -> {
				this.lightWidget.renderBackground(graphics, font);
				this.exposureWidget.renderBackground(graphics, font);
				this.lightWidget.renderIcons(graphics, font);
				this.exposureWidget.renderIcons(graphics, font);
				this.lightWidget.renderText(graphics, font);
				this.exposureWidget.renderText(graphics, font);
			});
		}

		if (goalVisible) {
			withScaledPose(graphics, goalLeft, goalTop, () -> {
				this.goalWidget.renderBackground(graphics, font);
				this.goalWidget.renderIcons(graphics, font);
				this.goalWidget.renderText(graphics, font);
			});
		}

		if (panelVisible) {
			withScaledPose(graphics, workLeft, workTop, () -> {
				this.workPanel.renderBackground(graphics, font);
				this.workPanel.renderIcons(graphics, font);
				this.workPanel.renderText(graphics, font);
			});
		}
	}

	/** Компонент action-bar сообщения по причине предупреждения (локализованный, без raw ID). */
	private static Component warningComponent(int reason) {
		return switch (reason) {
			case DarkWarningPolicy.REASON_FIND_LIGHT -> Component.translatable("hud.white_fog.warning.find_light");
			case DarkWarningPolicy.REASON_DRAIN -> Component.translatable("hud.white_fog.warning.drain");
			default -> Component.empty();
		};
	}

	/** Отрисовка панели в масштабе {@link DarkHudLayout#SCALE} от реальной точки-якоря. */
	private static void withScaledPose(GuiGraphicsExtractor graphics, int x, int y, Runnable body) {
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(x, y);
			graphics.pose().scale(DarkHudLayout.SCALE);
			body.run();
		} finally {
			graphics.pose().popMatrix();
		}
	}

	/**
	 * Dev-only self-test регистрации/раскладки HUD (маркер {@code WHITEFOG_HUD_SELFTEST}).
	 * Не претендует на пиксельное или runtime-доказательство: проверяет единственный корень,
	 * число виджетов и чистые инварианты раскладки.
	 */
	public static boolean selfTest() {
		WhiteFogHud hud = instance;
		boolean registered = hud != null;
		boolean widgets = registered && hud.widgetCount() == 5;
		boolean layout = DarkHudLayout.STATUS_ROWS == 4
				&& DarkHudLayout.goalVisible(320, 180)
				&& !DarkHudLayout.goalVisible(319, 180)
				&& DarkHudLayout.realHeight(4) == 42
				&& DarkHudLayout.goalPanelWidth(1000) == DarkHudLayout.GOAL_MAX_WIDTH
				&& DarkHudLayout.clampDt(1.0) == DarkHudLayout.MAX_DT_SECONDS
				&& DarkHudLayout.ceilSeconds(21) == 2
				&& DarkHudLayout.goalTextX() == DarkHudLayout.PAD
				&& DarkHudLayout.goalTextX() + DarkHudLayout.goalTextMaxLogical()
						<= DarkHudLayout.goalPanelLogicalWidth(DarkHudLayout.goalTextMaxLogical())
				&& DarkHudLayout.WORK_PANEL_ROWS == 7
				&& DarkHudLayout.darknessBarRow() == 1
				&& DarkHudLayout.exposureNormalized(50) == 0.5
				&& DarkHudLayout.stageGoalVisible(false, true, 320, 180)
				&& !DarkHudLayout.stageGoalVisible(false, false, 320, 180)
				&& DarkHudLayout.statusVisibleWithWorkPanel(true) == false
				&& DarkHudLayout.statusVisibleWithWorkPanel(false) == true
				// Запрос пользователя: компактный статус 1.9 выключен, Work Panel видна постоянно.
				&& !DarkHudLayout.shouldShowCompactStageHud()
				&& DarkHudLayout.workPanelAlwaysVisible()
				&& !DarkHudLayout.compactStageHudVisible(false, true)
				&& DarkHudLayout.workPanelVisible(false)
				&& !DarkHudLayout.workPanelVisible(true);
		boolean goals = DarkGoalService.goalCount() == 5
				&& DarkGoalService.DEFAULT_GOAL.equals(DarkGoalService.firstIncomplete(DarkGoalService.defaultFlags()));
		return registered && widgets && layout && goals;
	}
}
