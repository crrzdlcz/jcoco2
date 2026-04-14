package coco;

import java.time.Duration;
import java.time.Instant;

public class Contador {
    
    private Instant inicio;
    private Instant fin;
    private boolean estaCorriendo;

    public Contador() {
        this.estaCorriendo = false;
    }

    // Inicia el conteo
    public void iniciar() {
        this.inicio = Instant.now();
        this.estaCorriendo = true;
    }

    // Detiene el conteo
    public void detener() {
        if (estaCorriendo) {
            this.fin = Instant.now();
            this.estaCorriendo = false;
        }
    }

    // Regresa la duración total en milisegundos
    public long getMilisegundos() {
        return Duration.between(inicio, fin).toMillis();
    }

    // Te da un String formateado bonito (ej: "1.235 segundos" o "450 milisegundos")
    public String getTiempoFormateado() {
        Duration duracion = Duration.between(inicio, fin);
        long millis = duracion.toMillis();
        
        if (millis >= 1000) {
            double segundos = millis / 1000.0;
            return String.format("%.3f segundos", segundos);
        } else {
            return millis + " milisegundos";
        }
    }
}