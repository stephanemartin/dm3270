package com.bytezone.dm3270.replyfield;

import com.bytezone.dm3270.Charset;
import com.bytezone.dm3270.buffers.Buffer;
import java.util.ArrayList;
import java.util.List;

public class CharacterSets extends QueryReplyField {

  private static final int GRAPHIC_ESCAPE_SUPPORT_FLAG = 0x80;
  private static final int CGCSGID_PRESENT_FLAG = 0x02;
  private static final int DEFAULT_CHARACTER_SLOT_WIDTH = 7;
  private static final int DEFAULT_CHARACTER_SLOT_HEIGHT = 12;
  private static final int[] FORM_TYPES = {0x00, 0x00, 0x00, 0x00};
  private static final int DESCRIPTOR_LENGTH = 7;
  private final byte[][] charSets = new byte[][]{
      {0x00, 0x00, 0x00, 0x02, (byte) 0xB9,  0x04, 0x17},
      {0x01, 0x00, (byte) 0xF1, 0x03, (byte) 0xC3, 0x01, 0x36}
  };

  private byte flags1;
  private byte flags2;
  private int defaultSlotWidth;
  private int defaultSlotHeight;
  private int loadTypes;
  private int descriptorLength;
  private final List<Descriptor> descriptors = new ArrayList<>();

  public CharacterSets(final Charset cs) {
    super(CHARACTER_SETS_REPLY);
    final byte[] code = cs.getCode();
    charSets[0][5] = code[0];
    charSets[0][6] = code[1];

    int ptr = createReply(4 + FORM_TYPES.length + 1 + charSets.length * DESCRIPTOR_LENGTH);
    reply[ptr++] = (byte) (GRAPHIC_ESCAPE_SUPPORT_FLAG | CGCSGID_PRESENT_FLAG & 0xff);
    reply[ptr++] = 0x00;
    reply[ptr++] = DEFAULT_CHARACTER_SLOT_WIDTH;
    reply[ptr++] = DEFAULT_CHARACTER_SLOT_HEIGHT;
    for (int formType : FORM_TYPES) {
      reply[ptr++] = (byte) formType;
    }
    reply[ptr++] = DESCRIPTOR_LENGTH;
    for (byte[] charset : charSets) {
      System.arraycopy(charset, 0, reply, ptr, DESCRIPTOR_LENGTH);
      ptr += DESCRIPTOR_LENGTH;
    }

    checkDataLength(ptr);
  }

  public CharacterSets(byte[] buffer) {
    super(buffer);

    assert data[1] == CHARACTER_SETS_REPLY;

    flags1 = data[2];
    flags2 = data[3];
    defaultSlotWidth = data[4] & 0xFF;
    defaultSlotHeight = data[5] & 0xFF;

    for (int i = 0; i < 4; i++) {
      loadTypes = ((loadTypes << 8) | (data[i + 6] & 0xFF));
    }

    descriptorLength = data[10] & 0xFF;

    for (int ptr = 11; ptr < data.length; ptr += descriptorLength) {
      descriptors.add(new Descriptor(data, ptr, descriptorLength));
    }
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder(super.toString());

    text.append(String.format("%n  flags1     : %02X", flags1));
    text.append(String.format("%n  flags2     : %02X", flags2));
    text.append(String.format("%n  SDW        : %d", defaultSlotWidth));
    text.append(String.format("%n  SDH        : %d", defaultSlotHeight));
    text.append(String.format("%n  load types : %08X", loadTypes));
    text.append(String.format("%n  desc len   : %d", descriptorLength));
    for (Descriptor descriptor : descriptors) {
      text.append(String.format("%n  Descriptor : %n%s", descriptor));
    }

    return text.toString();
  }

  public static class Descriptor {

    private final int set;
    private final byte flags;
    private final int localCharsetID;
    private final int slotWidth;
    private final int slotHeight;
    private final int startSubsection;
    private final int endSubsection;
    private int cgcsID;

    private Descriptor(byte[] buffer, int offset, int length) {
      set = buffer[offset] & 0xFF;
      flags = buffer[offset + 1];
      localCharsetID = buffer[offset + 2] & 0xFF;
      slotWidth = buffer[offset + 3] & 0xFF;
      slotHeight = buffer[offset + 4] & 0xFF;
      startSubsection = buffer[offset + 5] & 0xFF;
      endSubsection = buffer[offset + 6] & 0xFF;
      if (length > 7) {
        cgcsID = Buffer.unsignedLong(buffer, 7);
      }
    }

    @Override
    public String toString() {
      return String.format("    Set : %d", set)
          + String.format("%n    flags    : %02X", flags)
          + String.format("%n    charset  : %02X", localCharsetID)
          + String.format("%n    slot w   : %02X", slotWidth)
          + String.format("%n    slot h   : %02X", slotHeight)
          + String.format("%n    start    : %02X", startSubsection)
          + String.format("%n    end      : %02X", endSubsection)
          + String.format("%n    graphics : %d", cgcsID);
    }

  }

}
