package nu.metacraft.revival;

import com.google.common.collect.ImmutableSet;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.DialogTags;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DeathMessageType;
import nu.metacraft.lib.METAcraftLib;
import nu.metacraft.lib.custom_message.CodecMessageHandler;
import nu.metacraft.lib.util.BoostrapContextRegistryOpsAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class RevivalDatagen implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		var pack = fabricDataGenerator.createPack();
		pack.addProvider(DynamicProvider::new);
		pack.addProvider(DamageTags::new);
		pack.addProvider(DialogsTags::new);
	}

	@Override
	public void buildRegistry(RegistrySetBuilder registryBuilder) {
		registryBuilder.add(
				Registries.DAMAGE_TYPE, ctx -> {
					ctx.register(
							RevivalDamageTypes.ACCEPTED_FATE,
							new DamageType(
									"metacraft.revival.accepted_fate", DamageScaling.NEVER, 0,
									DamageEffects.HURT, DeathMessageType.DEFAULT
							)
					);
				}
		);
		registryBuilder.add(
				Registries.DIALOG, ctx -> {
					ctx.register(
						RevivalDialogs.REOPEN_REVIVAL,
						new NoticeDialog(
							new CommonDialogData(
								Component.translatableWithFallback("dialog.metacraft.revival.reopen", "Reopen Revival"),
								Optional.of(RevivalDialogs.REVIVAL_EXTERNAL_TITLE),
								true, true,
								DialogAction.WAIT_FOR_RESPONSE,
								List.of(
									new PlainMessage(
										Component.translatableWithFallback(
												"dialog.metacraft.revival.reopen.desc",
											"Please click the button below to open the revival GUI"
										),
										200
									)
								), List.of()
							),
							new ActionButton(
								new CommonButtonData(
									Component.translatableWithFallback(
											"dialog.metacraft.revival.reopen.reopen",
											"Open Revival GUI"
									), 200
								),
								Optional.of(
									new StaticAction(
										CodecMessageHandler.createSimpleClickEvent(
											RevivalDialogs.REVIVAL_MENU,
											RevivalDialogs.Option.OPEN,
											ops -> BoostrapContextRegistryOpsAdapter.createRegistryOps(
													ops, ctx
											)
										)
									)
								)
							)
						)
					);
				}
		);
	}

	// For some reason, Fabric won't include stuff added via buildRegistry in the data generation automatically.
	public static class DynamicProvider extends FabricDynamicRegistryProvider {

		private static final Set<ResourceKey<? extends Registry<?>>> DYNAMIC_REGISTRIES = new ImmutableSet.Builder<ResourceKey<? extends Registry<?>>>().addAll(
				DynamicRegistries.getDynamicRegistries().stream().map(RegistryDataLoader.RegistryData::key).iterator()
		).addAll(
				RegistryDataLoader.DIMENSION_REGISTRIES.stream().map(RegistryDataLoader.RegistryData::key).iterator()
		).build();

		public DynamicProvider(
				FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture
		) {
			super(output, registriesFuture);
		}

		private static boolean isDynamic(ResourceKey<? extends Registry<?>> key) {
			return DYNAMIC_REGISTRIES.contains(key);
		}

		private static <T> void add(Entries entries, Holder.Reference<T> ref) {
			entries.add(ref.key(), ref.value());
		}

		@Override
		protected void configure(HolderLookup.Provider registries, Entries entries) {
			registries.listRegistries().forEach(reg -> {
				if (isDynamic(reg.key())) {
					reg.listElements().forEach(e -> {
						if (e.key().identifier().getNamespace().equals(METAcraftLib.NAMESPACE)) {
							add(entries, e);
						}
					});
				}
			});
		}

		@Override
		public @NotNull String getName() {
			return "DynamicRegistries";
		}
	}

	public static class DialogsTags extends FabricTagProvider<Dialog> {

		public DialogsTags(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, Registries.DIALOG, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider wrapperLookup) {
			builder(DialogTags.QUICK_ACTIONS).add(
					RevivalDialogs.REOPEN_REVIVAL
			);
		}
	}

	public static class DamageTags extends FabricTagProvider<DamageType> {

		public DamageTags(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
			super(output, Registries.DAMAGE_TYPE, registriesFuture);
		}

		@Override
		protected void addTags(HolderLookup.Provider provider) {
			builder(DamageTypeTags.BYPASSES_INVULNERABILITY).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_ARMOR).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_SHIELD).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_COOLDOWN).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_EFFECTS).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_RESISTANCE).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_ENCHANTMENTS).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.NO_KNOCKBACK).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.NO_IMPACT).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(DamageTypeTags.BYPASSES_WOLF_ARMOR).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
			builder(RevivalTags.Damage.BYPASSES_REVIVAL).addTag(
					DamageTypeTags.BYPASSES_INVULNERABILITY
			).add(
					RevivalDamageTypes.ACCEPTED_FATE
			);
		}
	}
}
