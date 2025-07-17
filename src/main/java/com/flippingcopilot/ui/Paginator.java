package com.flippingcopilot.ui;

import lombok.Getter;
import lombok.Setter;
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
	@Getter
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
		arrowLeft.addMouseListener(onDecreasePage());
		arrowRight.addMouseListener(onIncreasePage());
	}

	public void setTotalPages(int totalPages) {
		this.totalPages = totalPages;
		if(pageNumber > this.totalPages) {
			pageNumber = 1;
			onPageChange.accept(pageNumber);
		}
		statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
	}

	public void setTotalPagesWithoutEffect(int totalPages) {
		this.totalPages = totalPages;
		statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
		statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
	}

	private MouseAdapter onIncreasePage() {
		return new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (pageNumber < totalPages) {
					pageNumber++;
					onPageChange.accept(pageNumber);
					statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				arrowRight.setIcon(HIGHLIGHTED_ARROW_RIGHT);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				arrowRight.setIcon(ARROW_RIGHT);
			}
		};
	}

	private MouseAdapter onDecreasePage() {
		return new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (pageNumber > 1) {
					pageNumber--;
					onPageChange.accept(pageNumber);
					statusText.setText(String.format("Page %d of %d", pageNumber, totalPages));
				}
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				arrowLeft.setIcon(HIGHLIGHTED_ARROW_LEFT);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				arrowLeft.setIcon(ARROW_LEFT);
			}
		};
	}
}
