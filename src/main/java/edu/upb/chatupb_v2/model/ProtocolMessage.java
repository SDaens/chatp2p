package edu.upb.chatupb_v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public abstract class ProtocolMessage {

    public enum Code {
        REQUEST("001", 2, "ID", "Nombre"),
        ACCEPT("002", 2, "ID", "Nombre"),
        REJECT("003", 0),
        HELLO_BROADCAST("004", 1, "ID"),
        HELLO_ACCEPT("005", 1, "ID"),
        HELLO_REJECT("006", 0),
        CHAT("007", 3, "ID_user", "ID_mensaje", "Mensaje"),
        RECEIPT("008", 1, "ID_mensaje"),
        DELETE("009", 1, "ID_mensaje"),
        BUZZ("010", 1, "ID_ver"),
        PIN("011", 1, "ID_mensaje"),
        SEEN("012", 3, "ID_user", "ID_run", "Mensaje"),
        THEME("013", 2, "ID_user", "ID_tema"),
        OUTLINE("0018", 1, "ID_user");

        private final String value;
        private final int paramCount;
        private final List<String> paramNames;

        Code(String value, int paramCount, String... paramNames) {
            this.value = value;
            this.paramCount = paramCount;
            List<String> names = new ArrayList<>();
            if (paramNames != null) {
                Collections.addAll(names, paramNames);
            }
            this.paramNames = Collections.unmodifiableList(names);
        }

        public String value() {
            return value;
        }

        public int paramCount() {
            return paramCount;
        }

        public List<String> paramNames() {
            return paramNames;
        }

        public static Code from(String raw) {
            String normalized = raw == null ? "" : raw.trim();
            for (Code code : values()) {
                if (code.value.equals(normalized)) {
                    return code;
                }
            }
            throw new IllegalArgumentException("Unknown protocol code: " + raw);
        }
    }

    private final List<String> params;

    protected ProtocolMessage(List<String> params) {
        this.params = Collections.unmodifiableList(new ArrayList<>(params));
    }

    public static ProtocolMessage of(Code code, String... params) {
        Objects.requireNonNull(code, "code");
        List<String> parts = new ArrayList<>();
        if (params != null) {
            Collections.addAll(parts, params);
        }
        return fromParts(code, parts);
    }

    public static ProtocolMessage parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty protocol line");
        }
        String[] split = line.split(Pattern.quote("|"));
        if (split.length == 0) {
            throw new IllegalArgumentException("Invalid protocol line: " + line);
        }
        String code = split[0].trim();
        return switch (code) {
            case "001", "01", "1" -> parseRequest(split);
            case "002", "02", "2" -> parseAccept(split);
            case "003", "03", "3" -> parseReject(split);
            case "004", "04", "4" -> parseHelloBroadcast(split);
            case "005", "05", "5" -> parseHelloAccept(split);
            case "006", "06", "6" -> parseHelloReject(split);
            case "007", "07", "7" -> parseChat(split);
            case "008", "08", "8" -> parseReceipt(split);
            case "009" -> parseDelete(split);
            case "010" -> parseBuzz(split);
            case "011" -> parsePin(split);
            case "012", "12" -> parseSeen(split);
            case "013" -> parseTheme(split);
            case "0018", "018", "18" -> parseOutLine(split);
            default -> throw new IllegalArgumentException("Unknown protocol code: " + code);
        };
    }

    public abstract Code code();

    public List<String> params() {
        return params;
    }

    public String param(int index) {
        return params.get(index);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(code().value());
        for (String param : params) {
            sb.append("|").append(param);
        }
        return sb.toString();
    }

    public String generarTrama() {
        return serialize();
    }

    private static ProtocolMessage fromParts(Code code, List<String> parts) {
        validateParamCount(code, parts);
        return switch (code) {
            case REQUEST -> new RequestMessage(parts.get(0), parts.get(1));
            case ACCEPT -> new AcceptMessage(parts.get(0), parts.get(1));
            case REJECT -> new RejectMessage();
            case HELLO_BROADCAST -> new HelloBroadcastMessage(parts.get(0));
            case HELLO_ACCEPT -> new HelloAcceptMessage(parts.get(0));
            case HELLO_REJECT -> new HelloRejectMessage();
            case CHAT -> parts.size() >= 4
                    ? new ChatMessage(parts.get(0), parts.get(1), parts.get(2), parts.get(3))
                    : new ChatMessage(parts.get(0), parts.get(1), parts.get(2));
            case RECEIPT -> new ReceiptMessage(parts.get(0));
            case DELETE -> new DeleteMessage(parts.get(0));
            case BUZZ -> new BuzzMessage(parts.get(0));
            case PIN -> new PinMessage(parts.get(0));
            case SEEN -> new SeenMessage(parts.get(0), parts.get(1), parts.get(2));
            case THEME -> new ThemeMessage(parts.get(0), parts.get(1));
            case OUTLINE -> new RejectMessage();
        };
    }

    private static void validateParamCount(Code code, List<String> parts) {
        int expected = code.paramCount();
        int actual = parts == null ? 0 : parts.size();
        if (code == Code.CHAT && (actual == 2 || actual == 3 || actual == 4)) {
            return;
        }
        if (expected != actual) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Code %s expects %d params, got %d",
                            code.value(), expected, actual));
        }
    }

    private static String[] extractParts(String[] split) {
        String[] parts = new String[Math.max(0, split.length - 1)];
        for (int i = 1; i < split.length; i++) {
            parts[i - 1] = split[i].trim();
        }
        return parts;
    }

    private static ProtocolMessage parseRequest(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid request payload");
        }
        return new RequestMessage(parts[0], parts[1]);
    }

    private static ProtocolMessage parseAccept(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid accept payload");
        }
        return new AcceptMessage(parts[0], parts[1]);
    }

    private static ProtocolMessage parseReject(String[] split) {
        return of(Code.REJECT, extractParts(split));
    }

    private static ProtocolMessage parseHelloBroadcast(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid hello broadcast payload");
        }
        // Interop: some clients include extra fields (e.g. id|name). Keep first field as requester id.
        return new HelloBroadcastMessage(parts[0]);
    }

    private static ProtocolMessage parseHelloAccept(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid hello accept payload");
        }
        // Interop: some clients include extra fields (e.g. id|name). Keep first field as responder id.
        return new HelloAcceptMessage(parts[0]);
    }

    private static ProtocolMessage parseHelloReject(String[] split) {
        return of(Code.HELLO_REJECT, extractParts(split));
    }

    private static ProtocolMessage parseChat(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length >= 4) {
            return new ChatMessage(parts[0], parts[1], parts[2], parts[3]);
        }
        if (parts.length == 3) {
            return new ChatMessage(parts[0], parts[1], parts[2]);
        }
        if (parts.length == 2) {
            // Interop: chat as userId|messageText without messageId
            String syntheticMessageId = parts[0] + "-" + UUID.randomUUID();
            return new ChatMessage(parts[0], syntheticMessageId, parts[1]);
        }
        throw new IllegalArgumentException("Invalid chat payload");
    }

    private static ProtocolMessage parseReceipt(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Invalid receipt payload");
        }
        // Interop: some clients send extra fields (e.g. userId|messageId). We use the last field as messageId.
        String messageId = parts[parts.length - 1];
        return new ReceiptMessage(messageId);
    }

    private static ProtocolMessage parseDelete(String[] split) {
        return of(Code.DELETE, extractParts(split));
    }

    private static ProtocolMessage parseBuzz(String[] split) {
        return of(Code.BUZZ, extractParts(split));
    }

    private static ProtocolMessage parsePin(String[] split) {
        return of(Code.PIN, extractParts(split));
    }

    private static ProtocolMessage parseSeen(String[] split) {
        String[] parts = extractParts(split);
        if (parts.length >= 3) {
            return new SeenMessage(parts[0], parts[1], parts[2]);
        }
        if (parts.length == 2) {
            // Interop: seen as userId|messageId
            return new SeenMessage(parts[0], parts[1], "VISTO");
        }
        if (parts.length == 1) {
            // Interop: seen as only messageId
            return new SeenMessage("", parts[0], "VISTO");
        }
        throw new IllegalArgumentException("Invalid seen payload");
    }

    private static ProtocolMessage parseTheme(String[] split) {
        return of(Code.THEME, extractParts(split));
    }

    public static final class RequestMessage extends ProtocolMessage {
        public RequestMessage(String id, String nombre) {
            super(List.of(id, nombre));
        }

        @Override
        public Code code() {
            return Code.REQUEST;
        }
    }

    public static final class AcceptMessage extends ProtocolMessage {
        public AcceptMessage(String id, String nombre) {
            super(List.of(id, nombre));
        }

        @Override
        public Code code() {
            return Code.ACCEPT;
        }
    }

    public static final class RejectMessage extends ProtocolMessage {
        public RejectMessage() {
            super(List.of());
        }

        @Override
        public Code code() {
            return Code.REJECT;
        }
    }

    public static final class HelloBroadcastMessage extends ProtocolMessage {
        public HelloBroadcastMessage(String id) {
            super(List.of(id));
        }

        @Override
        public Code code() {
            return Code.HELLO_BROADCAST;
        }
    }

    public static final class HelloAcceptMessage extends ProtocolMessage {
        public HelloAcceptMessage(String id) {
            super(List.of(id));
        }

        @Override
        public Code code() {
            return Code.HELLO_ACCEPT;
        }
    }

    public static final class HelloRejectMessage extends ProtocolMessage {
        public HelloRejectMessage() {
            super(List.of());
        }

        @Override
        public Code code() {
            return Code.HELLO_REJECT;
        }
    }

    public static final class ChatMessage extends ProtocolMessage {
        public ChatMessage(String userId, String messageId, String message) {
            super(List.of(userId, messageId, message));
        }

        public ChatMessage(String userId, String messageId, String message, String timestamp) {
            super(List.of(userId, messageId, message, timestamp));
        }

        @Override
        public Code code() {
            return Code.CHAT;
        }
    }

    public static final class ReceiptMessage extends ProtocolMessage {
        public ReceiptMessage(String messageId) {
            super(List.of(messageId));
        }

        @Override
        public Code code() {
            return Code.RECEIPT;
        }
    }

    public static final class DeleteMessage extends ProtocolMessage {
        public DeleteMessage(String messageId) {
            super(List.of(messageId));
        }

        @Override
        public Code code() {
            return Code.DELETE;
        }
    }

    public static final class BuzzMessage extends ProtocolMessage {
        public BuzzMessage(String idVer) {
            super(List.of(idVer));
        }

        @Override
        public Code code() {
            return Code.BUZZ;
        }
    }

    public static final class PinMessage extends ProtocolMessage {
        public PinMessage(String messageId) {
            super(List.of(messageId));
        }

        @Override
        public Code code() {
            return Code.PIN;
        }
    }

    public static final class SeenMessage extends ProtocolMessage {
        public SeenMessage(String userId, String runId, String message) {
            super(List.of(userId, runId, message));
        }

        @Override
        public Code code() {
            return Code.SEEN;
        }
    }

    public static final class ThemeMessage extends ProtocolMessage {
        public ThemeMessage(String userId, String themeId) {
            super(List.of(userId, themeId));
        }

        @Override
        public Code code() {
            return Code.THEME;
        }
    }

    private static ProtocolMessage parseOutLine(String[] split) {
        return of(Code.OUTLINE, extractParts(split));
    }
}
