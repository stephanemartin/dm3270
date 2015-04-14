package com.bytezone.dm3270.application;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.buffers.MultiBuffer;
import com.bytezone.dm3270.buffers.ReplyBuffer;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.extended.AbstractExtendedCommand;
import com.bytezone.dm3270.extended.CommandHeader;
import com.bytezone.dm3270.session.SessionRecord;
import com.bytezone.dm3270.session.SessionRecord.SessionRecordType;
import com.bytezone.dm3270.session.SessionTable;
import com.bytezone.dm3270.streams.TelnetSocket.Source;

public class CommandPane extends TabPane
{
  private static final int TEXT_WIDTH = 540;

  public CommandPane (Screen screen, SessionTable table, boolean process)
  {
    //    TabPane tabPane = new TabPane ();
    setSide (Side.TOP);
    setTabClosingPolicy (TabClosingPolicy.UNAVAILABLE);

    final TextArea commandTextArea = getTextArea (TEXT_WIDTH);
    final TextArea replyTextArea = getTextArea (TEXT_WIDTH);
    final TextArea screenTextArea = getTextArea (TEXT_WIDTH);
    final TextArea fieldsTextArea = getTextArea (TEXT_WIDTH);
    final TextArea bufferTextArea = getTextArea (TEXT_WIDTH);
    final TextArea replyBufferTextArea = getTextArea (TEXT_WIDTH);

    final Tab tabCommand = getTab ("Command", commandTextArea);
    final Tab tabReply = getTab ("Reply", replyTextArea);
    final Tab tabScreen = getTab ("Screen", screenTextArea);
    final Tab tabFields = getTab ("Fields", fieldsTextArea);
    final Tab tabBuffer = getTab ("Buffer", bufferTextArea);
    final Tab tabReplyBuffer = getTab ("Reply Buffer", replyBufferTextArea);

    getTabs ().addAll (tabCommand, tabBuffer, tabFields, tabScreen, tabReply,
                       tabReplyBuffer);

    table
        .getSelectionModel ()
        .selectedItemProperty ()
        .addListener ( (ObservableValue<? extends SessionRecord> observable,
                          SessionRecord oldValue, SessionRecord newValue) //
                      -> replay (newValue, commandTextArea, bufferTextArea,
                                 replyTextArea, replyBufferTextArea, fieldsTextArea,
                                 screenTextArea, process, screen));
  }

  protected void replay (SessionRecord sessionRecord, TextArea textArea,
      TextArea bufferTextArea, TextArea replyTextArea, TextArea replyBufferTextArea,
      TextArea fieldsTextArea, TextArea screenTextArea, boolean process, Screen screen)
  {
    if (sessionRecord == null)     // nothing selected
      return;

    boolean ebcdic = false;
    CommandHeader commandHeader = null;

    if (sessionRecord.getDataRecordType () != SessionRecordType.TELNET)
    {
      ebcdic = true;
      if (sessionRecord.getMessage () instanceof AbstractExtendedCommand)
        commandHeader =
            ((AbstractExtendedCommand) sessionRecord.getMessage ()).getCommandHeader ();
    }

    ReplyBuffer message = sessionRecord.getMessage ();

    if (process)
      message.process ();       // only process the message when in Replay mode

    Buffer reply = message.getReply ();

    if (textArea != null)
    {
      textArea.setText ("");

      if (commandHeader != null)
      {
        textArea.appendText (commandHeader.toString ());
        textArea.appendText ("\n\n");
      }

      textArea.appendText (message.toString ());
      textArea.positionCaret (0);
    }

    if (bufferTextArea != null)
    {
      bufferTextArea.setText (Utility.toHex (sessionRecord.getBuffer (), ebcdic));
      bufferTextArea.positionCaret (0);
    }

    if (fieldsTextArea != null)
    {
      if (sessionRecord.getSource () == Source.SERVER)
      {
        fieldsTextArea.setText (screen.getFieldText ());
        fieldsTextArea.positionCaret (0);
      }
    }

    if (screenTextArea != null)
    {
      screenTextArea.setText (screen.getScreenText ());
      screenTextArea.positionCaret (0);
    }

    if (replyTextArea != null)
    {
      if (reply == null || reply.size () == 0)
        replyTextArea.setText ("");
      else
      {
        replyTextArea.setText ("");

        if (reply instanceof MultiBuffer)
        {
          int buffers = ((MultiBuffer) reply).totalBuffers ();
          for (int i = 0; i < buffers; i++)
          {
            appendCommand (replyTextArea, ((MultiBuffer) reply).getBuffer (i));
            replyTextArea.appendText ("\n\n");
          }
          replyTextArea.deleteText (replyTextArea.getLength () - 2,
                                    replyTextArea.getLength ());
        }
        else
          appendCommand (replyTextArea, reply);

        replyTextArea.positionCaret (0);
      }
    }

    if (replyBufferTextArea != null)
    {
      if (reply == null || reply.size () == 0)
        replyBufferTextArea.setText ("");
      else
      {
        replyBufferTextArea.setText (Utility.toHex (reply.getTelnetData (), ebcdic));
        replyBufferTextArea.positionCaret (0);
      }
    }
  }

  private void appendCommand (TextArea textArea, Buffer buffer)
  {

    if (buffer instanceof AbstractExtendedCommand)
    {
      CommandHeader header = ((AbstractExtendedCommand) buffer).getCommandHeader ();
      if (header != null)
      {
        textArea.appendText (header.toString ());
        textArea.appendText ("\n\n");
      }
    }
    textArea.appendText (buffer.toString ());
  }

  protected Tab getTab (String name, TextArea textArea)
  {
    Tab tab = new Tab ();
    tab.setText (name);
    tab.setContent (textArea);
    return tab;
  }

  protected TextArea getTextArea (int width)
  {
    TextArea textArea = new TextArea ();
    textArea.setEditable (false);
    textArea.setFont (Font.font ("Monospaced", 12));
    textArea.setPrefWidth (width);
    return textArea;
  }
}