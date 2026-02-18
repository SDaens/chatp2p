# Tecnica para desacoplar la interfaz grafica de ChatServer y SocketClient

## Pregunta
**Investigar alguna tecnica para desacoplar la interfaz grafica de ChatServer y SocketClient.**

## Respuesta corta
La tecnica aplicada es **Patron Observador + Inversion de Dependencias**.

- La UI (`ChatUIApp`) ya no usa `Socket`, `ServerSocket`, `BufferedReader` ni `BufferedWriter` directamente.
- La UI depende de una interfaz (`ChatTransport`) y recibe eventos por observador (`ChatTransportListener`).
- La implementacion concreta de red (`SocketChatTransport`) queda aislada en la capa de negocio/red.

---

## 1) Contrato de red (Dependency Inversion)

La UI depende de esta interfaz, no de clases concretas:

```java
public interface ChatTransport {
    void setListener(ChatTransportListener listener);
    void start();
    void stop();
    void connect(String ip);
    void send(ProtocolMessage message) throws IOException;
    boolean isConnected();
}
```

**Idea:** si manana quieres cambiar sockets por WebSocket, solo cambias la implementacion, no la UI.

---

## 2) Observador de eventos de red (Observer)

Se define un listener para notificar eventos asincornos hacia la UI:

```java
public interface ChatTransportListener {
    default void onConnected(String remoteIp, String contextMessage) {}
    default void onDisconnected(String remoteIp, String reason) {}
    default void onMessageReceived(String line) {}
    default void onError(String message, Exception exception) {}
}
```

**Idea:** la capa de red emite eventos y la UI decide como mostrarlos.

---

## 3) Adaptador concreto de sockets

`SocketChatTransport` implementa `ChatTransport` y encapsula todo lo de red:

```java
public class SocketChatTransport implements ChatTransport {
    private volatile ChatTransportListener listener = new ChatTransportListener() {};

    @Override
    public void start() {
        // abre ServerSocket y escucha conexiones
    }

    @Override
    public void connect(String ip) {
        // abre Socket saliente y conecta al peer
    }

    @Override
    public void send(ProtocolMessage message) throws IOException {
        // serializa y envia por socket
    }
}
```

Fragmento clave de notificacion hacia UI:

```java
while (running && (line = reader.readLine()) != null) {
    listener.onMessageReceived(line);
}
```

---

## 4) Uso desde la UI (ya desacoplada)

La UI solo configura listener y consume eventos:

```java
private final ChatTransport transport = new SocketChatTransport(PORT);

private void configureTransport() {
    transport.setListener(new ChatTransportListener() {
        @Override
        public void onConnected(String remoteIp, String contextMessage) {
            addOrSelectContact(remoteIp);
            setConnectionState(remoteIp, contextMessage);
        }

        @Override
        public void onMessageReceived(String line) {
            handleProtocolLine(line);
        }

        @Override
        public void onDisconnected(String remoteIp, String reason) {
            addSystemMessage(reason);
        }

        @Override
        public void onError(String message, Exception exception) {
            addSystemMessage(message);
        }
    });
}
```

Y para enviar:

```java
private void sendProtocol(ProtocolMessage message) {
    if (!transport.isConnected()) {
        addSystemMessage("Sin conexion activa.");
        return;
    }
    transport.send(message);
}
```

---

## 5) Beneficios del desacople

1. **Menor acoplamiento:** UI no depende de sockets directos.
2. **Mayor mantenibilidad:** cambios de red no rompen la UI.
3. **Mejor testeo:** puedes simular `ChatTransport` sin abrir puertos reales.
4. **Arquitectura mas clara:** presentacion separada de comunicacion.

---

## Concluson
La tecnica recomendada para este proyecto es usar un **transporte abstracto + listener observador**.
Con esto se desacopla la interfaz grafica de `ChatServer`/`SocketClient` y se obtiene un diseno mas limpio, escalable y facil de mantener.
