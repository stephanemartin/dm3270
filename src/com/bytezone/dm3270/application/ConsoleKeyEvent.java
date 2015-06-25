package com.bytezone.dm3270.application;

import com.bytezone.dm3270.display.Screen;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class ConsoleKeyEvent implements EventHandler<KeyEvent>
{
  private final Screen screen;

  public ConsoleKeyEvent (Screen screen)
  {
    this.screen = screen;
  }

  @Override
  public void handle (KeyEvent e)       // onKeyTyped
  {
    if (screen.isKeyboardLocked () || e.isMetaDown () || e.isControlDown ()
        || e.isAltDown ())
    {
      // seems to be a bug in java
      String key = e.getCharacter ();
      if (e.isMetaDown () && !key.isEmpty () && key.charAt (0) == '-')    // osx fix
      {
        // screen.doFontSmaller ();
        screen.getFontManager ().smaller ();
        e.consume ();
        return;
      }

      return;
    }

    if (e.getCode () == KeyCode.UNDEFINED)
    {
      String c = e.getCharacter ();
      if (!c.isEmpty ())
      {
        char ch = c.charAt (0);
        if (ch >= 32 && ch < 0x7F)
        {
          screen.getScreenCursor ().typeChar ((byte) Utility.asc2ebc[ch]);
          e.consume ();
        }
      }
    }
  }
}