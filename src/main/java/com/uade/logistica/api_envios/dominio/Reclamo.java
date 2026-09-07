package com.uade.logistica.api_envios.dominio;

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
        if (dniCiudadano == null || !dniCiudadano.matches("\\d{7,8}")) {
            throw new IllegalArgumentException("DNI de ciudadano inválido");
        }
        if (direccion == null || direccion.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección no puede estar vacía");
        }
        if (descripcion == null || descripcion.trim().isEmpty()) {
            throw new IllegalArgumentException("La descripción es obligatoria");
        }

        this.id = id;
        this.dniCiudadano = dniCiudadano;
        this.direccion = direccion;
        this.descripcion = descripcion;
        this.categoria = categoria != null ? categoria.toUpperCase() : "GENERAL";
        this.estado = "NUEVO";
    }

    public void actualizarEstado(String nuevoEstado) {
        if (nuevoEstado == null || nuevoEstado.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado no puede ser nulo");
        }
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