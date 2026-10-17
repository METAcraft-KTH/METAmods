package nu.metacraft.booklet.clienttest;

import eu.pb4.simpleimagerenderer.renderer.AbstractImageRenderer;
import eu.pb4.simpleimagerenderer.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * Drives one Simple Image Renderer render the way its own preview screen does: inside a frame, over
 * and over, so the entities' textures and the canvases' maps are uploaded before the picture is
 * kept. The last frame is written to {@code file} and the screen closes itself.
 */
final class RenderScreen extends Screen {
	private static final int FRAMES = 30;
	private final AbstractImageRenderer<?> renderer;
	private final Path file;
	private int frame;
	volatile boolean done;

	RenderScreen(AbstractImageRenderer<?> renderer, Path file) {
		super(Component.literal("render"));
		this.renderer = renderer;
		this.file = file;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (this.done) return;
		this.frame++;
		if (this.frame < FRAMES) {
			this.renderer.render((target, object, f) -> {}, true);
		} else if (this.frame == FRAMES) {
			this.renderer.render((target, object, f) -> {
				try {
					RenderUtils.writeToNativeImage(target, image -> image.writeToFile(this.file));
				} catch (Throwable e) {
					throw new AssertionError("could not write " + this.file, e);
				}
			}, false);
		} else if (this.frame >= FRAMES + 10) {
			// The write copies the target off the GPU over the next frames; only then may it go.
			this.done = true;
			this.renderer.close();
			this.minecraft.gui.setScreen(null);
		}
	}
}
