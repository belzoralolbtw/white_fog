package com.whitefog.tests.breaktimer;

/**
 * Снимок серверного игрока, нужный правилам: кто, в каком измерении, где, режим и дальность.
 *
 * <p>Дальность соответствует {@code Player#isWithinBlockInteractionRange(BlockPos, double)}
 * (javap-проверено в 26.2).</p>
 */
public final class PlayerRef {
	public final String uuid;
	public final String dimension;
	public final double x;
	public final double y;
	public final double z;
	public final double reach;
	public final boolean creative;

	public PlayerRef(String uuid, String dimension, double x, double y, double z, double reach, boolean creative) {
		this.uuid = uuid;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.reach = reach;
		this.creative = creative;
	}

	public PlayerRef at(double nx, double ny, double nz) {
		return new PlayerRef(uuid, dimension, nx, ny, nz, reach, creative);
	}

	public PlayerRef inDimension(String other) {
		return new PlayerRef(uuid, other, x, y, z, reach, creative);
	}

	public PlayerRef creative(boolean value) {
		return new PlayerRef(uuid, dimension, x, y, z, reach, value);
	}

	/** Квадрат расстояния от игрока до центра блока. */
	public double distanceSqrTo(BlockKey key) {
		double dx = x - (key.x() + 0.5);
		double dy = y - (key.y() + 0.5);
		double dz = z - (key.z() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}

	public boolean withinReach(BlockKey key) {
		return distanceSqrTo(key) <= reach * reach;
	}

	@Override
	public String toString() {
		return uuid + "@" + dimension + "(" + x + "," + y + "," + z + ")"
				+ (creative ? "[creative]" : "");
	}
}
