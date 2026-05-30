package coco;

public class Instruccion {
    public String op;      
    public String arg1;     
    public String arg2;     
    public String res;      
    
    public Instruccion(String op, String arg1, String arg2, String res) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.res = res;
    }

    public String aString() {
        switch (op) {
            case "label":
                return res + ":";
            case "=":
                return res + " = " + arg1;
            case "[]=":
                return res + "[" + arg2 + "] = " + arg1;
            case "=[]":
                return res + " = " + arg1 + "[" + arg2 + "]";
            case "+": case "-": case "*": case "/": case "%":
            case "==": case "!=": case "<": case ">": case "<=": case ">=":
            case "&&": case "||":
            case "f+": case "f-": case "f*": case "f/":
                return res + " = " + arg1 + " " + op + " " + arg2;
            case "if_false":
                return "if_false " + arg1 + " goto " + res;
            case "goto":
                return "goto " + res;
            case "return":
                return arg1 != null ? "return " + arg1 : "return";
            case "print":
                return "print " + arg1;
            case "print_int":
                return "print_int " + arg1;
            case "print_float":
                return "print_float " + arg1;
            case "scan_int":
                return "scan_int " + arg1;
            case "scan_string":
            	return "scan_string " + arg1;
            case "param":
                return "param " + arg1;
            case "call":
                return res + " = call " + arg1 + ", " + arg2;
            case "call_void":
                return "call " + arg1 + ", " + arg2;
            default:
                // Fallback por si hay algún operador que no se consideró
                if (res != null) {
                    return res + " = " + arg1 + " " + op + " " + arg2;
                }
                return op + " " + arg1 + " " + arg2 + " " + res;
        }
    }

    @Override
    public String toString() {
        return aString() != null ? aString() : "";
    }
}