package com.uade.logistica.api_envios.dominio;

import com.uade.logistica.api_envios.utilidad.ValidadorReclamo;

public class Reclamo {
    private final String id;
    private final String dniCiudadano;
    private final String direccion;
    private final String descripcion;
    private final String categoria;
    private String prioridad;
    private String areaMunicipal;
    private String estado;

    public Reclamo(String id, String dniCiudadano, String direccion, String descripcion, String categoria) {
        ValidadorReclamo.requerirTextoNoVacio(id, "ID");
        ValidadorReclamo.requerirDniValido(dniCiudadano);
        ValidadorReclamo.requerirTextoNoVacio(direccion, "dirección");
        ValidadorReclamo.requerirTextoNoVacio(descripcion, "descripción");

        this.id = id;
        this.dniCiudadano = dniCiudadano;
        this.direccion = direccion;
        this.descripcion = descripcion;
        this.categoria = categoria != null ? categoria.toUpperCase() : "GENERAL";
        this.estado = "NUEVO";
    }

    public void actualizarEstado(String nuevoEstado) {
        ValidadorReclamo.requerirTextoNoVacio(nuevoEstado, "estado");
        this.estado = nuevoEstado;
    }

    public String getId() { return id; }
    public String getDniCiudadano() { return dniCiudadano; }
    public String getDireccion() { return direccion; }
    public String getDescripcion() { return descripcion; }
    public String getCategoria() { return categoria; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getAreaMunicipal() { return areaMunicipal; }
    public void setAreaMunicipal(String areaMunicipal) { this.areaMunicipal = areaMunicipal; }
    public String getEstado() { return estado; }
}