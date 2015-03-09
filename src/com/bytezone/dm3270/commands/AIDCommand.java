package com.bytezone.dm3270.commands;

import java.util.ArrayList;
import java.util.List;

import com.bytezone.dm3270.display.Cursor;
import com.bytezone.dm3270.display.Field;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.orders.BufferAddress;
import com.bytezone.dm3270.orders.BufferAddressSource;
import com.bytezone.dm3270.orders.Order;
import com.bytezone.dm3270.orders.SetBufferAddressOrder;
import com.bytezone.dm3270.orders.TextOrder;

public class AIDCommand extends Command implements BufferAddressSource
{
  public static final byte NO_AID_SPECIFIED = 0x60;
  public static final byte AID_READ_PARTITION = 0x61;

  private static byte[] keys = { //
      0, (byte) 0x60, (byte) 0x7D, (byte) 0xF1, (byte) 0xF2, (byte) 0xF3, (byte) 0xF4,
          (byte) 0xF5, (byte) 0xF6, (byte) 0xF7, (byte) 0xF8, (byte) 0xF9, (byte) 0x7A,
          (byte) 0x7B, (byte) 0x7C, (byte) 0xC1, (byte) 0xC2, (byte) 0xC3, (byte) 0xC4,
          (byte) 0xC5, (byte) 0xC6, (byte) 0xC7, (byte) 0xC8, (byte) 0xC9, (byte) 0x4A,
          (byte) 0x4B, (byte) 0x4C, (byte) 0x6C, (byte) 0x6E, (byte) 0x6B, (byte) 0x6D,
          (byte) 0x6A, (byte) 0x61 };

  private static String[] keyNames = { //
      "Not found", "No AID", "ENTR", "PF1", "PF2", "PF3", "PF4", "PF5", "PF6", "PF7",
          "PF8", "PF9", "PF10", "PF11", "PF12", "PF13", "PF14", "PF15", "PF16", "PF17",
          "PF18", "PF19", "PF20", "PF21", "PF22", "PF23", "PF24", "PA1", "PA2", "PA3",
          "CLR", "CLR Partition", "Read Partition" };

  private int key;
  private byte keyCommand;
  private BufferAddress cursorAddress;

  private final List<AIDField> aidFields = new ArrayList<> ();
  private final List<Order> orders = new ArrayList<> ();
  private int textOrders;

  public AIDCommand (Screen screen, byte[] buffer, int offset, int length)
  {
    super (buffer, offset, length, screen);

    keyCommand = buffer[offset];
    key = findKey (keyCommand);

    if (length <= 1)
    {
      cursorAddress = null;
      return;
    }

    cursorAddress = new BufferAddress (buffer[offset + 1], buffer[offset + 2]);

    int ptr = offset + 3;
    int max = offset + length;
    Order previousOrder = null;
    SetBufferAddressOrder sba = null;

    while (ptr < max)
    {
      Order order = Order.getOrder (buffer, ptr, max);
      if (!order.rejected ())
      {
        if (previousOrder != null && previousOrder.matches (order))
          previousOrder.incrementDuplicates ();
        else
        {
          orders.add (order);
          previousOrder = order;
        }

        if (order instanceof TextOrder)
        {
          // create an AIDField when a TextOrder is preceded by a SetBufferAddressOrder
          // these are created by screen.readModifiedFields () in response to the 
          // user pressing ENTR/PFxx
          if (sba != null)
            aidFields.add (new AIDField (sba, (TextOrder) order));
          textOrders++;
        }

        if (order instanceof SetBufferAddressOrder)
          sba = (SetBufferAddressOrder) order;
        else
          sba = null;

      }
      ptr += order.size ();
    }
  }

  private int findKey (byte keyCommand)
  {
    for (int i = 1; i < keys.length; i++)
      if (keys[i] == keyCommand)
        return i;
    return 0;
  }

  // copy modified fields back to the screen - only used in Replay mode
  // Normally an AID is a reply command (which is never processed)

  @Override
  public void process ()
  {
    Cursor cursor = screen.getScreenCursor ();
    cursor.setVisible (true);

    // test to see whether this is data entry that was null suppressed into moving
    // elsewhere on the screen (like the TSO logoff command) - purely aesthetic
    boolean done = false;
    if (aidFields.size () == 1)
    {
      int cursorOldLocation = cursor.getLocation ();
      int cursorDistance = cursorAddress.getLocation () - cursorOldLocation;

      byte[] buffer = aidFields.get (0).getBuffer ();
      Field currentField = cursor.getCurrentField ();
      if (buffer.length == cursorDistance && currentField != null
          && currentField.contains (cursorOldLocation))
      {
        for (byte b : buffer)
          cursor.typeChar (b);   // send characters through the old cursor
        done = true;
      }
    }

    if (!done)
      for (AIDField aidField : aidFields)
      {
        Field field = screen.getField (aidField.getLocation ());
        if (field != null)    // in replay mode we cannot rely on the fields list
        {
          field.setText (aidField.getBuffer ());
          field.draw ();
        }
      }

    // place cursor in new location
    if (cursorAddress != null)
      cursor.moveTo (cursorAddress.getLocation ());
  }

  @Override
  public BufferAddress getBufferAddress ()
  {
    return cursorAddress;
  }

  public byte getKeyCommand ()
  {
    return keyCommand;
  }

  @Override
  public String getName ()
  {
    return "AID : " + keyNames[key];
  }

  public static byte getKey (String name)
  {
    int ptr = 0;
    for (String keyName : keyNames)
    {
      if (keyName.equals (name))
        return keys[ptr];
      ptr++;
    }
    return -1;
  }

  @Override
  public String brief ()
  {
    return keyNames[key];
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("AID     : %-12s : %02X%n", keyNames[key], keyCommand));
    text.append (String.format ("Cursor  : %s%n", cursorAddress));

    if (aidFields.size () > 0)
    {
      text.append (String.format ("%nModified fields  : %d", aidFields.size ()));
      for (AIDField aidField : aidFields)
      {
        text.append ("\nField   : ");
        text.append (aidField);
      }
    }
    // response to a read buffer request
    else if (orders.size () > 0)
    {
      text.append (String.format ("%nOrders  : %d%n", orders.size () - textOrders));
      text.append (String.format ("Text    : %d%n", textOrders));

      // if the list begins with a TextOrder then tab out the missing columns
      if (orders.size () > 0 && orders.get (0) instanceof TextOrder)
        text.append (String.format ("%40s", ""));

      for (Order order : orders)
      {
        String fmt = (order instanceof TextOrder) ? "%s" : "%n%-40s";
        text.append (String.format (fmt, order));
      }
    }
    return text.toString ();
  }

  private class AIDField
  {
    SetBufferAddressOrder sbaOrder;
    TextOrder textOrder;

    public AIDField (SetBufferAddressOrder sbaOrder, TextOrder textOrder)
    {
      this.sbaOrder = sbaOrder;
      this.textOrder = textOrder;
    }

    public int getLocation ()
    {
      return sbaOrder.getBufferAddress ().getLocation ();
    }

    public byte[] getBuffer ()
    {
      return textOrder.getBuffer ();
    }

    @Override
    public String toString ()
    {
      return String.format ("%s : %s", sbaOrder, textOrder);
    }
  }
}