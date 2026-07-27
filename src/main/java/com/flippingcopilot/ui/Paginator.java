package com.flippingcopilot.ui;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.flippingcopilot.ui.UIUtilities.BUTTON_HOVER_LUMINANCE;

@Slf4j
public class Paginator extends JPanel {

	public final BufferedImage ARROW_ICON = ImageUtil.loadImageResource(getClass(),"/small_open_arrow.png");
	public final Icon ARROW_RIGHT = new ImageIcon(ARROW_ICON);
	public final Icon HIGHLIGHTED_ARROW_RIGHT = new ImageIcon(ImageUtil.luminanceScale(ARROW_ICON, BUTTON_HOVER_LUMINANCE));
	public final Icon ARROW_LEFT = new ImageIcon(ImageUtil.rotateImage(ARROW_ICON, Math.toRadians(180)));
	public final Icon HIGHLIGHTED_ARROW_LEFT = new ImageIcon(ImageUtil.luminanceScale(ImageUtil.rotateImage(ARROW_ICON, Math.toRadians(180)), BUTTON_HOVER_LUMINANCE));

	@Getter
	private int pageNumber = 1;
	private int totalPages = 1;
	private final JLabel statusText = new JLabel("Page 1 of 1", SwingUtilities.CENTER);
	private final JLabel arrowRight= new JLabel(ARROW_RIGHT);
	private final JLabel arrowLeft =  new JLabel(ARROW_LEFT);
	private final Consumer<Integer> onPageChange;

	public Paginator(Consumer<Integer> onPageChange) {
		this.onPageChange = onPageChange;
		this.statusText.setFont(FontManager.getRunescapeFont());
		this.arrowRight.setForeground(Color.blue);
		setLayout(new FlowLayout());
		add(arrowLeft);
		add(statusText);
		add(arrowRight);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(3, 0, 0, 0));
		arrowLeft.addMouseListener(onPageStep(arrowLeft, ARROW_LEFT, HIGHLIGHTED_ARROW_LEFT, -1, () -> pageNumber > 1));
		arrowRight.addMouseListener(onPageStep(arrowRight, ARROW_RIGHT, HIGHLIGHTED_ARROW_RIGHT, 1, () -> pageNumber < totalPages));
	}

	private void updateStatusText() {
		statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
		if(pageNumber > this.totalPages) {
			pageNumber = 1;
			onPageChange.accept(pageNumber);
		}
		updateStatusText();
	}

	public void setTotalPagesWithoutEffect(int totalPages) {
		this.totalPages = totalPages;
		updateStatusText();
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
		updateStatusText();
	}

	private MouseAdapter onPageStep(JLabel arrow, Icon icon, Icon highlightedIcon, int delta, BooleanSupplier canStep) {
		return new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (canStep.getAsBoolean()) {
					pageNumber += delta;
					onPageChange.accept(pageNumber);
					updateStatusText();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				arrow.setIcon(highlightedIcon);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				arrow.setIcon(icon);
			}
		};
	}
}
