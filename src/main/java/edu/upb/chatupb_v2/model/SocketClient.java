package edu.upb.chatupb_v2.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SocketClient {
    private static final String SECURITY_NEGOTIATION_CODE = "014";
    private static final String SECURITY_SELECTED_CODE = "015";
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String HANDSHAKE_XOR_MASK = "chatupb-secure-handshake-v1";
    private static final String SUPPORTED_PROTOCOLS_PAYLOAD = "AES_128,AES_256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Socket socket;
    private final boolean initiator;
    private final String connectedDetail;
    private volatile SocketListener listener = new SocketListener() {
        @Override
        public void onConnected(SocketClient socketClient, String remoteIp, String detail) {
        }

        @Override
        public void onMessage(SocketClient socketClient, String remoteIp, String line) {
        }

        @Override
        public void onDisconnected(SocketClient socketClient, String remoteIp, String reason) {
        }

        @Override
        public void onError(SocketClient socketClient, String detail, Exception exception) {
        }
    };
    private volatile Thread readerThread;
    private final AtomicBoolean disconnectedNotified = new AtomicBoolean(false);
    private final AtomicBoolean connectedNotified = new AtomicBoolean(false);
    private volatile boolean listening;
    private volatile boolean securityReady;
    private volatile SecurityContext securityContext;
    private volatile String remoteIp;

    public SocketClient(Socket socket, boolean initiator, String connectedDetail) {
        this.socket = socket;
        this.initiator = initiator;
        this.connectedDetail = connectedDetail;
        this.remoteIp = socket != null && socket.getInetAddress() != null
                ? socket.getInetAddress().getHostAddress()
                : null;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setListener(SocketListener listener) {
        if (listener == null) {
            return;
        }
        this.listener = listener;
    }

    public void startListening() throws IOException {
        if (socket == null) {
            throw new IOException("Socket no inicializado");
        }
        if (listening) {
            return;
        }
        listening = true;
        securityReady = false;
        securityContext = null;
        connectedNotified.set(false);
        disconnectedNotified.set(false);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        readerThread = new Thread(() -> listenLoop(reader), "socket-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        if (initiator) {
            sendRaw(SECURITY_NEGOTIATION_CODE + "|" + SUPPORTED_PROTOCOLS_PAYLOAD);
        }
    }

    private void listenLoop(BufferedReader reader) {
        try {
            String line;
            while (listening && (line = reader.readLine()) != null) {
                if (!securityReady) {
                    processSecurityHandshakeLine(line);
                    continue;
                }
                String clearLine = decryptMessage(line);
                if (clearLine == null) {
                    listener.onError(this, "No se pudo desencriptar trama entrante.", null);
                    close();
                    return;
                }
                listener.onMessage(this, remoteIp, clearLine);
            }
        } catch (IOException ex) {
            if (listening) {
                listener.onError(this, "Conexión cerrada: " + ex.getMessage(), ex);
            }
        } finally {
            listening = false;
            notifyDisconnectedOnce("Conexión finalizada");
            try {
                reader.close();
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void send(ProtocolMessage message) throws IOException {
        if (message == null) {
            return;
        }
        if (!securityReady) {
            throw new IOException("Canal seguro no negociado todavía.");
        }
        String encrypted = encryptMessage(message.serialize());
        if (encrypted == null) {
            throw new IOException("No se pudo encriptar el mensaje.");
        }
        sendRaw(encrypted);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void close() {
        listening = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            notifyDisconnectedOnce("Conexión finalizada");
        }
    }

    private void notifyDisconnectedOnce(String reason) {
        if (disconnectedNotified.compareAndSet(false, true)) {
            listener.onDisconnected(this, remoteIp, reason);
        }
    }

    private void processSecurityHandshakeLine(String line) throws IOException {
        String cleanLine = line == null ? "" : line.trim();
        if (cleanLine.isEmpty()) {
            return;
        }
        if (cleanLine.startsWith(SECURITY_NEGOTIATION_CODE + "|")) {
            handleSecurityNegotiationRequest(cleanLine);
            return;
        }
        if (cleanLine.startsWith(SECURITY_SELECTED_CODE + "|")) {
            handleSecuritySelection(cleanLine);
            return;
        }
        listener.onError(this, "Handshake de seguridad inválido o no soportado.", null);
        close();
    }

    private void handleSecurityNegotiationRequest(String line) throws IOException {
        if (initiator) {
            return;
        }
        String[] parts = line.split("\\|", 2);
        if (parts.length < 2) {
            return;
        }
        List<SecurityProtocol> commonProtocols = resolveCommonProtocols(parts[1]);
        if (commonProtocols.isEmpty()) {
            return;
        }
        SecurityProtocol selected = commonProtocols.get(SECURE_RANDOM.nextInt(commonProtocols.size()));
        SecurityContext selectedContext = SecurityContext.create(selected);
        String rawKey = Base64.getEncoder().encodeToString(selectedContext.keyMaterial());
        String payload = SECURITY_SELECTED_CODE + "|" + selected.id() + "|" + encodeHandshakeKey(rawKey);
        sendRaw(payload);
        completeHandshake(selectedContext);
    }

        private void handleSecuritySelection(String line) {
            if (!initiator) {
                return;
            }
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) {
                return;
            }
            SecurityProtocol protocol = SecurityProtocol.from(parts[1]);
            if (protocol == null) {
                return;
            }
            String decodedKey = decodeHandshakeKey(parts[2]);
            if (decodedKey == null || decodedKey.isBlank()) {
                return;
            }
            try {
                byte[] keyMaterial = Base64.getDecoder().decode(decodedKey);
                SecurityContext context = SecurityContext.fromSharedKey(protocol, keyMaterial);
                completeHandshake(context);
            } catch (IllegalArgumentException ex) {
                listener.onError(this, "No se pudo decodificar la llave negociada.", ex);
                close();
            }
        }

    private void completeHandshake(SecurityContext context) {
        if (context == null || connectedNotified.get()) {
            return;
        }
        securityContext = context;
        securityReady = true;
        if (connectedNotified.compareAndSet(false, true)) {
            listener.onConnected(this, remoteIp, connectedDetail);
        }
    }

    private List<SecurityProtocol> resolveCommonProtocols(String rawProtocols) {
        if (rawProtocols == null || rawProtocols.isBlank()) {
            return Collections.emptyList();
        }
        String[] received = rawProtocols.split(",");
        List<SecurityProtocol> common = new ArrayList<>();
        for (String item : received) {
            SecurityProtocol protocol = SecurityProtocol.from(item);
            if (protocol != null) {
                common.add(protocol);
            }
        }
        return common;
    }

    private String encryptMessage(String clearText) {
        SecurityContext context = securityContext;
        if (context == null) {
            return null;
        }
        try {
            byte[] iv = new byte[16];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, context.secretKeySpec(), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(clearText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            return null;
        }
    }

    private String decryptMessage(String encryptedText) {
        SecurityContext context = securityContext;
        if (context == null) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= 16) {
                return null;
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(payload, 16, payload.length);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, context.secretKeySpec(), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String encodeHandshakeKey(String clearText) {
        byte[] source = clearText.getBytes(StandardCharsets.UTF_8);
        byte[] mask = HANDSHAKE_XOR_MASK.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            encoded[i] = (byte) (source[i] ^ mask[i % mask.length]);
        }
        return Base64.getEncoder().encodeToString(encoded);
    }

    private String decodeHandshakeKey(String encodedText) {
        try {
            byte[] source = Base64.getDecoder().decode(encodedText);
            byte[] mask = HANDSHAKE_XOR_MASK.getBytes(StandardCharsets.UTF_8);
            byte[] decoded = new byte[source.length];
            for (int i = 0; i < source.length; i++) {
                decoded[i] = (byte) (source[i] ^ mask[i % mask.length]);
            }
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private synchronized void sendRaw(String payload) throws IOException {
        String frame = payload + System.lineSeparator();
        byte[] bytes = frame.getBytes(StandardCharsets.UTF_8);
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
    }

    private enum SecurityProtocol {
        AES128("AES_128", 16),
        AES256("AES_256", 32);

        private final String id;
        private final int materialLength;

        SecurityProtocol(String id, int materialLength) {
            this.id = id;
            this.materialLength = materialLength;
        }

        public String id() {
            return id;
        }

        public int materialLength() {
            return materialLength;
        }

        public static SecurityProtocol from(String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (SecurityProtocol protocol : values()) {
                if (protocol.id.equals(normalized)) {
                    return protocol;
                }
            }
            return null;
        }
    }

    private record SecurityContext(SecurityProtocol protocol, byte[] keyMaterial, SecretKeySpec secretKeySpec) {
        static SecurityContext create(SecurityProtocol protocol) {
            byte[] material = new byte[protocol.materialLength()];
            SECURE_RANDOM.nextBytes(material);
            return fromSharedKey(protocol, material);
        }

        static SecurityContext fromSharedKey(SecurityProtocol protocol, byte[] keyMaterial) {
            if (protocol == null || keyMaterial == null || keyMaterial.length == 0) {
                throw new IllegalArgumentException("Parámetros de seguridad inválidos.");
            }
            byte[] aesKey = deriveAesKey(protocol, keyMaterial);
            return new SecurityContext(protocol, Arrays.copyOf(keyMaterial, keyMaterial.length), new SecretKeySpec(aesKey, "AES"));
        }

        private static byte[] deriveAesKey(SecurityProtocol protocol, byte[] source) {
            byte[] key = new byte[protocol == SecurityProtocol.AES128 ? 16 : 32];
            System.arraycopy(source, 0, key, 0, Math.min(source.length, key.length));
            return key;
        }
    }
}
