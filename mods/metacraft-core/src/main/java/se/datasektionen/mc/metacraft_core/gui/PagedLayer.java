package se.datasektionen.mc.metacraft_core.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilderInterface;
import eu.pb4.sgui.api.elements.GuiElementInterface;
import eu.pb4.sgui.api.gui.layered.Layer;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class PagedLayer extends Layer {

	public static final Text NEXT_PAGE = Text.translatableWithFallback("gui.metacraft.next_page", "Next Page");
	public static final Text PREV_PAGE = Text.translatableWithFallback("gui.metacraft.prev_page", "Previous Page");

	private int page = 0;

	private final int prevPageIndex, nextPageIndex;
	private final GuiElementBuilderInterface<?> prevPageButton, nextPageButton;
	private List<GuiElementInterface> elements;
	private final Int2IntFunction itemPlacer;
	private final int maxElementsPerPage;
	private final GuiElementInterface background;

	private int numPages;

	public PagedLayer(
			int height, int width, int prevPageIndex, int nextPageIndex,
			GuiElementBuilderInterface<?> prevPageButton, GuiElementBuilderInterface<?> nextPageButton,
			Int2IntFunction itemPlacer, int maxElementsPerPage,
			GuiElementInterface background
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
			GuiElementBuilderInterface<?> prevPageButton, GuiElementBuilderInterface<?> nextPageButton,
			GuiElementInterface background
	) {
		this(
				height, width, prevPageIndex, nextPageIndex, prevPageButton, nextPageButton,
				i -> i, height * width, background
		);
	}

	public PagedLayer(
			int height, int width,
			GuiElementBuilderInterface<?> prevPageButton,
			GuiElementBuilderInterface<?> nextPageButton,
			GuiElementInterface background
	) {
		this(
				height, width, (height-1) * width, height * width - 1,
				prevPageButton, nextPageButton, background
		);
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
		numPages = MathHelper.ceil(2 + (double) c / (maxElementsPerPage-2));
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
				setSlot(prevPageIndex, prevPageButton.setCallback((index, type, action) -> {
					prevPage();
				}));
			}
			if (hasNextButton()) {
				setSlot(nextPageIndex, nextPageButton.setCallback((index, type, action) -> {
					nextPage();
				}));
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

	public void setElements(List<GuiElementInterface> elements) {
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
