package com.bytezone.dm3270;

import com.bytezone.dm3270.buffers.Buffer;
import java.nio.charset.UnsupportedCharsetException;

public enum Charset {
  CP037,
  CP273,
  CP277,
  CP278,
  CP280,
  CP284,
  CP285,
  CP297,
  CP420,
  CP424,
  CP437,
  CP500,
  CP737,
  CP775,
  CP838,
  CP850,
  CP852,
  CP855,
  CP857,
  CP860,
  CP861,
  CP862,
  CP863,
  CP864,
  CP865,
  CP866,
  CP868,
  CP869,
  CP870,
  CP871,
  CP874,
  CP875,
  CP918,
  CP921,
  CP922,
  CP930,
  CP933,
  CP935,
  CP937,
  CP939,
  CP942,
  CP943,
  CP948,
  CP949,
  CP950,
  CP964,
  CP970,
  CP1006,
  CP1025,
  CP1026,
  CP1046,
  CP1047,
  CP1097,
  CP1098,
  CP1112,
  CP1122,
  CP1123,
  CP1140,
  CP1141,
  CP1142,
  CP1143,
  CP1144,
  CP1145,
  CP1146,
  CP1147,
  CP1148,
  CP1149,
  CP1166,
  CP1250,
  CP1251,
  CP1252,
  CP1253,
  CP1254,
  CP1255,
  CP1256,
  CP1257,
  CP1258,
  CP1381,
  CP1383,
  CP33722;

  private char[] charsMapping;
  private java.nio.charset.Charset charset;

  public synchronized void load() throws UnsupportedCharsetException  {
    if (charset != null) {
      return;
    }
    charset = java.nio.charset.Charset.forName(name());
    byte[] baseBytes = new byte[256];
    for (int i = 0; i < 256; i++) {
      baseBytes[i] = (byte) i;
    }
    charsMapping = new String(baseBytes, charset).toCharArray();
  }

  public char getChar(byte value) {
    return charsMapping[value & 0xFF];
  }

  public String getString(byte[] buffer) {
    return new String(buffer, charset);
  }

  public String getString(byte[] buffer, int offset, int length) {
    return new String(buffer,
        offset + length > buffer.length ? buffer.length - offset - 1 : offset,
        length, charset);
  }

  public byte[] getCode() {
    final byte[] bytes = new byte[2];
    try {
      final int i = Integer.parseInt(name().substring(2));
      bytes[1] = (byte) i;
      bytes[0] = (byte) (i >>> 8);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid charset name: " + name());
    }
    return bytes;
  }

  public String toHex(byte[] b) {
    return toHex(b, 0, b.length);
  }

  public String toHex(byte[] b, int offset, int length) {
    StringBuilder text = new StringBuilder();
    for (int ptr = offset, max = offset + length; ptr < max; ptr += Buffer.HEX_LINE_SIZE) {
      StringBuilder hexLine = new StringBuilder();
      StringBuilder textLine = new StringBuilder();
      for (int linePtr = 0; linePtr < Buffer.HEX_LINE_SIZE && ptr + linePtr < max; linePtr++) {
        int val = b[ptr + linePtr] & 0xFF;
        hexLine.append(String.format("%02X ", val));
        if (val < 0x40 || val == 0xFF) {
          textLine.append('.');
        } else {
          textLine.append(new String(b, ptr + linePtr, 1, charset));
        }
      }
      text.append(String.format("%04X  %-48s %s%n", ptr, hexLine.toString(), textLine.toString()));
    }
    return text.length() > 0 ? text.substring(0, text.length() - 1) : text.toString();
  }

}
