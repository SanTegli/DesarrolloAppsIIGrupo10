package com.uade.logistica.api_envios.dominio;

public class ReclamoEvento {
    private final String reclamoId;
    private final String tipoEvento;
    private final String detalle;

    public ReclamoEvento(String reclamoId, String tipoEvento, String detalle) {
        this.reclamoId = reclamoId;
        this.tipoEvento = tipoEvento;
        this.detalle = detalle;
    }

    public String getReclamoId() { return reclamoId; }
    public String getTipoEvento() { return tipoEvento; }
    public String getDetalle() { return detalle; }
}