package com.bytezone.dm3270.orders;

import com.bytezone.dm3270.Charset;
import com.bytezone.dm3270.display.DisplayScreen;
import com.bytezone.dm3270.display.Pen;
import com.bytezone.dm3270.display.Screen;

public class RepeatToAddressOrder extends Order {

  private final BufferAddress stopAddress;
  private char repeatCharacter;
  private byte rptChar;

  public RepeatToAddressOrder(byte[] buffer, int offset, Charset charset) {
    assert buffer[offset] == Order.REPEAT_TO_ADDRESS;

    stopAddress = new BufferAddress(buffer[offset + 1], buffer[offset + 2]);

    if (buffer[offset + 3] == Order.GRAPHICS_ESCAPE) {
      repeatCharacter = charset.getChar(buffer[offset + 4]);
      // offset + 5 can be used, but I haven't seen one yet
      rptChar = buffer[offset + 4];

      this.buffer = new byte[6];
    } else {
      repeatCharacter = charset.getChar(buffer[offset + 3]);
      rptChar = buffer[offset + 3];

      this.buffer = new byte[4];
    }

    System.arraycopy(buffer, offset, this.buffer, 0, this.buffer.length);

    if (rptChar == 0) {
      repeatCharacter = ' ';
    }
  }

  @Override
  public void process(DisplayScreen screen) {
    int stopLocation = stopAddress.getLocation();

    Pen pen = screen.getPen();
    if (pen.getPosition() == stopLocation) {
      screen.clearScreen(((Screen) screen).getCurrentScreenOption());
    } else {
      while (pen.getPosition() != stopLocation) {
        pen.write(rptChar);
      }
    }
  }

  @Override
  public String toString() {
    return String.format("RTA : %-12s : %02X [%1.1s]", stopAddress, rptChar,
        repeatCharacter);
  }

}
