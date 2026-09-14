# Desarrollo de Aplicaciones II - TP Primera Parte

Docente: Mg. Christian Parkinson  
Tema: Arquitectura de Aplicaciones e Integracion  

Este repositorio contiene la implementacion del Trabajo Practico Primera Parte para la materia Desarrollo de Aplicaciones II. La documentacion formal con la explicacion de los patrones de diseño (Factory, Repository, Strategy, Observer y Facade), la arquitectura en capas y los diagramas de secuencia y procesal se encuentra disponible en el archivo ENTREGA_TP_PRIMERA_PARTE.md en la raiz del proyecto.

## Ejecucion del Proyecto

Para correr la aplicacion o los tests automatizados, ingresar a esta carpeta en la terminal:

```powershell
cd api-envios
```

Para ejecutar la suite de pruebas unitarias e integracion:
```powershell
.\mvnw.cmd test
```

Para levantar la aplicacion en el puerto 8080:
```powershell
.\mvnw.cmd spring-boot:run
```
