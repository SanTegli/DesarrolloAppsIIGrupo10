package ar.edu.uade.reclamos.dominio.modelo;

import ar.edu.uade.reclamos.comun.validacion.Validador;

/** Empleado municipal que atiende los reclamos del área a la que pertenece. */
public class AgenteMunicipal extends Usuario {

    private AreaMunicipal area;

    /** Requerido por JPA. */
    protected AgenteMunicipal() {
    }

    public AgenteMunicipal(String dni, String nombre, String apellido, String email, String telefono,
                           AreaMunicipal area) {
        super(dni, nombre, apellido, email, telefono);
        this.area = Validador.requerirNoNulo(area, "área");
    }

    @Override
    public Rol getRol() {
        return Rol.AGENTE_MUNICIPAL;
    }

    public boolean perteneceA(AreaMunicipal otraArea) {
        return area != null && area.equals(otraArea);
    }

    public void cambiarArea(AreaMunicipal nuevaArea) {
        this.area = Validador.requerirNoNulo(nuevaArea, "área");
    }

    public AreaMunicipal getArea() {
        return area;
    }
}
