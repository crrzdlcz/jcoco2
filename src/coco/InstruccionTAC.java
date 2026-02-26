package coco;

/* 	CLASE: INSTRUCCION Three Address Code
 * Se recorre el arbol y se genera 
 * una secuencia lineal de tres instrucciones:
 * 	operador, operando1, operando2, resultado
 *
 * --- en esta clase solo se representa  
 * como quedaria el codigo intermedio para
 * para su posterior generacion---
 * 
 */

public class InstruccionTAC {
    private String op;          // Operador (+, -, =, if, goto, etc.)
    private String arg1;         // Primer operando (puede ser null)
    private String arg2;         // Segundo operando (puede ser null)
    private String resultado;    // Variable destino o etiqueta

    public InstruccionTAC(String op, String arg1, String arg2, String resultado) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
    }

    // Getters y toString para debug
    @Override
    public String toString() {
        if (op.equals("label")) {
            return resultado + ":";
        } else if (op.equals("goto")) {
            return "goto " + resultado;
        } else if (op.equals("if")) {
            return "if " + arg1 + " goto " + resultado;
        } else if (op.equals("param") || op.equals("call") || op.equals("return")) {
            // formato especial para llamadas
        }
        // Operaciones binarias o asignaciones
        return resultado + " = " + arg1 + " " + op + " " + arg2;
    }
}