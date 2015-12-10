package com.bytezone.dm3270.display;

import com.sun.javafx.tk.FontMetrics;
import com.sun.javafx.tk.Toolkit;

import javafx.scene.text.Font;

public class FontDetails
{
  public final int width;
  public final int height;
  public final int ascent;
  public final int descent;
  public final Font font;

  public FontDetails (Font font)
  {
    this.font = font;

    FontMetrics fontMetrics =
        Toolkit.getToolkit ().getFontLoader ().getFontMetrics (font);
    width = (int) (fontMetrics.computeStringWidth ("W") + 0.9);

    ascent = (int) (fontMetrics.getAscent () + fontMetrics.getLeading () + 0.9);
    descent = (int) (fontMetrics.getDescent () + 0.9);
    height = ascent + descent;
  }

  // called from ConsolePane.setFontData()
  public Font getFont (int sizeChange)
  {
    return Font.font (font.getName (), font.getSize () + sizeChange);
  }

  @Override
  public String toString ()
  {
    return String.format ("[%-18s %d w=%2d, h=%2d, a=%2d, d=%2d]", font.getName (),
                          (int) font.getSize (), width, height, ascent, descent);
  }
}