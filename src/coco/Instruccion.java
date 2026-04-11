package coco;

public class Instruccion {
    public String op;       // Operador (+, -, =, if, goto, label)
    public String arg1;     // Argumento 1 o operando
    public String arg2;     // Argumento 2
    public String res;      // Resultado o etiqueta destino

    public Instruccion(String op, String arg1, String arg2, String res) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.res = res;
    }

    @Override
    public String toString() {
        // Formato pa imprimir el txt
        if (op.equals("label")) {
            return res + ":";
        } else if (op.equals("goto")) {
            return "goto " + res;
        } else if (op.equals("if_false")) {
            return "if_false " + arg1 + " goto " + res;
        } else if (op.equals("print") || op.equals("scan")) {
            return op + " " + arg1;
        } else if (op.equals("param")) {
            return "param " + arg1;
        } else if (op.equals("call")) {
            if (res != null) return res + " = call " + arg1 + ", " + arg2;
            return "call " + arg1 + ", " + arg2;
        } else if (op.equals("return")) {
            if (arg1 != null) return "return " + arg1;
            return "return";
        } else if (op.equals("=")) {
            return res + " = " + arg1;
        }
        else if (op.equals("print_int")) {
        	return "print_int " + arg1;
        } else if (op.equals("print_float")) {
        	return "print_float " + arg1;
        }
        else if (op.equals("[]=")) {
            return res + "[" + arg2 + "] = " + arg1; // res = array, arg2 = index, arg1 = value
        }
        else if (op.equals("=[]")) {
            return res + " = " + arg1 + "[" + arg2 + "]"; // res = temp, arg1 = array, arg2 = index
        }
        // Para leer datos desde el teclado, solo int.
        else if (op.equals("scan_int")) {
            return "scan_int " + arg1;
        }
        // Operaciones binarias: res = arg1 op arg2
        return res + " = " + arg1 + " " + op + " " + arg2;
    }
}