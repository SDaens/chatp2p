/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upb.chatupb_v2.bl.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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
                DataOutputStream dout = new DataOutputStream(socketClient.getOutputStream());
                String message = "Buenas!!!. "+System.lineSeparator();
                dout.write(message.getBytes("UTF-8") );
                dout.flush();
                //BufferedReader br = new BufferedReader(new InputStreamReader(socketClient.getInputStream()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
