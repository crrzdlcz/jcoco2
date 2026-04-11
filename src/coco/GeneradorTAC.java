package coco;

import java.util.ArrayList;
import java.util.List;
import coco.Tabla;
import coco.Simbolo;

public class GeneradorTAC {
    
    private List<Instruccion> codigo;
    private int tempCount;
    private int labelCount;
    
    private List<String> keywords = java.util.Arrays.asList("coco", "int", "float", "string", "bool", 
            "fn", "if", "else", "while", "for", "switch", "case", "default", "return", "print!", "scan!",
                ":", "=", ";", "(", ")", "{", "}", "[", "]");

    public GeneradorTAC() {
        this.codigo = new ArrayList<>();
        this.tempCount = 0;
        this.labelCount = 0;
    }

    public void generar(Arbol nodo) {
        if (nodo == null) return;
        
        String nombre = nodo.getNombreProduccion();

        if (nombre.equals("DeclaracionDeFuncion")) {
            genFuncion(nodo);
        } else if (nombre.equals("DeclaracionDeVariable")) {
            genDeclaracion(nodo);
        } else if (nombre.equals("SentenciasConIdentificador")) {
            genSentenciaIdentificador(nodo);
        } else if (nombre.equals("SentenciaDeRetorno")) {
            genRetorno(nodo);
        } else if (nombre.equals("SentenciaDeSalida")) {
            genSalida(nodo);
        } else if (nombre.equals("SentenciaDeEntrada")) {
            genEntrada(nodo);
        } else if (nombre.equals("Condicional_IF")) {
            genIf(nodo);
        } else if (nombre.equals("Bucle_WHILE")) {
            genWhile(nodo);
        } else if (nombre.equals("Bucle_FOR")) {
            genFor(nodo);
        } 
        // Casos recursivos (Bloques, Raíz, etc.)
        else {
            for (Arbol hijo : nodo.getHijos()) {
                generar(hijo);
            }
        }
    }

    // --- Métodos Auxiliares ---

    private String newTemp() { return "t" + (++tempCount); }
    private String newLabel() { return "L" + (++labelCount); }

    private void emit(String op, String arg1, String arg2, String res) {
        codigo.add(new Instruccion(op, arg1, arg2, res));
    }

    // --- Generadores gen ---

    private void genFuncion(Arbol nodo) {
    	// Busca el nombre de la funcion
        String nombreFunc = extraerNombreID(nodo.getHijos());
        if (nombreFunc != null) {
            emit("label", null, null, nombreFunc); // Etiqueta de inicio de función
        }
        
        // Generar código del cuerpo
        generar(nodo.getHijos().get(nodo.getHijos().size() - 1)); // Último hijo, es el bloque
    }

    private void genDeclaracion(Arbol nodo) {
        List<Arbol> hijos = nodo.getHijos();
        String id = extraerNombreID(hijos);
        
        // Buscar si hay asignación (buscar nodo expresión al final)
        Arbol exp = buscarExpresion(hijos);
        
        if (id != null && exp != null) {
            String val = genExpresion(exp);
            emit("=", val, null, id);
        }
    }

    private void genSentenciaIdentificador(Arbol nodo) {
        List<Arbol> hijos = nodo.getHijos();
        
        // Llamada a función suelta resta();
        if (hijos.size() > 0 && hijos.get(0).getNombreProduccion().equals("LlamadaFuncion")) {
            genExpresion(hijos.get(0)); // Genera param y call
        }
        
        // Asignación a Vector v[i] = 5; 
        // AccesoVector, Token =, Expresion, ;
        else if (hijos.size() > 2 && hijos.get(0).getNombreProduccion().equals("AccesoAlVEctor")) {
        	Arbol nodoVec = hijos.get(0);
        	String val = genExpresion(hijos.get(2)); // Lado derecho
        	
            String idVector = extraerTexto(nodoVec.getHijos(), "ID_Vector: ");
            String idx = genExpresion(nodoVec.getHijos().get(2)); // Índice
            
            emit("[]=", val, idx, idVector); // vect[index] = val
        }
        
        // Asignación simple (ej: x = 5; )
        // ID_Asignacion, Token =, Expresion, ;
        else {
            String id = extraerTexto(hijos, "ID_Asignacion: ");
            if (id == null) id = extraerTexto(hijos, "Token: "); // Fallback
            
            Arbol exp = buscarExpresion(hijos);
            
            // Verificar si la expresión es una llamada a función resultado = suma(5,10)
            if (id != null && exp != null) {
                String val = genExpresion(exp);
                emit("=", val, null, id);
            }
        }
    }

