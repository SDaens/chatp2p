package edu.upb.chatupb_v2.model.payment;

import edu.upb.chatupb_v2.model.entities.Cobro;

import java.math.BigDecimal;
import java.util.UUID;

public class CobroFiat implements ECobros {

    @Override
    public Cobro cobrar(BigDecimal monto) {
        String qrGenerado = "QR-BOB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Cobro(monto, qrGenerado, null);
    }
}
