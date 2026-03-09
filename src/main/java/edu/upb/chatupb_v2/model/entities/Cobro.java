package edu.upb.chatupb_v2.model.entities;

import edu.upb.chatupb_v2.model.payment.ECobros;

import java.math.BigDecimal;

public class Cobro {

    private final BigDecimal monto;
    private final String qr;
    private final String red;

    public Cobro(BigDecimal monto, String qr, String red) {
        this.monto = monto;
        this.qr = qr;
        this.red = red;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public String getQr() {
        return qr;
    }

    public String getRed() {
        return red;
    }

    public static Cobro cobro(ECobros metodoCobro, BigDecimal monto) {
        if (metodoCobro == null) {
            throw new IllegalArgumentException("El metodo de cobro no puede ser nulo");
        }
        return metodoCobro.cobrar(monto);
    }
}
