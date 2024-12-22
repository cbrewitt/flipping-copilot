/* Copyright (c) 2018, Jasper <Jasper0781@gmail.com>
 * Copyright (c) 2020, melky <https://github.com/melkypie>
 * Copyright (c) 2024, Cillian Brewitt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.flippingcopilot.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class WidgetHighlightOverlay extends Overlay
{
    private final Widget widget;
    private final Color color;
    private final Rectangle relativeBounds;

    public WidgetHighlightOverlay(final Widget widget, Color color, Rectangle relativeBounds)
    {
        this.widget = widget;
        this.color = color;
        this.relativeBounds = relativeBounds;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
        setMovable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (widget == null)
        {
            return null;
        }

        Rectangle highlightBounds = widget.getBounds();

        if (highlightBounds == null)
        {
            return null;
        }

        highlightBounds.x += relativeBounds.x;
        highlightBounds.y += relativeBounds.y;
        highlightBounds.width = relativeBounds.width;
        highlightBounds.height = relativeBounds.height;

        drawHighlight(graphics, highlightBounds);
        return null;
    }

    private void drawHighlight(Graphics2D graphics, Rectangle bounds)
    {
        graphics.setColor(color);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
}