    private void genRetorno(Arbol nodo) {
        Arbol exp = buscarExpresion(nodo.getHijos());
        if (exp != null) {
            String val = genExpresion(exp);
            emit("return", val, null, null);
        } else {
            emit("return", null, null, null);
        }
    }

    
    private void genSalida(Arbol nodo) {
        Arbol exp = buscarExpresion(nodo.getHijos());
        if (exp != null) {
            String val = genExpresion(exp);
            String nombreExp = exp.getNombreProduccion();
            
            // 1. Si es una cadena literal, usamos print normal
            if (nombreExp.startsWith("Literal_CADENA")) {
                emit("print", val, null, null);
            } 
            // 2. Si es un número flotante literal (ej. 10.5)
            else if (nombreExp.startsWith("Literal_FLOAT")) {
                emit("print_float", val, null, null);
            }
            // 3. Si es una variable (Identificador), buscamos su tipo real en la Tabla de Símbolos
            else if (nombreExp.startsWith("Literal_IDENTIFICADOR")) {
                String idNombre = nombreExp.split(": ")[1].trim();
                Simbolo s = Tabla.getTabla().buscar(idNombre);
                if (s != null && s.tipo.equals("float")) {
                    emit("print_float", val, null, null);
                } else {
                    emit("print_int", val, null, null);
                }
            }
            // 4. Cualquier otra cosa (enteros, resultados de operaciones)
            else {
                // Usamos el método que agregamos antes para saber si la operación resultó en float
                String tipoOp = obtenerTipoDeArbol(exp);
                if (tipoOp.equals("float")) {
                    emit("print_float", val, null, null);
                } else {
                    emit("print_int", val, null, null);
                }
            }
        }
    }
    
    
    // Para la entrada, solo son numeros enteros
    private void genEntrada(Arbol nodo) {
        String idVar = null;
        
        // Buscamos el identificador de la variable
        for (Arbol h : nodo.getHijos()) {
            String nombre = h.getNombreProduccion();
            if (nombre.startsWith("Token: ")) {
                String val = nombre.split(": ")[1].trim();
                
                // Filtramos la palabra reservada y los paréntesis/punto y coma
                if (!val.equals("scan!") && !val.equals("(") && !val.equals(")") && !val.equals(";")) {
                    idVar = val;
                    break; // Ya encontramos la variable, salimos del ciclo
                }
            }
        }
        
        if (idVar != null) {
            emit("scan_int", idVar, null, idVar);
        }
    }
    // Método auxiliar para saber si una operación es flotante
    private String obtenerTipoDeArbol(Arbol nodo) {
        if (nodo == null) return "int";
        String nombre = nodo.getNombreProduccion();
        if (nombre.startsWith("Literal_FLOAT")) return "float";
        if (nombre.startsWith("Literal_INT")) return "int";
        if (nombre.startsWith("Literal_IDENTIFICADOR")) {
            try {
                String id = nombre.split(": ")[1].trim();
                Simbolo s = Tabla.getTabla().buscar(id);
                if (s != null && s.tipo.equals("float")) return "float";
            } catch (Exception e) {}
        }
        if (nombre.startsWith("Operacion_") || nombre.startsWith("Operador_")) {
            if (nodo.getHijos().size() > 0) {
                if (obtenerTipoDeArbol(nodo.getHijos().get(0)).equals("float")) return "float";
            }
        }
        return "int";
    }
    
