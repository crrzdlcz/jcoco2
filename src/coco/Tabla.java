package coco;

import static java.lang.System.err;

import java.util.HashMap;
import java.util.Stack;

public class Tabla {
	private Stack<HashMap<String, Simbolo>> pilaDeTablas;
	private String ambitoActual;

	// Metodo constructor; comienza con ambito = 0 (global);
	public Tabla() {
		pilaDeTablas = new Stack<>();
		entrarAmbito("global");
	}

	// Obtenemos el ambito actual (nombre);
	public String getAmbitoActual() {
		return this.ambitoActual;
	}

	public void entrarAmbito(String nombreAmbito) {
		this.ambitoActual = nombreAmbito;
		pilaDeTablas.push(new HashMap<>());
		// Comentado por el momento.
		// out.println(">>> Entrando a un nuevo ambito: " + nombreAmbito);
	}

	public void salirAmbito() {
		if (!pilaDeTablas.isEmpty()) {
			String nombreAmbitoSaliente = this.ambitoActual;

			pilaDeTablas.pop();

			if (!pilaDeTablas.isEmpty()) {
			} else {
				this.ambitoActual = "global"; // Vuelve al ámbito inicial
			}

			// Comentado por el momento
			// out.println("<<< Saliendo del ámbito: " + nombreAmbitoSaliente);
		}
	}

	// Para insertar un nuevo simbolo en el ambito actual.
	public boolean insertar(Simbolo simbolo) {
		HashMap<String, Simbolo> tablaActual = pilaDeTablas.peek();
		if (tablaActual.containsKey(simbolo.nombre)) {
			err.println("?? Linea " + simbolo.lineaDeclaracion + "El identificador " + simbolo.nombre
					+ "ya ha sido declarado en este ambito.");
			return false;
		}
		tablaActual.put(simbolo.nombre, simbolo);
		return true;
	}

	public Simbolo buscar(String nombre) {
		for (int i = pilaDeTablas.size() - 1; i >= 0; i--) {
			HashMap<String, Simbolo> tabla = pilaDeTablas.get(i); // El iterador
			if (tabla.containsKey(nombre)) {
				return tabla.get(nombre);
			}
		}
		return null; // Si no se encontro en ningun ambito.
	}

	public void imprimirTabla(java.io.PrintStream destino) {
		destino.println("--- TABLA DE SIMBOLOS ----------------------------------");

		for (int i = 0; i < pilaDeTablas.size(); i++) {
			HashMap<String, Simbolo> tabla = pilaDeTablas.get(i);

			String nombreAmbito;
			if (i == 0) {
				nombreAmbito = "global";
			} else {
				nombreAmbito = "Ámbito Nivel " + i;
			}

			destino.println("\n== " + nombreAmbito + " (Registros: " + tabla.size() + ") ==");

			destino.printf("%-15s | %-10s | %-10s | %s\n", "NOMBRE", "TIPO", "ÁMBITO", "LINEA");
			destino.println("-----------------------------------------------------");

			for (Simbolo s : tabla.values()) {
				destino.printf("%-15s | %-10s | %-10s | %d\n", s.nombre, s.tipo, s.ambito, s.lineaDeclaracion);
			}
		}
		destino.println("------------------------------------ FIN TABLA ------");

	}

	private static Tabla tabla = new Tabla();

	public static Tabla getTabla() {
		return tabla;
	}

}
