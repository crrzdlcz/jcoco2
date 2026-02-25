package coco;

import static java.lang.System.err;
import java.util.HashMap;
import java.util.Stack;
import java.io.PrintStream;

public class Tabla {
    private Stack<HashMap<String, Simbolo>> pilaDeTablas;
    private Stack<String> pilaDeNombres;

    public Tabla() {
        pilaDeTablas = new Stack<>();
        pilaDeNombres = new Stack<>();
        entrarAmbito("global");
    }

    public String getAmbitoActual() {
        return pilaDeNombres.isEmpty() ? "global" : pilaDeNombres.peek();
    }

    public void entrarAmbito(String nombreAmbito) {
        pilaDeTablas.push(new HashMap<>());
        pilaDeNombres.push(nombreAmbito);
    }

    public void salirAmbito() {
        if (pilaDeTablas.size() > 1) {
            pilaDeTablas.pop();
            pilaDeNombres.pop();
        }
    }

    public boolean insertar(Simbolo simbolo) {
        HashMap<String, Simbolo> tablaActual = pilaDeTablas.peek();
        
        if (tablaActual.containsKey(simbolo.nombre)) {
            err.println("Linea " + simbolo.lineaDeclaracion + ": El identificador " 
                        + simbolo.nombre + " ya ha sido declarado en este ambito.");
            return false;
        }
        
        tablaActual.put(simbolo.nombre, simbolo);
        return true;
    }

    public Simbolo buscar(String nombre) {
        for (int i = pilaDeTablas.size() - 1; i >= 0; i--) {
            HashMap<String, Simbolo> tabla = pilaDeTablas.get(i);
            if (tabla.containsKey(nombre)) {
                return tabla.get(nombre);
            }
        }
        return null;
    }

    public void imprimirTabla(PrintStream destino) {
        destino.println("--- TABLA DE SIMBOLOS ----------------------------------");

        for (int i = 0; i < pilaDeTablas.size(); i++) {
            HashMap<String, Simbolo> tabla = pilaDeTablas.get(i);
            String nombreAmbito = pilaDeNombres.get(i);

            destino.println("\n== " + nombreAmbito + " (Registros: " + tabla.size() + ") ==");
            destino.printf("%-15s | %-10s | %-15s | %s\n", "NOMBRE", "TIPO", "ÁMBITO", "LINEA");
            destino.println("-----------------------------------------------------");

            for (Simbolo s : tabla.values()) {
                destino.printf("%-15s | %-10s | %-15s | %d\n", s.nombre, s.tipo, s.ambito, s.lineaDeclaracion);
            }
        }
        destino.println("\n------------------------------------ FIN TABLA ------");
    }

    private static Tabla instancia = new Tabla();

    public static Tabla getTabla() {
        return instancia;
    }
}