/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package edu.upb.chatupb_v2;

import edu.upb.chatupb_v2.bl.server.ChatServer;
import edu.upb.chatupb_v2.bl.server.SocketClient;
import edu.upb.chatupb_v2.repository.Contact;
import edu.upb.chatupb_v2.repository.ContactDao;

import java.util.List;

/**
 * @author rlaredo
 */
public class ChatUPB_V2 {

    public static void main(String[] args) {
        try {
            ChatServer chatServer = new ChatServer();
            chatServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("ChatServer started..");

        try {
            SocketClient socketClient = new SocketClient("localhost");
            socketClient.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
