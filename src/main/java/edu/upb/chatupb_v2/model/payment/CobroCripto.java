package edu.upb.chatupb_v2.model.payment;

import edu.upb.chatupb_v2.model.entities.Cobro;

import java.math.BigDecimal;

public class CobroCripto implements ECobros {

    private final String blockchain;

    public CobroCripto(String blockchain) {
        this.blockchain = blockchain == null || blockchain.isBlank() ? "Bitcoin" : blockchain;
    }

    @Override
    public Cobro cobrar(BigDecimal monto) {
        return new Cobro(monto, null, blockchain);
    }
}
