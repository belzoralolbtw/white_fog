package com.whitefog.mixin;

import com.whitefog.crafting.CraftingLock;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Удаление всех рецептов типа {@code minecraft:crafting} при загрузке менеджера рецептов.
 *
 * <p>Перехватываем аргумент {@link RecipeManager#apply(RecipeMap, net.minecraft.server.packs.resources.ResourceManager,
 * net.minecraft.util.profiling.ProfilerFiller)} на входе и подменяем набор рецептов отфильтрованным.
 * В итоге {@code getRecipeFor(CRAFTING, ...)} ничего не находит, слот результата всегда пуст, а книга
 * рецептов (дисплеи строятся в {@code finalizeRecipeLoading} из этого же набора) не содержит крафта.
 * Срабатывает и при первом старте, и при каждом {@code /reload}.</p>
 *
 * <p>priority = 1500 — выше стандартного (1000), чтобы фильтр применился раньше, чем Fabric API
 * построит свой {@code SynchronizedRecipes} из того же аргумента.</p>
 *
 * <p>Reference: {@code FabricMC/fabric-api} —
 * {@code fabric-recipe-api-v1/src/main/java/net/fabricmc/fabric/mixin/recipe/RecipeManagerMixin.java}
 * (точка {@code apply}) и {@code .../RecipeManagerAccessor.java} (внутреннее поле {@code RecipeMap}).</p>
 */
@Mixin(value = RecipeManager.class, priority = 1500)
public abstract class RecipeManagerMixin {
	@ModifyVariable(
			method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;"
					+ "Lnet/minecraft/server/packs/resources/ResourceManager;"
					+ "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At("HEAD"),
			argsOnly = true)
	private RecipeMap whiteFog$filterCraftingRecipes(RecipeMap recipes) {
		return CraftingLock.filterOutCraftingRecipes(recipes);
	}
}