    private void genIf(Arbol nodo) {
        String lElse = newLabel();
        String lEnd = newLabel();
        
        Arbol cond = buscarExpresion(nodo.getHijos());
        
        if (cond != null) {
            String tempCond = genExpresion(cond);
            emit("if_false", tempCond, null, lElse);
        } else {
            // Si  falla, entonces es error.
            emit("if_false", "null_cond", null, lElse);
        }
        
        // Bloque IF
        Arbol bloqueIf = buscarNodo(nodo.getHijos(), "BloqueDeCodigo");
        if (bloqueIf != null) generar(bloqueIf);
        
        emit("goto", null, null, lEnd);
        
        // Bloque ELSE
        emit("label", null, null, lElse);
        // El else puede ser un bloque o otro IF (else if)
        for (Arbol h : nodo.getHijos()) {
            if (h.getNombreProduccion().equals("Condicional_IF")) generar(h); // Else If
            else if (h.getNombreProduccion().equals("BloqueDeCodigo") && h != bloqueIf) generar(h); // Else simple
        }
        
        emit("label", null, null, lEnd);
    }
    
    private void genWhile(Arbol nodo) {
        String lStart = newLabel();
        String lEnd = newLabel();
        
        emit("label", null, null, lStart);
        
        Arbol cond = buscarExpresion(nodo.getHijos());
        if(cond != null) {
            String tempCond = genExpresion(cond);
            emit("if_false", tempCond, null, lEnd);
        }
        
        Arbol bloque = buscarNodo(nodo.getHijos(), "BloqueDeCodigo");
        if (bloque != null) generar(bloque);
        
        emit("goto", null, null, lStart);
        emit("label", null, null, lEnd);
    }
    
    private void genFor(Arbol nodo) {
        // Inicialización
        generar(buscarNodo(nodo.getHijos(), "Inicializacion_Bucle_FOR"));
        
        String lStart = newLabel();
        String lEnd = newLabel();
        
        emit("label", null, null, lStart);
        
        // Condición
        Arbol cond = buscarExpresion(nodo.getHijos());
        if(cond != null) {
            String tempCond = genExpresion(cond);
            emit("if_false", tempCond, null, lEnd);
        }

        // Cuerpo
        generar(buscarNodo(nodo.getHijos(), "BloqueDeCodigo"));
        
        // Actualización
        generar(buscarNodo(nodo.getHijos(), "Actualizacion_Bucle_FOR"));
        
        emit("goto", null, null, lStart);
        emit("label", null, null, lEnd);
    }

    // --- Expresiones ---

    private String genExpresion(Arbol nodo) {
        if (nodo == null) return null;
        String nombre = nodo.getNombreProduccion();

        // Literales
        if (nombre.startsWith("Literal_INT") || nombre.startsWith("Literal_FLOAT") || 
            nombre.startsWith("Literal_BOOL") || nombre.startsWith("Literal_CADENA")) {
            return nodo.toString().split(": ")[1].trim();
        }
        
        // Identificador
        if (nombre.startsWith("Literal_IDENTIFICADOR")) {
            return nodo.toString().split(": ")[1].trim();
        }

        // Operación Binaria
        if (nombre.startsWith("Operacion_") || nombre.startsWith("Operador_")) {
            List<Arbol> hijos = nodo.getHijos();
            String arg1 = genExpresion(hijos.get(0));
            String op = hijos.get(1).toString().split(": ")[1].trim();
            String arg2 = genExpresion(hijos.get(2));

            String temp = newTemp();
            
            // --- PARA AND (&&) ---
            if (op.equals("&&")) {
                String lFalso = newLabel();
                String lFin = newLabel();
                
                // Si arg1 es falso (0), saltamos directo a poner el 0
                emit("if_false", arg1, null, lFalso);
                // Si arg2 es falso (0), saltamos a poner el 0
                emit("if_false", arg2, null, lFalso);
                // Si no saltamos, ambos son verdaderos
                emit("=", "1", null, temp);
                emit("goto", null, null, lFin);
                
                // Etiqueta donde ponemos falso
                emit("label", null, null, lFalso);
                emit("=", "0", null, temp);
                
                // Etiqueta de fin
                emit("label", null, null, lFin);
                return temp;
            }
            // --- PARA OR (||) ---
            else if (op.equals("||")) {
                String lCheckArg2 = newLabel();
                String lFalso = newLabel();
                String lFin = newLabel();
                
                // Si arg1 es verdadero (!=0), saltamos a poner el 1
                emit("if_false", arg1, null, lCheckArg2);
                emit("=", "1", null, temp);
                emit("goto", null, null, lFin);
                
                // Aquí llegamos si arg1 fue falso. Ahora evaluamos arg2
                emit("label", null, null, lCheckArg2);
                emit("if_false", arg2, null, lFalso);
                emit("=", "1", null, temp);
                emit("goto", null, null, lFin);
                
                // Aquí llegamos si ambos fueron falsos
                emit("label", null, null, lFalso);
                emit("=", "0", null, temp);
                
                // Etiqueta de fin
                emit("label", null, null, lFin);
                return temp;
            }
            
            // NUEVO: Si es una operación de flotantes, le agregamos una 'f' (ej: f+)
            if (obtenerTipoDeArbol(nodo).equals("float")) {
                if (op.equals("+")) op = "f+";
                else if (op.equals("-")) op = "f-";
                else if (op.equals("*")) op = "f*";
                else if (op.equals("/")) op = "f/";
            }

            // Los operadores relacionales (==, !=, <, >) se quedan igual
            emit(op, arg1, arg2, temp);
            return temp;
        }

        // Llamada a Función (dentro de expresión)
        if (nombre.equals("LlamadaFuncion")) {
            return genLlamadaFuncion(nodo);
        }
        
        // Acceso a Vector lectura x = v[i]
        if (nombre.equals("AccesoAlVEctor")) {
            String idVector = extraerTexto(nodo.getHijos(), "ID_Vector: ");
            String idx = genExpresion(nodo.getHijos().get(2));
            String temp = newTemp();
            emit("=[]", idVector, idx, temp); // temp = array[idx]
            return temp;
        }
        
        // Pa cuando 'i' se usa dentro de corchetes
        if (nombre.startsWith("ID_Indice")) {
            return nodo.toString().split(": ")[1].trim();
        }

        return "null";
    }

