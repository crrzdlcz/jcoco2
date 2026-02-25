package coco;

import java.util.Stack;

import javacc.GramaticaCoco;

import java.util.ArrayList;
import java.util.List;

public class Pila {
    private Stack<Object> stack;
    private List<String> erroresSemanticos;
    public List<String> historialPila; // Para imprimir todo el "recorrido" de la pila.

    public Pila() {
        this.stack = new Stack<>();
        this.erroresSemanticos = new ArrayList<>();
        this.historialPila = new ArrayList<>();
    }

    public void push(Object valor) {
        stack.push(valor);
        // Agregamos un movimiento.
        historialPila.add("PUSH: " + valor + "  |  Pila Actual: " + stack.toString());

    }

    public Object pop() {
        if (!stack.isEmpty()) 
        {
        	Object val = stack.pop();
            historialPila.add("POP:  " + val + "  |  Pila Actual: " + stack.toString());
            return val;
            //return stack.pop();
        }
        historialPila.add("POP:  (Intento fallido, pila vacía)");
        return null;
    }

    public Object peek() {
        if (!stack.isEmpty()) {
            return stack.peek();
        }
        return null;
    }
    
    public void registrarError(String mensaje, int linea) {
        String error = "Error Semántico (Línea " + linea + "): " + mensaje;
        erroresSemanticos.add(error);
        GramaticaCoco.registrarError(error);
    }
    
    
    public List<String> getHistorial() {
        return historialPila;
    }

    
    public void imprimirHistorial(java.io.PrintStream destino) {
        destino.println("--- TRAZA DE LA PILA SEMÁNTICA ---");
        destino.println("Formato: [Operación] | [Estado de la Pila]");
        destino.println("------------------------------------------------");
        for (String linea : historialPila) {
            destino.println(linea);
        }
        destino.println("------------------------------------------------");
        destino.println("ESTADO FINAL: " + (stack.isEmpty() ? "Vacía" : "Con elementos (Se encontraron errores)"));
    }

    public List<String> getErrores() {
        return erroresSemanticos;
    }

    public boolean estaVacia() {
        return stack.isEmpty();
    }

    public void limpiarPila() {
        stack.clear();
    }

    public int tamaño() {
        return stack.size();
    }
}