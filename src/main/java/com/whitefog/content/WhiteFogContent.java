package com.whitefog.content;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogConfig;
import com.whitefog.content.block.FlatStoneBlock;
import com.whitefog.content.block.SmallStoneBlock;
import com.whitefog.content.block.entity.FlatStoneBlockEntity;
import com.whitefog.content.item.GroundPlacedBlockItem;
import com.whitefog.content.menu.FlatStoneMenu;
import com.whitefog.content.menu.LightSourceMenu;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;
import java.util.function.Function;

/**
 * Регистрация контента мода (этап 1.4): блоки, BlockItem, block entity и меню станции.
 *
 * <p>Все регистрации выполняются один раз при общем {@code onInitialize} (до старта
 * загрузки мира). Имена взяты ровно как в паспорте: {@code white_fog:flat_stone} и
 * {@code white_fog:small_stone}. Для 26.2 обязателен {@code Properties#setId(ResourceKey)}
 * (иначе загрузка регистрирует блок без id).</p>
 *
 * <p>Поршень: {@code flat_stone} — {@link PushReaction#BLOCK} (станция не перемещается, в т.ч.
 * с job), {@code small_stone} — {@link PushReaction#DESTROY} (разрушается поршнем с обычным loot).</p>
 *
 * <p><b>Hook points (не реализовано в 1.4, без фиктивной генерации/рецептов):</b></p>
 * <ul>
 *     <li>TODO (этап 2.1, worldgen): размещать одну {@code flat_stone} у укрытия и не менее 24
 *         {@code small_stone} в радиусе 32 блоков на доступной поверхности; вариант камушка брать
 *         из {@link SmallStoneBlock#variantFor} (детерминирован по позиции, loot не меняет).</li>
 *     <li>TODO (этап 3.5, рецепты инструментов): рецепты используют стек камушка напрямую; слоты и
 *         job-UI станции заполняются поверх {@code FlatStoneBlockEntity} (schemaVersion/escrow/output/
 *         mode/revision уже сохранены).</li>
 * </ul>
 */
public final class WhiteFogContent {

	/** «Плоский камень» — станция (block entity есть только у неё). */
	public static final FlatStoneBlock FLAT_STONE = registerBlock("flat_stone", FlatStoneBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(1.5F)
					.sound(SoundType.STONE)
					.noOcclusion()
					.pushReaction(PushReaction.BLOCK));

	/** «Камушка» — обычный блок, не decor entity. */
	public static final SmallStoneBlock SMALL_STONE = registerBlock("small_stone", SmallStoneBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.instabreak()
					.sound(SoundType.STONE)
					.noCollision()
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));

	/** Предмет станции: stack 16, без прочности. */
	public static final Item FLAT_STONE_ITEM = registerBlockItem("flat_stone", FLAT_STONE,
			new Item.Properties().stacksTo(WhiteFogConfig.FLAT_STONE_STACK_SIZE));

	/** Предмет камушка: stack 64, без прочности. */
	public static final Item SMALL_STONE_ITEM = registerBlockItem("small_stone", SMALL_STONE,
			new Item.Properties().stacksTo(WhiteFogConfig.SMALL_STONE_STACK_SIZE));

	/** Block entity только у станции. */
	public static final BlockEntityType<FlatStoneBlockEntity> FLAT_STONE_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, WhiteFog.id("flat_stone"),
			new BlockEntityType<>(FlatStoneBlockEntity::new, Set.<Block>of(FLAT_STONE)));

	/** Пустое меню станции (вкладка рецептов пустая на этом этапе). */
	public static final MenuType<FlatStoneMenu> FLAT_STONE_MENU = Registry.register(
			BuiltInRegistries.MENU, WhiteFog.id("flat_stone"),
			new MenuType<>(FlatStoneMenu::new, FeatureFlags.VANILLA_SET));

	/** Меню источника света (этап поверх 1.6): кнопки Заправить/Потушить/Зажечь, без слотов. */
	public static final MenuType<LightSourceMenu> LIGHT_SOURCE_MENU = Registry.register(
			BuiltInRegistries.MENU, WhiteFog.id("light_source"),
			new MenuType<>(LightSourceMenu::new, FeatureFlags.VANILLA_SET));

	private WhiteFogContent() {
	}

	private static boolean registered = false;

	/** Форсирует инициализацию класса (сами поля уже зарегистрировали контент) и пишет лог. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WhiteFog.LOGGER.info(
				"White Fog: content registered (flat_stone, small_stone, block entity, menu) — stage 1.4");
	}

	private static <T extends Block> T registerBlock(String name,
			Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, WhiteFog.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item registerBlockItem(String name, Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, WhiteFog.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new GroundPlacedBlockItem(block, properties.setId(key)));
	}
}
