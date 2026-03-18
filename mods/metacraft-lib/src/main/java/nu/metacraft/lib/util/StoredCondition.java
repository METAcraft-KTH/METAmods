package nu.metacraft.lib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import nu.metacraft.lib.METAcraftLib;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface StoredCondition {

	Codec<StoredCondition> CODEC = Type.CODEC.dispatch(
			StoredCondition::getType, Type::codec
	);
	
	boolean matches(CommandSourceStack source);

	Type getType();

	record PredicateCondition(TagOrSet<LootItemCondition> condition, Variant variant) implements StoredCondition {

		public static final MapCodec<PredicateCondition> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						TagOrSet.codec(Registries.PREDICATE).fieldOf("predicate").forGetter(PredicateCondition::condition),
						Variant.CODEC.fieldOf("variant").forGetter(PredicateCondition::variant)
				).apply(instance, PredicateCondition::new)
		);

		@Override
		public boolean matches(CommandSourceStack source) {
			var elements = condition.getElements(source.getServer().reloadableRegistries().lookup()).stream().map(
					Holder::value
			).map(e -> (LootItemCondition.Builder) () -> e).toArray(LootItemCondition.Builder[]::new);
			LootItemCondition condition = switch (variant) {
				case ALL -> AllOfCondition.allOf(elements).build();
				case ANY -> AnyOfCondition.anyOf(elements).build();
			};
			ServerLevel serverLevel = source.getLevel();
			LootParams lootParams = new LootParams.Builder(serverLevel)
					.withParameter(LootContextParams.ORIGIN, source.getPosition())
					.withOptionalParameter(LootContextParams.THIS_ENTITY, source.getEntity())
					.create(LootContextParamSets.COMMAND);
			LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
			lootContext.pushVisitedElement(LootContext.createVisitedEntry(condition));
			return condition.test(lootContext);
		}

		@Override
		public Type getType() {
			return Type.PREDICATE;
		}

		public enum Variant implements StringRepresentable {
			ALL("all"),
			ANY("any");

			public static final Codec<Variant> CODEC = StringRepresentable.fromEnum(Variant::values);

			private final String name;

			Variant(String name) {
				this.name = name;
			}

			@Override
			public @NonNull String getSerializedName() {
				return name;
			}
		}
	}

	record FunctionCondition(Identifier function, Optional<CompoundTag> macroArgs) implements StoredCondition {

		public static final MapCodec<FunctionCondition> CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
						Identifier.CODEC.fieldOf("function").forGetter(FunctionCondition::function),
						CompoundTag.CODEC.optionalFieldOf("macro_args").forGetter(FunctionCondition::macroArgs)
				).apply(instance, FunctionCondition::new)
		);

		@Override
		public boolean matches(CommandSourceStack source) {
			MutableBoolean isSuccessful = new MutableBoolean(false);
			source.getServer().getFunctions().get(function).ifPresent(function -> {
				try {
					var actualFunction = function.instantiate(macroArgs.orElse(null), source.getServer().getFunctions().getDispatcher());
					Commands.executeCommandInContext(
							source,
							ctx -> ExecutionContext.queueInitialFunctionCall(
									ctx, actualFunction, source.withPermission(LevelBasedPermissionSet.GAMEMASTER),
									(success, result) -> isSuccessful.setValue(result > 0)
							)
					);
				} catch (FunctionInstantiationException e) {
					METAcraftLib.LOGGER.error(e::getMessage);
				}
			});
			return isSuccessful.booleanValue();
		}

		@Override
		public Type getType() {
			return Type.FUNCTION;
		}
	}

	enum Type implements StringRepresentable {
		FUNCTION("function", FunctionCondition.CODEC),
		PREDICATE("predicate", PredicateCondition.CODEC);

		public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

		private final String name;
		private final MapCodec<? extends StoredCondition> codec;

		Type(String name, MapCodec<? extends StoredCondition> codec) {
			this.name = name;
			this.codec = codec;
		}

		public MapCodec<? extends StoredCondition> codec() {
			return codec;
		}

		@Override
		public @NonNull String getSerializedName() {
			return name;
		}
	}

}
