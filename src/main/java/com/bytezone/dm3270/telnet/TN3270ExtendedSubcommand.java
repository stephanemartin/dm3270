package com.bytezone.dm3270.telnet;

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.streams.TelnetState;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TN3270ExtendedSubcommand extends TelnetSubcommand {

  private static final Logger LOG = LoggerFactory.getLogger(TN3270ExtendedSubcommand.class);

  private static final byte EXT_DEVICE_TYPE = 2;
  private static final byte EXT_FUNCTIONS = 3;

  private static final byte EXT_IS = 4;
  private static final byte EXT_REQUEST = 7;
  private static final byte EXT_SEND = 8;

  private SubType subType;
  private String luName = "";
  private List<Function> functions;
  private String functionsList = "";

  private enum SubType {
    IS, REQUEST, DEVICE_TYPE
  }

  public enum Function {
    BIND_IMAGE, RESPONSES, SYSREQ, REASON
  }

  public TN3270ExtendedSubcommand(byte[] buffer, int offset, int length,
      TelnetState telnetState) {
    super(buffer, offset, length, telnetState);

    switch (buffer[3]) {
      case EXT_SEND:
        type = SubcommandType.SEND;
        if (buffer[4] == EXT_DEVICE_TYPE) {
          subType = SubType.DEVICE_TYPE;
        }
        break;

      case EXT_DEVICE_TYPE:
        type = SubcommandType.DEVICE_TYPE;
        if (buffer[4] == EXT_REQUEST) {
          subType = SubType.REQUEST;
          value = new String(buffer, 5, length - 7);
        } else if (buffer[4] == EXT_IS) {
          subType = SubType.IS;
          for (int ptr = 6; ptr < length; ptr++) {
            if (buffer[ptr] == 1) {
              value = new String(buffer, 5, ptr - 5);          // value before the ptr
              luName = new String(buffer, ptr + 1, length - ptr - 3);   // after
              break;
            }
          }
          // don't know if this can happen
          if (value == null) {
            value = new String(buffer, 5, length - 5);
          }
        }
        break;

      case EXT_FUNCTIONS:
        type = SubcommandType.FUNCTIONS;
        if (buffer[4] == EXT_REQUEST) {
          subType = SubType.REQUEST;
          setFunctions(buffer, length);
        } else if (buffer[4] == EXT_IS) {
          subType = SubType.IS;
          setFunctions(buffer, length);
        }
        break;

      default:
        throw new InvalidParameterException(
            String.format("Unknown Extended: %02X", buffer[3]));
    }
  }

  private void setFunctions(byte[] buffer, int length) {
    StringBuilder funcList = new StringBuilder();
    functions = new ArrayList<>();

    for (int ptr = 5, max = length - 2; ptr < max; ptr++) {
      if (buffer[ptr] == 0) {
        functions.add(Function.BIND_IMAGE);
        funcList.append("BIND, ");
      } else if (buffer[ptr] == 2) {
        functions.add(Function.RESPONSES);
        funcList.append("RESPONSES, ");
      } else if (buffer[ptr] == 4) {
        functions.add(Function.SYSREQ);
        funcList.append("SYSREQ, ");
      } else if (buffer[ptr] == 5) {
        functions.add(Function.REASON);
        funcList.append("REASON, ");
      } else {
        throw new InvalidParameterException(
            String.format("Unknown function: %02X%n", buffer[ptr]));
      }
    }

    if (funcList.length() > 0) {
      funcList.deleteCharAt(funcList.length() - 1);
      funcList.deleteCharAt(funcList.length() - 1);
    }
    functionsList = funcList.toString();
  }

  @Override
  public void process(Screen screen) {
    if (type == SubcommandType.SEND && subType == SubType.DEVICE_TYPE) {
      byte[] header = {TelnetCommand.IAC, TelnetCommand.SB, TN3270E, EXT_DEVICE_TYPE,
          EXT_REQUEST};
      String terminalType = telnetState.doDeviceType();
      byte[] terminal = terminalType.getBytes(StandardCharsets.US_ASCII);
      byte[] reply = new byte[header.length + terminal.length + 2];

      System.arraycopy(header, 0, reply, 0, header.length);
      System.arraycopy(terminal, 0, reply, header.length, terminal.length);
      reply[reply.length - 2] = TelnetCommand.IAC;
      reply[reply.length - 1] = TelnetCommand.SE;

      setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
    }

    // after the server assigns our device type, request these three functions
    if (type == SubcommandType.DEVICE_TYPE && subType == SubType.IS) {
      byte[] reply =
          {TelnetCommand.IAC, TelnetCommand.SB, TN3270E, EXT_FUNCTIONS, EXT_REQUEST,
              0x00, 0x02, 0x04, TelnetCommand.IAC, TelnetCommand.SE};
      setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
    }

    switch (subType) {
      // the server disagrees with our request and is making a counter-request
      case REQUEST:
        if (type == SubcommandType.FUNCTIONS) {
          // copy the server's proposal and accept it
          byte[] reply = new byte[data.length];
          System.arraycopy(data, 0, reply, 0, data.length);
          reply[4] = EXT_IS;        // replace REQUEST with IS
          setReply(new TN3270ExtendedSubcommand(reply, 0, reply.length, telnetState));
        }
        break;

      // if the server agrees to our request
      case IS:
        if (type == SubcommandType.FUNCTIONS) {
          telnetState.setFunctions(functions);
        } else if (type == SubcommandType.DEVICE_TYPE) {
          telnetState.setDeviceType(getValue());
        }
        break;

      case DEVICE_TYPE:
        break;

      default:
        LOG.warn("Unknown subtype: {}", subType);
        break;
    }
  }

  @Override
  public String toString() {
    switch (type) {
      case SEND:
        return type + " " + subType;
      case FUNCTIONS:
        return type + " " + subType + " : " + functionsList;
      case DEVICE_TYPE:
        String connectText = luName.isEmpty() ? "" : " (" + luName + ")";
        return type + " " + subType + " " + value + connectText;
      default:
        return "SUB: " + "Unknown";
    }
  }

}
