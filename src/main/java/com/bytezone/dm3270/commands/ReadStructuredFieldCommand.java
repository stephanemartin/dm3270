package com.bytezone.dm3270.commands;

import com.bytezone.dm3270.Charset;
import com.bytezone.dm3270.buffers.Buffer;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.ScreenDimensions;
import com.bytezone.dm3270.replyfield.CharacterSets;
import com.bytezone.dm3270.replyfield.Color;
import com.bytezone.dm3270.replyfield.Highlight;
import com.bytezone.dm3270.replyfield.ImplicitPartition;
import com.bytezone.dm3270.replyfield.QueryReplyField;
import com.bytezone.dm3270.replyfield.QueryReplyField.ReplyType;
import com.bytezone.dm3270.replyfield.ReplyModes;
import com.bytezone.dm3270.replyfield.Summary;
import com.bytezone.dm3270.replyfield.UsableArea;
import com.bytezone.dm3270.streams.TelnetState;
import com.bytezone.dm3270.structuredfields.DefaultStructuredField;
import com.bytezone.dm3270.structuredfields.QueryReplySF;
import com.bytezone.dm3270.structuredfields.StructuredField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadStructuredFieldCommand extends Command {

  private static final Logger LOG = LoggerFactory.getLogger(ReadStructuredFieldCommand.class);

  private static final String SEPARATOR =
      "\n-------------------------------------------------------------------------";

  private final List<StructuredField> structuredFields = new ArrayList<>();

  private ScreenDimensions screenDimensions;

  public ReadStructuredFieldCommand(TelnetState telnetState, Charset charset) {
    this(buildReply(telnetState), charset);
  }

  public ReadStructuredFieldCommand(List<ReplyType> queryList, TelnetState telnetState,
      Charset charset) {
    this(buildReply(queryList, telnetState), charset);
  }

  private ReadStructuredFieldCommand(byte[] buffer, Charset charset) {
    this(buffer, 0, buffer.length, charset);
  }

  public ReadStructuredFieldCommand(byte[] buffer, int offset, int length, Charset charset) {
    super(buffer, offset, length);

    assert data[0] == AIDCommand.AID_STRUCTURED_FIELD;

    int ptr = 1;
    int max = data.length;

    List<QueryReplyField> replies = new ArrayList<>();
    while (ptr < max) {
      int size = Buffer.unsignedShort(data, ptr) - 2;
      ptr += 2;

      switch (data[ptr]) {
        case StructuredField.QUERY_REPLY:
          QueryReplySF queryReply = new QueryReplySF(data, ptr, size, charset);
          structuredFields.add(queryReply);
          replies.add(queryReply.getQueryReplyField());
          break;

        default:
          LOG.warn("Unknown Structured Field: {}", Buffer.toHex(data, ptr, 1));
          structuredFields.add(new DefaultStructuredField(data, ptr, size, charset));
      }
      ptr += size;
    }

    if (replies.size() > 0) {
      for (QueryReplyField reply : replies) {
        reply.addReplyFields(replies);         // allow each QRF to see all the others
        if (screenDimensions == null && reply instanceof UsableArea) {
          screenDimensions = ((UsableArea) reply).getScreenDimensions();
        }
      }
    }
  }

  private static byte[] buildReply(TelnetState telnetState) {
    return buildReplyBytes(buildAvailableReplyFields(telnetState));
  }

  private static byte[] buildReply(List<ReplyType> queryList, TelnetState telnetState) {
    Map<ReplyType, QueryReplyField> replyFields = buildAvailableReplyFields(telnetState).stream()
        .collect(Collectors.toMap(QueryReplyField::getReplyType, r -> r));
    return buildReplyBytes(queryList.stream()
        .map(replyFields::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList()));
  }

  private static List<QueryReplyField> buildAvailableReplyFields(TelnetState telnetState) {
    final ScreenDimensions screenDimensions = telnetState.getSecondary();

    return Arrays.asList(new UsableArea(screenDimensions.rows, screenDimensions.columns),
        new Color(),
        new Highlight(),
        new ImplicitPartition(screenDimensions.rows, screenDimensions.columns),
        new ReplyModes(),
        new CharacterSets(telnetState.getCharset())
        );
  }

  private static byte[] buildReplyBytes(List<QueryReplyField> replyFields) {
    List<QueryReplyField> replyFieldsWithSummary = new ArrayList<>();
    replyFieldsWithSummary.add(new Summary(replyFields));
    replyFieldsWithSummary.addAll(replyFields);
    int replyLength = replyFieldsWithSummary.stream()
        .mapToInt(QueryReplyField::replySize)
        .sum() + 1;
    byte[] buffer = new byte[replyLength];
    int ptr = 0;
    buffer[ptr++] = AIDCommand.AID_STRUCTURED_FIELD;
    for (QueryReplyField reply : replyFieldsWithSummary) {
      ptr = reply.packReply(buffer, ptr);
    }
    assert ptr == replyLength;
    return buffer;
  }

  @Override
  public void process(Screen screen) {
  }

  @Override
  public String getName() {
    return "Read SF";
  }

  @Override
  public String toString() {
    StringBuilder text =
        new StringBuilder(String.format("RSF (%d):", structuredFields.size()));

    for (StructuredField sf : structuredFields) {
      text.append(SEPARATOR);
      text.append("\n");
      text.append(sf);
    }

    if (structuredFields.size() > 0) {
      text.append(SEPARATOR);
    }

    return text.toString();
  }

}
