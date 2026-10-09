package com.whitefog.darkness.light;

/**
 * Чистый (без Minecraft) детерминированный tie-break ближайшего источника (этап 1.9).
 *
 * <p>При равном расстоянии от глаз до центра блока выбирается источник с меньшими
 * {@code x}, затем {@code y}, затем {@code z}. Правило вынесено из
 * {@link LightSourceService} в чистый класс, чтобы sandbox мог проверить его напрямую, а
 * сервер и клиент использовали ровно одну реализацию.</p>
 */
public final class LightTieBreak {
	private LightTieBreak() {
	}

	/** Должен ли кандидат заменить текущий лучший при равном расстоянии. */
	public static boolean better(int candidateX, int candidateY, int candidateZ,
			int bestX, int bestY, int bestZ) {
		if (candidateX != bestX) {
			return candidateX < bestX;
		}
		if (candidateY != bestY) {
			return candidateY < bestY;
		}
		return candidateZ < bestZ;
	}
}
