# Explicación de desacoplamiento UI-Red (versión limpia)

## Objetivo
Separar la interfaz gráfica (`ChatUIApp`) de la lógica de sockets para que el código sea más mantenible y fácil de probar.

## Qué se dejó en el proyecto
Se dejó una sola arquitectura de red:
- `ChatTransport` (interfaz): contrato de red para la UI.
- `ChatTransportListener` (observador): eventos asíncronos.
- `SocketChatTransport` (implementación real): maneja `ServerSocket`, `Socket` e hilos.
- `ProtocolMessage` (modelo de protocolo): parseo/serialización de códigos `001-013`.

La UI (`ChatUIApp`) solo conoce el contrato `ChatTransport`.
No manipula sockets directamente.

## Patrón aplicado
- **Observer**: `ChatTransportListener` notifica conexión, desconexión, mensajes y errores.
- **Dependency Inversion**: la UI depende de interfaz (`ChatTransport`), no de una implementación concreta.
- **Adapter**: `SocketChatTransport` adapta sockets a una API simple para la UI.

## Flujo resumido
1. `ChatUIApp` crea `SocketChatTransport(PORT)`.
2. Configura listener con callbacks (`onConnected`, `onDisconnected`, `onMessageReceived`, `onError`).
3. Llama `transport.start()` para abrir listener local.
4. Para conectar a otro peer, llama `transport.connect(ip)`.
5. Mensajes entrantes llegan como líneas y se parsean con `ProtocolMessage.parse(...)`.
6. Mensajes salientes se envían con `transport.send(...)` usando `message.serialize()`.

## Beneficio práctico
- Código más ordenado para nivel de 5to semestre.
- Menos acoplamiento entre UI y red.
- Más fácil reemplazar transporte o crear pruebas sin sockets reales.

## Nota sobre base de datos
`ConnectionDB.java` no se modifica en este ajuste.
