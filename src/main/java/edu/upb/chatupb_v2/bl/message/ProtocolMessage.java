package edu.upb.chatupb_v2.bl.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Wire-level protocol message for the 001-013 chat actions defined in diagram.puml.
 * Single-class representation with parsing/serialization helpers.
 */
public final class ProtocolMessage {

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
        THEME("013", 2, "ID_user", "ID_tema");

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

    private final Code code;
    private final List<String> params;

    private ProtocolMessage(Code code, List<String> params) {
        this.code = Objects.requireNonNull(code, "code");
        this.params = Collections.unmodifiableList(new ArrayList<>(params));
    }

    public static ProtocolMessage of(Code code, String... params) {
        Objects.requireNonNull(code, "code");
        int expected = code.paramCount();
        int actual = params == null ? 0 : params.length;
        if (expected != actual) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Code %s expects %d params, got %d",
                            code.value(), expected, actual));
        }
        List<String> parts = new ArrayList<>();
        if (params != null) {
            Collections.addAll(parts, params);
        }
        return new ProtocolMessage(code, parts);
    }

    public static ProtocolMessage parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty protocol line");
        }
        String[] rawTokens = line.split("\\|");
        if (rawTokens.length == 0) {
            throw new IllegalArgumentException("Invalid protocol line: " + line);
        }
        String codeToken = rawTokens[0].trim();
        Code code = Code.from(codeToken);
        List<String> parts = new ArrayList<>();
        for (int i = 1; i < rawTokens.length; i++) {
            parts.add(rawTokens[i].trim());
        }
        if (parts.size() != code.paramCount()) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "Code %s expects %d params, got %d",
                            code.value(), code.paramCount(), parts.size()));
        }
        return new ProtocolMessage(code, parts);
    }

    public Code code() {
        return code;
    }

    public List<String> params() {
        return params;
    }

    public String param(int index) {
        return params.get(index);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder(code.value());
        for (String param : params) {
            sb.append(" | ").append(param);
        }
        return sb.toString();
    }
}
