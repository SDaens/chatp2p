package edu.upb.chatupb_v2.model;

import edu.upb.chatupb_v2.model.ProtocolMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

public class Mediador implements SocketListener {
    private static Mediador instance;
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\."
                    + "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\."
                    + "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\."
                    + "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    );

    private final HashMap<String, SocketClient> clientes;
    private final BlockingQueue<TransportEvent> transportEvents;
    private volatile ChatTransportListener transportListener = new ChatTransportListener() {
    };

    private Mediador() {
        this.clientes = new HashMap<>();
        this.transportEvents = new LinkedBlockingQueue<>();
    }


    public static synchronized Mediador getInstance() {
        if (instance == null) {
            instance = new Mediador();
        }
        return instance;
    }

    public void setTransportListener(ChatTransportListener listener) {
        if (listener == null) {
            this.transportListener = new ChatTransportListener() {
            };
            return;
        }
        this.transportListener = listener;
    }

    public enum EventType {
        CONNECTED,
        DISCONNECTED,
        MESSAGE,
        ERROR
    }

    public record TransportEvent(EventType type, String remoteIp, String payload, String detail) {
    }

    public void publishConnected(String remoteIp, String detail) {
        transportEvents.offer(new TransportEvent(EventType.CONNECTED, remoteIp, null, detail));
    }

    public void publishDisconnected(String remoteIp, String detail) {
        transportEvents.offer(new TransportEvent(EventType.DISCONNECTED, remoteIp, null, detail));
    }

    public void publishIncomingMessage(String remoteIp, String line) {
        transportEvents.offer(new TransportEvent(EventType.MESSAGE, remoteIp, line, null));
    }

    public void publishError(String detail) {
        transportEvents.offer(new TransportEvent(EventType.ERROR, null, null, detail));
    }

    public void publishError(String detail, Exception exception) {
        if (exception == null) {
            publishError(detail);
            return;
        }
        publishError(detail + " (" + exception.getClass().getSimpleName() + ")");
    }

    public List<TransportEvent> drainTransportEvents() {
        List<TransportEvent> drained = new ArrayList<>();
        transportEvents.drainTo(drained);
        return drained;
    }


    public synchronized void registrarCliente(String id, SocketClient socketClient) {
        if (id == null || id.isBlank() || socketClient == null) {
            return;
        }
        clientes.put(id, socketClient);
    }

    public synchronized void addClient(String idUsuario, SocketClient socketClient) {
        registrarCliente(idUsuario, socketClient);
    }

    public synchronized SocketClient obtenerCliente(String id) {
        return clientes.get(id);
    }

    public synchronized SocketClient eliminarCliente(String id) {
        return clientes.remove(id);
    }

    public synchronized SocketClient removeClient(String idUsuario) {
        return eliminarCliente(idUsuario);
    }

    public synchronized boolean contieneCliente(String id) {
        return clientes.containsKey(id);
    }

    public synchronized Map<String, SocketClient> obtenerClientes() {
        return Collections.unmodifiableMap(new HashMap<>(clientes));
    }

    public void sendMessage(String idUsuario, ProtocolMessage message) throws IOException {
        SocketClient socketClient;
        synchronized (this) {
            socketClient = clientes.get(idUsuario);
        }
        if (socketClient == null) {
            throw new IOException("No existe cliente para el id: " + idUsuario);
        }
        socketClient.send(message);
    }

    public boolean enviarInvitacion(String idUsuario, String localUserId, String localNombre) {
        try {
            validarInvitacion(idUsuario, localUserId, localNombre);
            ProtocolMessage invitacion = ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localUserId, localNombre);
            sendMessage(idUsuario, invitacion);
            return true;
        } catch (InvitationException exception) {
            publishError("No se pudo enviar la invitacion: " + exception.getMessage(), exception);
            return false;
        } catch (IOException exception) {
            publishError("Error de IO al enviar la invitacion", exception);
            return false;
        }
    }

    private void validarInvitacion(String idUsuario, String localUserId, String localNombre) throws InvitationException {
        if (idUsuario == null || idUsuario.isBlank()) {
            throw new InvalidInvitationDataException("El id del usuario remoto es obligatorio.");
        }
        if (!esIpValida(idUsuario)) {
            throw new InvalidIpException("La IP remota no tiene un formato IPv4 valido: " + idUsuario);
        }
        if (localUserId == null || localUserId.isBlank()) {
            throw new InvalidInvitationDataException("El id local es obligatorio.");
        }
        if (localNombre == null || localNombre.isBlank()) {
            throw new InvalidInvitationDataException("El nombre local es obligatorio.");
        }
        if (!contieneCliente(idUsuario)) {
            throw new ClienteNoDisponibleException("No existe cliente conectado para el id: " + idUsuario);
        }
    }

    private boolean esIpValida(String ip) {
        return ip != null && IPV4_PATTERN.matcher(ip.trim()).matches();
    }

    public ProtocolMessage onMessage(SocketClient socketClient, ProtocolMessage message,
                                     boolean aceptarInvitacion, String localUserId, String localNombre) {
        if (message == null || socketClient == null) {
            return null;
        }

        if (message.code() != ProtocolMessage.Code.REQUEST) {
            return null;
        }

        String remoteUserId = message.param(0);
        if (aceptarInvitacion) {
            addClient(remoteUserId, socketClient);
            return ProtocolMessage.of(ProtocolMessage.Code.ACCEPT, localUserId, localNombre);
        }
        return ProtocolMessage.of(ProtocolMessage.Code.REJECT);
    }

    @Override
    public void onConnected(SocketClient socketClient, String remoteIp, String detail) {
        registrarCliente(remoteIp, socketClient);
        publishConnected(remoteIp, detail);
        transportListener.onConnected(remoteIp, detail);
    }

    @Override
    public void onMessage(SocketClient socketClient, String remoteIp, String line) {
        publishIncomingMessage(remoteIp, line);
        transportListener.onMessageReceived(line);
    }

    @Override
    public void onDisconnected(SocketClient socketClient, String remoteIp, String reason) {
        eliminarCliente(remoteIp);
        publishDisconnected(remoteIp, reason);
        transportListener.onDisconnected(remoteIp, reason);
    }

    @Override
    public void onError(SocketClient socketClient, String detail, Exception exception) {
        publishError(detail, exception);
        transportListener.onError(detail, exception);
    }
}
