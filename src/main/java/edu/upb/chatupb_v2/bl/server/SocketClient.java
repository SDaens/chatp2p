/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upb.chatupb_v2.bl.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;

/**
 * @author rlaredo
 */
public class SocketClient extends Thread {
    private final Socket socket;
    private final String ip;
    private final String localId;
    private final BufferedWriter bw;
    private final BufferedReader br;

    public SocketClient(Socket socket) throws IOException {
        this.socket = socket;
        this.ip = socket.getInetAddress().getHostAddress();
        this.localId = UUID.randomUUID().toString();
        this.bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.br = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public SocketClient(String ip) throws IOException {
        this.socket = new Socket(ip, 1900);
        this.ip = ip;
        this.localId = UUID.randomUUID().toString();
        this.bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.br = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            send(ProtocolMessage.of(ProtocolMessage.Code.REQUEST, localId, "ChatUPB-" + localId.substring(0, 8)));
            send(ProtocolMessage.of(ProtocolMessage.Code.HELLO_BROADCAST, localId));
            send(ProtocolMessage.of(ProtocolMessage.Code.CHAT, localId, UUID.randomUUID().toString(), "Hola desde " + ip));

            String message;
            while ((message = br.readLine()) != null) {
                try {
                    ProtocolMessage protocolMessage = ProtocolMessage.parse(message);
                    System.out.println("RX [" + protocolMessage.code().value() + "] " + protocolMessage.serialize());
                } catch (IllegalArgumentException ex) {
                    System.out.println("RX [UNKNOWN] " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void send(ProtocolMessage message) throws IOException {
        try {
            bw.write(message.serialize());
            bw.write(System.lineSeparator());
            bw.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            this.socket.close();
            this.br.close();
            this.bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
