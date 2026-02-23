package edu.upb.chatupb_v2.bl.server;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Mediador {
    private static Mediador instance;

    private final HashMap<String, SocketClient> clientes;
    private final BlockingQueue<TransportEvent> transportEvents;

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
}
