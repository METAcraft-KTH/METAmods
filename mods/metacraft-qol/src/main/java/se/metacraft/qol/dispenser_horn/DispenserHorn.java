package se.metacraft.qol.dispenser_horn;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import se.metacraft.qol.Qol;

/** Allow playing a goat horn by dispensing it with a dispenser. Still respect the cooldown. */
public final class DispenserHorn {

	/** Store on the block entity when it is ready to play another horn */
	private static final AttachmentType<Long> READY_AT = AttachmentRegistry.createPersistent(Qol.getID("horn_ready_at"), Codec.LONG);

	private DispenserHorn() {
	}

	public static void init() {
		DispenserBlock.registerBehavior(Items.GOAT_HORN, new OptionalDispenseItemBehavior() {
			@Override
			protected @NonNull ItemStack execute(@NonNull BlockSource source, @NonNull ItemStack stack) {
				var component = stack.get(DataComponents.INSTRUMENT);
				if (component == null) {
					setSuccess(false);
					return stack;
				}
				var level = source.level();
				var pos = source.pos();
				var now = level.getGameTime();
				var dispenser = source.blockEntity();
				if (dispenser.getAttachedOrElse(READY_AT, 0L) > now) {
					// dud use when on cooldown
					setSuccess(true);
					return stack;
				}
				var instrument = component.instrument().value();
				level.playSound(null, pos, instrument.soundEvent().value(), SoundSource.RECORDS, instrument.range() / 16.0F, 1.0F);
				level.gameEvent(GameEvent.INSTRUMENT_PLAY, Vec3.atCenterOf(pos), GameEvent.Context.of(source.state()));
				dispenser.setAttached(READY_AT, now + Mth.floor(instrument.useDuration() * 20.0F));
				setSuccess(true);
				return stack;
			}

			/** No dispenser click */
			@Override
			protected void playSound(@NonNull BlockSource source) {
			}
		});
	}

}
