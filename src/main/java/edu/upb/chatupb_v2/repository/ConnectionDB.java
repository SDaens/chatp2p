/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upb.chatupb_v2.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 * @author rlaredo
 */
public class ConnectionDB {
 
    private static final ConnectionDB connection = new ConnectionDB();
    
    private ConnectionDB(){
       
    }
    
    public static ConnectionDB getInstance(){
        return connection;
    }

    
    public Connection getConection(){
        Connection conn = null;
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:chat_upb.sqlite");
            if (conn != null) {
                System.out.println("Conexión exitosa.");
            } else {
                System.out.println("Conexión fallida");
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }catch(ClassNotFoundException e){
        
        }
        return conn;   
    }
}
