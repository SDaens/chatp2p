package edu.upb.chatupb_v2.model.payment;

import edu.upb.chatupb_v2.model.entities.Cobro;

import java.math.BigDecimal;

public interface ECobros {
    Cobro cobrar(BigDecimal monto);
}
