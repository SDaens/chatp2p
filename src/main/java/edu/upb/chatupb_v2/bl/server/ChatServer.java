/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upb.chatupb_v2.bl.server;

import edu.upb.chatupb_v2.bl.message.ProtocolMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author rlaredo
 */
public class ChatServer extends Thread {

    private static final int port = 1900;

    private final ServerSocket server;
    public ChatServer() throws IOException {
        this.server = new ServerSocket(port);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket socketClient = this.server.accept();
                new Thread(() -> handleClient(socketClient),
                        "chat-client-" + socketClient.getInetAddress().getHostAddress()).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket socketClient) {
        try (
                Socket ignored = socketClient;
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        socketClient.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                        socketClient.getOutputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                ProtocolMessage request;
                try {
                    request = ProtocolMessage.parse(line);
                } catch (IllegalArgumentException ex) {
                    write(bw, new ProtocolMessage.RejectMessage());
                    continue;
                }

                ProtocolMessage response = switch (request.code()) {
                    case REQUEST -> ProtocolMessage.of(
                            ProtocolMessage.Code.ACCEPT, request.param(0), request.param(1));
                    case HELLO_BROADCAST -> ProtocolMessage.of(
                            ProtocolMessage.Code.HELLO_ACCEPT, request.param(0));
                    case CHAT -> ProtocolMessage.of(
                            ProtocolMessage.Code.RECEIPT, request.param(1));
                    case DELETE, BUZZ, PIN, SEEN, THEME, RECEIPT -> request;
                    case ACCEPT, REJECT, HELLO_ACCEPT, HELLO_REJECT -> request;
                };

                write(bw, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void write(BufferedWriter bw, ProtocolMessage message) throws IOException {
        bw.write(message.serialize());
        bw.write(System.lineSeparator());
        bw.flush();
    }
}
