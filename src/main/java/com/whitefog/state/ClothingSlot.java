package com.whitefog.state;

/**
 * Слот одежды, для которого хранится отдельное значение мокроты.
 *
 * <p>Порядок констант фиксирован: он используется как индекс в массиве мокроты,
 * поэтому новые слоты добавляются только в конец.</p>
 */
public enum ClothingSlot {
	/** Голова (шлем). */
	HEAD("head"),
	/** Торс (нагрудник). */
	CHEST("chest"),
	/** Ноги (штаны). */
	LEGS("legs"),
	/** Ступни (ботинки). */
	FEET("feet");

	private final String serializedName;

	ClothingSlot(String serializedName) {
		this.serializedName = serializedName;
	}

	/** Имя слота для сериализации в NBT. */
	public String serializedName() {
		return this.serializedName;
	}

	/** Поиск слота по имени NBT; при неизвестном имени возвращает {@code null}. */
	public static ClothingSlot byName(String name) {
		for (ClothingSlot slot : values()) {
			if (slot.serializedName.equals(name)) {
				return slot;
			}
		}
		return null;
	}
}
