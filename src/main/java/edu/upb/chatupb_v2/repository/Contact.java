/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package edu.upb.chatupb_v2.repository;

import lombok.*;

import java.io.Serializable;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Contact implements Serializable, Model {
    public static final String ME_CODE = "af3bc20a-766c-4cd4-813d-b1067a01fa9a";


    public static final class Column{
        public static final String ID= "id";
        public static final String CODE ="code";
        public static final String NAME ="name";
        public static final String IP ="ip";

    }
    @Override
    public void setId(long id) {
        this.id = id;
    }
    private long id;
    private String code;
    private String name;
    private String ip;
    private boolean stateConnect = false;
    
    public String roomCode(){
        return ME_CODE + code;
    }
    

}


