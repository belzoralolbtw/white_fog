package com.whitefog.tests.guiicon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded sandbox: центрирование GUI-иконок предметов {@code white_fog:small_stone}
 * и {@code white_fog:flat_stone}.
 *
 * <p><b>НЕ трогает основной {@code build.gradle} и {@code src/}</b>. Читает реальные ресурсные
 * JSON предметной и блочной моделей и вызывает <b>настоящий</b> конвейер трансформации Minecraft 26.2
 * из clientonly-deobf jar: {@link net.minecraft.client.resources.model.cuboid.ItemTransform#apply}
 * + {@link com.mojang.blaze3d.vertex.PoseStack}. Это НЕ «своя математика на глаз» — единицы
 * translation ({@code ItemTransform$Deserializer}: {@code json * 0.0625}, clamp [-5,5]) и порядок
 * операций (T * R * S * T(-0.5), {@code ItemDisplayContext.GUI.leftHand()==false}) взяты из байткода
 * deobf jar, а не из памяти.</p>
 *
 * <p>Merge-семантика display подтверждена по байткоду 26.2
 * {@code ResolvedModel.findTopTransform}: трансформации наследуются <b>по контексту целиком</b>
 * (дочерний {@code gui} заменяет родительский целиком, остальные контексты берутся у родителя).
 * Поэтому предметная модель {@code flat_stone} содержит только {@code gui} (rotation/scale — те же,
 * что у {@code minecraft:block/block}), а прочие контексты наследуются из блока без изменений.</p>
 *
 * <p>Что доказывается: после GUI rotation/scale/translation центр bounding-box модели
 * проецируется точно в центр слота, если задан вычисленный translation. Дополнительно
 * сверяется, что матрица {@code ItemTransform.apply} совпадает с ручной композицией JOML
 * (проверка понимания порядка и единиц), и что размер (extent) сохраняется.</p>
 *
 * <p><b>Это НЕ runtime-рендер-proof</b>: проверяется матричная трансформация реальными классами,
 * но пиксельный вывод клиента не проверяется.</p>
 *
 * <p>Завершается сам: watchdog-поток через {@value #HARD_TIMEOUT_MS} мс гасит JVM кодом 124
 * ({@code TIMEOUT}).</p>
 */
public final class GuiIconCenterSelfTest {

	private static final long HARD_TIMEOUT_MS = 20_000L;
	private static final int EXIT_TIMEOUT = 124;

	/** ItemTransform$Deserializer: translation = json * 0.0625. */
	private static final float TRANSLATION_UNIT = 0.0625f;
	/** ItemTransform$Deserializer: translation clamp [-5.0, 5.0] (в тех же единицах). */
	private static final float TRANSLATION_LIMIT = 5.0f;

	/** Точность для «центр в нуле» (единицы модели, 1.0 = 16 px). */
	private static final float CENTRE_EPS = 1.0e-4f;
	/** Точность сравнения матриц ItemTransform.apply vs ручная JOML-композиция. */
	private static final float MATRIX_EPS = 1.0e-5f;
	/** Допуск для проверки фактического translation из файла (px); покрывает округление до 3 знаков. */
	private static final float FILE_TOL_PX = 0.02f;

	/**
	 * Ожидаемый gui.scale (доказательство, что размер предмета НЕ менялся фиксом):
	 * small_stone — 1.25 (прежний фикс видимости), flat_stone — 0.625
	 * (унаследованный от vanilla {@code minecraft:block/block} gui-scale блочного предмета).
	 */
	private static final float SMALL_STONE_GUI_SCALE = 1.25f;
	private static final float FLAT_STONE_GUI_SCALE = 0.625f;

	private static final Pattern VEC = Pattern.compile(
			"\"(rotation|translation|scale)\"\\s*:\\s*\\[\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*\\]");
	private static final Pattern CORNER = Pattern.compile(
			"\"(from|to)\"\\s*:\\s*\\[\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*,\\s*([-0-9.]+)\\s*\\]");

	private static int passed = 0;
	private static final List<String> failures = new ArrayList<>();

	private GuiIconCenterSelfTest() {
	}

	public static void main(String[] args) throws Exception {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(HARD_TIMEOUT_MS);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("HARD_TIMEOUT after " + HARD_TIMEOUT_MS + " ms");
			System.out.flush();
			Runtime.getRuntime().halt(EXIT_TIMEOUT);
		}, "gui-icon-test-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		System.out.println("=== White Fog item GUI icon centring sandbox (real 26.2 ItemTransform.apply) ===");
		long begin = System.nanoTime();

		Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
		Path itemDir = root.resolve("src/main/resources/assets/white_fog/models/item");
		Path blockDir = root.resolve("src/main/resources/assets/white_fog/models/block");
		System.out.println("root=" + root);

		probe("small_stone", itemDir.resolve("small_stone.json"),
				blockDir.resolve("small_stone_0.json"), SMALL_STONE_GUI_SCALE);
		probe("flat_stone", itemDir.resolve("flat_stone.json"),
				blockDir.resolve("flat_stone.json"), FLAT_STONE_GUI_SCALE);

		long ms = (System.nanoTime() - begin) / 1_000_000L;
		System.out.println();
		System.out.println("-------------------------------------------------------------");
		System.out.println("passed=" + passed + " failed=" + failures.size() + " timeMs=" + ms);
		for (String f : failures) {
			System.out.println("FAILED: " + f);
		}
		if (failures.isEmpty()) {
			System.out.println("SELFTEST status=SUCCESS");
			System.out.flush();
			Runtime.getRuntime().halt(0);
		} else {
			System.out.println("SELFTEST status=FAILURE");
			System.out.flush();
			Runtime.getRuntime().halt(1);
		}
	}

	// ------------------------------------------------------------------ one model group

	private static void probe(String label, Path itemModel, Path blockModel, float expectedGuiScale) throws Exception {
		System.out.println();
		System.out.println("### model: " + label + " ###");
		System.out.println("itemModel=" + itemModel);
		System.out.println("blockModel=" + blockModel);

		String itemJson = read(itemModel);
		String blockJson = read(blockModel);

		// --- geometry bounding box (block coordinates, 0..16) ---
		float[] bbox = bbox(blockJson); // minX,minY,minZ,maxX,maxY,maxZ
		float[][] corners = corners(bbox);
		System.out.printf(Locale.ROOT, "bboxPx=[%s]%n", fmt(bbox));
		System.out.printf(Locale.ROOT, "bboxCenterNorm=[%.6f, %.6f, %.6f]%n",
				(bbox[0] + bbox[3]) / 2f / 16f, (bbox[1] + bbox[4]) / 2f / 16f, (bbox[2] + bbox[5]) / 2f / 16f);

		// --- item gui display ---
		float[] rot = vec(itemJson, "rotation");
		float[] curTrans = vec(itemJson, "translation");
		float[] sc = vec(itemJson, "scale");
		System.out.printf(Locale.ROOT, "gui.rotation=%s gui.translation=%s gui.scale=%s%n",
				fmt(rot), fmt(curTrans), fmt(sc));
		check(sc[0] == sc[1] && sc[1] == sc[2], label + ": gui scale is uniform (visible size preserved): " + fmt(sc));
		check(Math.abs(sc[0] - expectedGuiScale) < 1.0e-6f,
				label + ": gui scale unchanged (" + fmtf(sc[0]) + " == expected " + fmtf(expectedGuiScale) + ")");

		// --- (1) real ItemTransform.apply on the CURRENT file values ---
		Matrix4f current = guiMatrix(rot, curTrans, sc[0]);
		float[] curBox = transformBBox(current, corners);
		float[] curCentre = {curBox[0], curBox[1], curBox[2]};
		System.out.printf(Locale.ROOT, "current projected bbox centre (model units, 1.0=16px) = [%.6f, %.6f, %.6f]%n",
				curCentre[0], curCentre[1], curCentre[2]);
		System.out.printf(Locale.ROOT, "current offset in GUI px = [%.3f, %.3f, %.3f]%n",
				curCentre[0] * 16f, curCentre[1] * 16f, curCentre[2] * 16f);

		// --- (2) required translation: f(0) is the offset with translation = 0; because
		//     f(t) = t + f(0), the translation that centres the bbox is exactly -f(0). ---
		float[] zeroBox = transformBBox(guiMatrix(rot, new float[]{0f, 0f, 0f}, sc[0]), corners);
		System.out.printf(Locale.ROOT, "un-translated (t=0) projected bbox centre = [%.6f, %.6f, %.6f]%n",
				zeroBox[0], zeroBox[1], zeroBox[2]);
		float[] reqPx = {-zeroBox[0] / TRANSLATION_UNIT, -zeroBox[1] / TRANSLATION_UNIT, -zeroBox[2] / TRANSLATION_UNIT};
		float[] reqPxRounded = {round3(reqPx[0]), round3(reqPx[1]), round3(reqPx[2])};
		System.out.printf(Locale.ROOT, "required gui.translation (px, exact)   = [%.6f, %.6f, %.6f]%n",
				reqPx[0], reqPx[1], reqPx[2]);
		System.out.printf(Locale.ROOT, "required gui.translation (px, rounded) = [%s]%n", fmt3(reqPxRounded));

		// apply the rounded required translation and prove the centre lands in the slot centre
		Matrix4f fixed = guiMatrix(rot, reqPxRounded, sc[0]);
		float[] fixedBox = transformBBox(fixed, corners);
		System.out.printf(Locale.ROOT, "fixed projected bbox centre (model units) = [%.6f, %.6f, %.6f]%n",
				fixedBox[0], fixedBox[1], fixedBox[2]);
		check(Math.abs(fixedBox[0]) < CENTRE_EPS && Math.abs(fixedBox[1]) < CENTRE_EPS && Math.abs(fixedBox[2]) < CENTRE_EPS,
				label + ": required translation puts the projected bbox centre exactly at the slot centre (0,0,0)");
		check(withinLimit(reqPxRounded),
				label + ": required translation is inside ItemTransform clamp [-5,5] model units / [-80,80] px");

		// --- (3) visible size preserved (translation does not change extent) ---
		check(near(curBox[3], fixedBox[3]) && near(curBox[4], fixedBox[4]) && near(curBox[5], fixedBox[5]),
				label + ": extent preserved: current=" + fmt(new float[]{curBox[3], curBox[4], curBox[5]})
						+ " fixed=" + fmt(new float[]{fixedBox[3], fixedBox[4], fixedBox[5]}));

		// --- (4) understanding check: real apply == manual JOML composition ---
		Matrix4f manual = manualMatrix(rot, reqPxRounded, sc[0]);
		check(matricesNear(fixed, manual, MATRIX_EPS),
				label + ": ItemTransform.apply == Mat4.translate(t).rotate(rotationXYZ).scale(s).translate(-0.5) (order/units confirmed)");

		// --- (5) actual file must carry the required translation ---
		boolean fileOk = Math.abs(curTrans[0] - reqPxRounded[0]) <= FILE_TOL_PX
				&& Math.abs(curTrans[1] - reqPxRounded[1]) <= FILE_TOL_PX
				&& Math.abs(curTrans[2] - reqPxRounded[2]) <= FILE_TOL_PX;
		check(fileOk, label + ": item model gui.translation matches required value (actual=" + fmt(curTrans)
				+ ", required=" + fmt3(reqPxRounded) + ")");

		System.out.printf(Locale.ROOT, "  note: %s current |offsetY|=%.3f px, required gui.translation=[%s]%n",
				label, Math.abs(curCentre[1]) * 16f, fmt3(reqPxRounded));
		System.out.println("--- patch for models/item/" + label + ".json ---");
		System.out.printf(Locale.ROOT, "\"gui\": { \"rotation\": [%s], \"translation\": [%s], \"scale\": [%s] }%n",
				fmt(rot), fmt3(reqPxRounded), fmt(sc));
	}

	// ------------------------------------------------------------------ transform helpers

	/**
	 * Полный GUI-конвейер через настоящий {@link ItemTransform#apply(boolean, PoseStack.Pose)}.
	 * {@code leftHand=false}, потому что {@code ItemDisplayContext.GUI.leftHand()==false} (javap).
	 */
	private static Matrix4f guiMatrix(float[] rot, float[] transPx, float scale) {
		ItemTransform transform = new ItemTransform(
				new Vector3f(rot[0], rot[1], rot[2]),
				clampedTranslation(transPx),
				new Vector3f(scale, scale, scale));
		PoseStack pose = new PoseStack();
		pose.pushPose();
		transform.apply(false, pose.last());
		return new Matrix4f(pose.last().pose());
	}

	/** Та же композиция, что и в {@code ItemTransform.apply}, но собранная вручную на JOML. */
	private static Matrix4f manualMatrix(float[] rot, float[] transPx, float scale) {
		Vector3f t = clampedTranslation(transPx);
		return new Matrix4f()
				.translate(t.x, t.y, t.z)
				.rotate(new Quaternionf().rotationXYZ(
						(float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2])))
				.scale(scale, scale, scale)
				.translate(-0.5f, -0.5f, -0.5f);
	}

	private static Vector3f clampedTranslation(float[] px) {
		Vector3f t = new Vector3f(px[0], px[1], px[2]).mul(TRANSLATION_UNIT);
		t.x = clamp(t.x, -TRANSLATION_LIMIT, TRANSLATION_LIMIT);
		t.y = clamp(t.y, -TRANSLATION_LIMIT, TRANSLATION_LIMIT);
		t.z = clamp(t.z, -TRANSLATION_LIMIT, TRANSLATION_LIMIT);
		return t;
	}

	/** Возвращает {cx, cy, cz, extentX, extentY, extentZ} AABB после трансформации углов. */
	private static float[] transformBBox(Matrix4f m, float[][] corners) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		Vector3f p = new Vector3f();
		for (float[] c : corners) {
			p.set(c[0], c[1], c[2]);
			m.transformPosition(p);
			minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
			minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
			minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
		}
		return new float[]{(minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f,
				maxX - minX, maxY - minY, maxZ - minZ};
	}

	private static float[][] corners(float[] b) {
		float[][] out = new float[8][3];
		int i = 0;
		for (float x : new float[]{b[0], b[3]}) {
			for (float y : new float[]{b[1], b[4]}) {
				for (float z : new float[]{b[2], b[5]}) {
					out[i][0] = x / 16f;
					out[i][1] = y / 16f;
					out[i][2] = z / 16f;
					i++;
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ parsing helpers

	private static String read(Path p) throws Exception {
		if (!Files.isRegularFile(p)) {
			throw new IllegalStateException("model file not found: " + p);
		}
		return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
	}

	private static float[] bbox(String json) {
		Matcher m = CORNER.matcher(json);
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		int found = 0;
		while (m.find()) {
			found++;
			float x = Float.parseFloat(m.group(2));
			float y = Float.parseFloat(m.group(3));
			float z = Float.parseFloat(m.group(4));
			minX = Math.min(minX, x); maxX = Math.max(maxX, x);
			minY = Math.min(minY, y); maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
		}
		if (found == 0) {
			throw new IllegalStateException("no from/to elements found in block model");
		}
		return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
	}

	private static float[] vec(String json, String key) {
		Matcher m = VEC.matcher(json);
		while (m.find()) {
			if (m.group(1).equals(key)) {
				return new float[]{Float.parseFloat(m.group(2)), Float.parseFloat(m.group(3)), Float.parseFloat(m.group(4))};
			}
		}
		throw new IllegalStateException("key not found in JSON: " + key);
	}

	// ------------------------------------------------------------------ assertions

	private static void check(boolean ok, String msg) {
		if (ok) {
			passed++;
			System.out.println("  ok: " + msg);
		} else {
			failures.add(msg);
			System.out.println("  FAIL: " + msg);
		}
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1.0e-4f;
	}

	private static boolean withinLimit(float[] px) {
		for (float v : px) {
			if (Math.abs(v * TRANSLATION_UNIT) > TRANSLATION_LIMIT + 1.0e-6f) {
				return false;
			}
		}
		return true;
	}

	private static boolean matricesNear(Matrix4f a, Matrix4f b, float eps) {
		for (int r = 0; r < 4; r++) {
			for (int c = 0; c < 4; c++) {
				if (Math.abs(a.get(r, c) - b.get(r, c)) > eps) {
					return false;
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ small utils

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	private static float round3(float v) {
		return Math.round(v * 1000f) / 1000f;
	}

	private static String fmt(float[] v) {
		return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", v[0], v[1], v[2]);
	}

	private static String fmt3(float[] v) {
		return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", v[0], v[1], v[2]);
	}

	private static String fmtf(float v) {
		return String.format(Locale.ROOT, "%.6f", v);
	}
}