    private String genLlamadaFuncion(Arbol nodo) {
        List<Arbol> hijos = nodo.getHijos();
        String idFuncion = extraerTexto(hijos, "Token: ");
        
        int numArgs = 0;
        // Recorrer hijos buscando expresiones (argumentos)
        for (int i = 1; i < hijos.size(); i++) {
            Arbol h = hijos.get(i);
            if (h.getNombreProduccion().startsWith("Operacion_") || 
                    h.getNombreProduccion().startsWith("Operador_") ||
                    h.getNombreProduccion().startsWith("Literal_") ||
                    h.getNombreProduccion().startsWith("Literal_IDENTIFICADOR")) {
                    String arg = genExpresion(h);
                    emit("param", arg, null, null);
                    numArgs++;
            }
        }
        
        String temp = newTemp();
        emit("call", idFuncion, String.valueOf(numArgs), temp);
        return temp;
    }

    // --- Utilidades para buscar en el árbol ---

    private String extraerTexto(List<Arbol> hijos, String prefijo) {
        for (Arbol h : hijos) {
            if (h.getNombreProduccion().startsWith(prefijo)) {
                return h.getNombreProduccion().split(": ")[1].trim();
            }
        }
        return null;
    }

    private Arbol buscarExpresion(List<Arbol> hijos) {
        for (Arbol h : hijos) {
        	String n = h.getNombreProduccion();
            if (n.startsWith("Operacion_") || n.startsWith("Operador_") || 
                    n.startsWith("Literal_") || n.equals("LlamadaFuncion")) {
                    return h;
            }
        }
        return null;
    }
    
    // Extrae el nombre de una variable o una funcion.
    private String extraerNombreID(List<Arbol> hijos) {
        for (Arbol h : hijos) {
            if (h.getNombreProduccion().startsWith("Token: ")) {
                String val = h.getNombreProduccion().split(": ")[1].trim();
                if (!keywords.contains(val)) { // Si no es "coco", "fn", etc.
                    return val;
                }
            }
        }
        return null;
    }
    
    private Arbol buscarNodo(List<Arbol> hijos, String nombre) {
        for(Arbol h : hijos) {
            if (h.getNombreProduccion().equals(nombre)) return h;
        }
        return null;
    }

    public List<Instruccion> getCodigo() {
        return codigo;
    }
}

