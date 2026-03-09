package edu.upb.chatupb_v2.controller;

import edu.upb.chatupb_v2.model.entities.Cobro;
import edu.upb.chatupb_v2.model.payment.CobroCripto;
import edu.upb.chatupb_v2.model.payment.CobroFiat;
import edu.upb.chatupb_v2.model.payment.ECobros;

import java.math.BigDecimal;

public class    CobroController {

    private static final BigDecimal MONTO_BASE_BOB = BigDecimal.TEN;

    private final ECobros cobroFiat = new CobroFiat();
    private final ECobros cobroCripto = new CobroCripto("Bitcoin");

    public BigDecimal getMontoBaseBob() {
        return MONTO_BASE_BOB;
    }

    public Cobro cobroFiat(BigDecimal monto) {
        return Cobro.cobro(cobroFiat, monto);
    }

    public Cobro cobroCripto(BigDecimal monto) {
        return Cobro.cobro(cobroCripto, monto);
    }
}
