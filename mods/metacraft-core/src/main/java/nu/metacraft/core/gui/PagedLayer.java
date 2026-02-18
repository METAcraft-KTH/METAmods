package nu.metacraft.core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilderCreator;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.gui.layered.Layer;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class PagedLayer extends Layer {

	public static final Component NEXT_PAGE = Component.translatableWithFallback("gui.metacraft.next_page", "Next Page");
	public static final Component PREV_PAGE = Component.translatableWithFallback("gui.metacraft.prev_page", "Previous Page");

	private int page = 0;

	private final int prevPageIndex, nextPageIndex;
	private final GuiElementBuilderCreator<?> prevPageButton, nextPageButton;
	private List<GuiElement> elements;
	private final Int2IntFunction itemPlacer;
	private final int maxElementsPerPage;
	private final GuiElement background;

	private int numPages;

	public PagedLayer(
			int height, int width, int prevPageIndex, int nextPageIndex,
			GuiElementBuilderCreator<?> prevPageButton, GuiElementBuilderCreator<?> nextPageButton,
			Int2IntFunction itemPlacer, int maxElementsPerPage,
			GuiElement background
	) {
		super(height, width);
		this.prevPageIndex = prevPageIndex;
		this.nextPageIndex = nextPageIndex;
		this.prevPageButton = prevPageButton;
		this.nextPageButton = nextPageButton;
		this.itemPlacer = itemPlacer;
		this.maxElementsPerPage = maxElementsPerPage;
		this.background = background;
	}

	public PagedLayer(
			int height, int width, int prevPageIndex, int nextPageIndex,
			GuiElementBuilderCreator<?> prevPageButton, GuiElementBuilderCreator<?> nextPageButton,
			GuiElement background
	) {
		this(
				height, width, prevPageIndex, nextPageIndex, prevPageButton, nextPageButton,
				i -> i, Math.max((height-1)*width, width), background
		);
	}

	public PagedLayer(
			int height, int width,
			GuiElementBuilderCreator<?> prevPageButton,
			GuiElementBuilderCreator<?> nextPageButton,
			GuiElement background
	) {
		this(
				height, width, (height-1) * width, height * width - 1,
				prevPageButton, nextPageButton, background
		);
	}

	public int getMaxElementsPerPage() {
		return maxElementsPerPage;
	}

	private int getStartElementIndex(int page) {
		if (page == 0) return 0;
		if (page == 1) return maxElementsPerPage-1;
		return maxElementsPerPage - 1 + (maxElementsPerPage - 2) * (page - 1);
	}

	private void fixPagesCount() {
		if (elements.size() <= maxElementsPerPage) {
			numPages = 1;
			keepInPageRange();
			return;
		}
		int c = elements.size() - (maxElementsPerPage-1);
		if (c < maxElementsPerPage) {
			numPages = 2;
			keepInPageRange();
			return;
		}
		c -= maxElementsPerPage-1;
		numPages = Mth.ceil(2 + (double) c / (maxElementsPerPage-2));
		keepInPageRange();
	}

	private void keepInPageRange() {
		if (page >= numPages) {
			page = numPages-1;
		}
	}

	private boolean hasPrevButton() {
		return page != 0;
	}

	private boolean hasNextButton() {
		return page != numPages-1;
	}

	private boolean hasAnyPageButton() {
		return elements.size() > size;
	}

	public void updateButtons() {
		clearSlots();
		if (hasAnyPageButton()) {
			if (hasPrevButton()) {
				setSlot(prevPageIndex, prevPageButton.setCallback(this::prevPage));
			}
			if (hasNextButton()) {
				setSlot(nextPageIndex, nextPageButton.setCallback(this::nextPage));
			}
		}
		int pos = getStartElementIndex(page);
		for (int i = 0; i < maxElementsPerPage; i++) {
			int slot = itemPlacer.apply(i);
			if (hasAnyPageButton() && (slot == prevPageIndex && hasPrevButton()) || (slot == nextPageIndex && hasNextButton())) {
				continue;
			}
			if (pos >= elements.size()) break;
			setSlot(slot, elements.get(pos));
			pos++;
		}

		while (this.getFirstEmptySlot() != -1) {
			addSlot(background);
		}
	}

	public void setElements(List<GuiElement> elements) {
		this.elements = elements;
		fixPagesCount();
		updateButtons();
	}

	public void nextPage() {
		if (page < numPages - 1) {
			page++;
			updateButtons();
		}
	}

	public void prevPage() {
		if (page > 0) {
			page--;
			updateButtons();
		}
	}
}
