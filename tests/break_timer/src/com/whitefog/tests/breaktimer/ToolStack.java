package com.whitefog.tests.breaktimer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Упрощённый {@code ItemStack} инструмента: id предмета + компоненты.
 *
 * <p>Проверка «смена предмета включая components»: два стека с одним item id,
 * но разным набором data-компонентов (например, разный уровень Efficiency)
 * считаются разными инструментами — как {@code ItemStack#isSameItemSameComponents}
 * в MC 26.2 (javap-проверено: метод существует).</p>
 */
public final class ToolStack {
	private final String itemId;
	private final Map<String, String> components;

	private ToolStack(String itemId, Map<String, String> components) {
		this.itemId = itemId;
		this.components = Map.copyOf(components);
	}

	public static ToolStack hand() {
		return new ToolStack("", Map.of());
	}

	public static ToolStack of(String itemId) {
		return new ToolStack(itemId, Map.of());
	}

	public static ToolStack of(String itemId, Map<String, String> components) {
		return new ToolStack(itemId, components);
	}

	/** Возвращает копию стека с изменённым компонентом (для теста смены предмета). */
	public ToolStack withComponent(String key, String value) {
		Map<String, String> copy = new LinkedHashMap<>(components);
		copy.put(key, value);
		return new ToolStack(itemId, copy);
	}

	public String itemId() {
		return itemId;
	}

	public Map<String, String> components() {
		return components;
	}

	public boolean isHand() {
		return itemId.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ToolStack other)) {
			return false;
		}
		return itemId.equals(other.itemId) && components.equals(other.components);
	}

	@Override
	public int hashCode() {
		return Objects.hash(itemId, components);
	}

	@Override
	public String toString() {
		return isHand() ? "HAND" : (itemId + components);
	}
}
