package coco;

import java.util.ArrayList;
import java.util.List;
import coco.Tabla;
import coco.Simbolo;

public class GeneradorTAC {
    
    private List<Instruccion> codigo;
    private int tempCount;
    private int labelCount;
    private java.util.Map<String, String> constantes = new java.util.HashMap<>();
    
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
        } else if (nombre.equals("AsignacionSinPuntoYComa")) {
        	genAsignacionSinPuntoYComa(nodo);
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
            //emit("=", val, null, id);
            
            // == == PROPAGACIÓN == == == == == //
            String valorConst = obtenerValorConstante(val);
            if (valorConst != null) {
                emit("=", valorConst, null, id); // <--- EMITIR EL VALOR DIRECTO
                constantes.put(id, valorConst);
            } else {
                emit("=", val, null, id);         // <--- SI NO ES CONSTANTE, EMITIR NORMAL
                constantes.remove(id);
            }
        }
    }

    private void genSentenciaIdentificador(Arbol nodo) {
        List<Arbol> hijos = nodo.getHijos();
        
        // Llamada a función suelta resta();
        if (hijos.size() > 0 && hijos.get(0).getNombreProduccion().equals("LlamadaFuncion")) {
            genExpresion(hijos.get(0)); 
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
//                emit("=", val, null, id);
                
                // == == == == RATREAR CONSTANTE == == == == // Propagación
                String valorConst = obtenerValorConstante(val);
                if (valorConst != null) {
                	emit("=", valorConst, null, id);
                	constantes.put(id, valorConst);
                } else {
                	emit("=", val, null, id);
                	constantes.remove(id);
                }
                
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
    
    private void genAsignacionSinPuntoYComa(Arbol nodo) {
        List<Arbol> hijos = nodo.getHijos();
        
        // Asignación a Vector v[i] = 5; (Por si usas vectores en el for)
        if (hijos.size() > 2 && hijos.get(0).getNombreProduccion().equals("AccesoAlVEctor")) {
            Arbol nodoVec = hijos.get(0);
            String val = genExpresion(hijos.get(2)); 
            String idVector = extraerTexto(nodoVec.getHijos(), "ID_Vector: ");
            String idx = genExpresion(nodoVec.getHijos().get(2)); 
            emit("[]=", val, idx, idVector);
        }
        // Asignación simple (ej: i = i + 1)
        else {
            String id = extraerTexto(hijos, "ID_Asignacion: ");
            Arbol exp = buscarExpresion(hijos);
            
            if (id != null && exp != null) {
                String val = genExpresion(exp);
                
                // == PROPAGACIÓN ==
                String valorConst = obtenerValorConstante(val);
                if (valorConst != null) {
                    emit("=", valorConst, null, id);
                    constantes.put(id, valorConst);
                } else {
                    emit("=", val, null, id);
                    constantes.remove(id);
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
            constantes.remove(idVar); // ya no es constante despues de leer.
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
    	
    	constantes.clear(); // Para limpiar las constantes antes de llegar a el condicional if.
    	
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
    	
    	constantes.clear(); // Para limpiar las constantes antes de iniciar el ciclo.
    	
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
        
        constantes.clear(); // Para limpiar las constantes antes del ciclo for.
        
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
            
            // == == == == OPT LOCAL == == == == //
            // Plegado de constantes con propagación.
            
            String val1 = obtenerValorConstante(arg1);
            String val2 = obtenerValorConstante(arg2);

            if (val1 != null && val2 != null) {
                String resultado = evaluarConstante(op, val1, val2);
                if (resultado != null) {
//                    emit("=", resultado, null, temp);
//                    constantes.put(temp, resultado); // El temporal también es constante
                    return resultado;
                }
            }
            
            // == == == == == == == == == == == //
            
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

    // --- Pa buscar en el árbol ---

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
    
    
    
    // == == == == == == == == == == == == ELIMINACION DE CÓDIGO MUERTO == == == == == == == == == //
    
    public void optimizarCodigoMuerto() {
        // 1. Encontrar todas las variables que realmente se se usan
        java.util.Set<String> variablesUsadas = new java.util.HashSet<>();
        
        for (Instruccion inst : codigo) {
            if (inst.arg1 != null && !esLiteralSimple(inst.arg1)) {
                variablesUsadas.add(inst.arg1);
            }
            if (inst.arg2 != null && !esLiteralSimple(inst.arg2)) {
                variablesUsadas.add(inst.arg2);
            }
        }
        
        // 2. Crear una nueva lista sin las asignaciones inútiles
        List<Instruccion> codigoOptimizado = new ArrayList<>();
        
        for (Instruccion inst : codigo) {
            boolean esAsignacionSimple = inst.op.equals("=");
            boolean esCodigoMuerto = false;
            
            // Si es una asignación (ej: z = 20)
            if (esAsignacionSimple && inst.res != null) {
                // Si la variable destino NUNCA fue leída, es código muerto
                if (!variablesUsadas.contains(inst.res)) {
                    esCodigoMuerto = true;
                }
            }
            
            // Solo guardamos si NO es código muerto
            if (!esCodigoMuerto) {
                codigoOptimizado.add(inst);
            }
        }
        
        // Reemplazamos la lista antigua
        this.codigo = codigoOptimizado;
    }

    // Auxiliar para saber si un string es un número o cadena (no una variable)
    private boolean esLiteralSimple(String str) {
        if (str == null) return true;
        // Si empieza con número, comilla, o signo menos, no es variable
        if (str.isEmpty()) return true;
        char c = str.charAt(0);
        return Character.isDigit(c) || c == '-' || c == '"';
    }
    
    // == == == == == == == == == == == == FIN ELIMINACIÓN DE CÓDIGO MUERTO == == == == == == == == //
     
    private String formatearNumero(double valor, boolean esFloat) {
        if (esFloat) return String.valueOf(valor);
        return String.valueOf((long) valor);
    }
    
    
    // PROPAGACIÓN DE CONSTANTES.
 // Verifica si un string es un número literal
    private boolean esConstante(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Obtiene el valor constante de un operando (literal o variable conocida)
    private String obtenerValorConstante(String operando) {
        if (esConstante(operando)) {
            return operando; // Ya es un literal
        }
        return constantes.get(operando); // Buscar en tabla de constantes
    }

    // Evalúa una operación entre dos constantes
    private String evaluarConstante(String op, String arg1, String arg2) {
        try {
            double v1 = Double.parseDouble(arg1);
            double v2 = Double.parseDouble(arg2);
            double resultado;
            
            switch (op) {
                case "+": resultado = v1 + v2; break;
                case "-": resultado = v1 - v2; break;
                case "*": resultado = v1 * v2; break;
                case "/": 
                    if (v2 == 0) return null; // No plegar división por cero
                    resultado = v1 / v2; 
                    break;
                case "%": 
                    if (v2 == 0) return null;
                    resultado = v1 % v2; 
                    break;
                case "==": resultado = (v1 == v2) ? 1 : 0; break;
                case "!=": resultado = (v1 != v2) ? 1 : 0; break;
                case "<":  resultado = (v1 < v2) ? 1 : 0; break;
                case ">":  resultado = (v1 > v2) ? 1 : 0; break;
                case "<=": resultado = (v1 <= v2) ? 1 : 0; break;
                case ">=": resultado = (v1 >= v2) ? 1 : 0; break;
                case "&&": resultado = ((v1 != 0) && (v2 != 0)) ? 1 : 0; break;
                case "||": resultado = ((v1 != 0) || (v2 != 0)) ? 1 : 0; break;
                default: return null;
            }
            
            // Retornar como entero si es posible
            if (resultado == (long) resultado) {
                return String.valueOf((long) resultado);
            }
            return String.valueOf(resultado);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    
    // == == == ==  == == == == == == == == == == //
    
    
    
    // ============================================
    // 			OPTIMIZACIÓN DE SALTOS
    // ============================================
    
    private java.util.Set<String> etiquetasFuncion = new java.util.HashSet<>();

    public void optimizarSaltos() {
        marcarEtiquetasDeFuncion(); 
        
        boolean cambio;
        int iteraciones = 0;
        int maxIteraciones = 10;
        
        do {
            cambio = false;
            iteraciones++;
            
            // Unir etiquetas vacías consecutivas (L1: L2: -> apuntar todo a L2)
            cambio |= aliasLabels();
            
            // Eliminar goto a la línea siguiente
            cambio |= eliminarSaltoASiguiente();
            
            // Optimizar saltos encadenados (goto -> goto)
            cambio |= optimizarSaltoASalto();
            
            // Eliminar código después de un goto incondicional
            cambio |= eliminarCodigoMuerto();
            
        } while (cambio && iteraciones < maxIteraciones);
        
        // Limpiar etiquetas que ya nadie usa
        eliminarEtiquetasNoUsadas();
    }

    private void marcarEtiquetasDeFuncion() {
        etiquetasFuncion.clear();
        for (Instruccion inst : codigo) {
            if (inst.op.equals("label") && inst.res != null) {
                if (!inst.res.matches("^L\\d+$")) {
                    etiquetasFuncion.add(inst.res);
                }
            }
        }
    }

    private boolean eliminarSaltoASiguiente() {
        boolean cambio = false;
        for (int i = 0; i < codigo.size() - 1; i++) {
            Instruccion actual = codigo.get(i);
            Instruccion siguiente = codigo.get(i + 1);
            
            if ((actual.op.equals("goto") || actual.op.equals("if_false")) 
                && siguiente.op.equals("label") 
                && actual.res != null 
                && actual.res.equals(siguiente.res)) {
                
                if (!etiquetasFuncion.contains(actual.res)) {
                    codigo.remove(i);
                    cambio = true;
                    i--; 
                }
            }
        }
        return cambio;
    }

    private boolean optimizarSaltoASalto() {
        boolean cambio = false;
        java.util.Map<String, Integer> mapaEtiquetas = mapearEtiquetas();
        
        for (int i = 0; i < codigo.size(); i++) {
            Instruccion inst = codigo.get(i);
            if (!inst.op.equals("goto") && !inst.op.equals("if_false")) continue;
            if (inst.res == null) continue;
            
            String destinoOriginal = inst.res;
            String destinoActual = destinoOriginal;
            int maxSeguimiento = 100;
            
            while (maxSeguimiento-- > 0) {
                Integer idxDestino = mapaEtiquetas.get(destinoActual);
                if (idxDestino == null) break;
                
                Instruccion instDestino = codigo.get(idxDestino);
                if (instDestino.op.equals("goto") && instDestino.res != null) {
                    if (!instDestino.res.equals(destinoActual)) {
                        destinoActual = instDestino.res;
                    } else {
                        break; 
                    }
                } else {
                    break; 
                }
            }
            
            if (!destinoActual.equals(destinoOriginal)) {
                inst.res = destinoActual;
                cambio = true;
            }
        }
        return cambio;
    }

    private boolean eliminarCodigoMuerto() {
        boolean cambio = false;
        for (int i = 0; i < codigo.size() - 1; i++) {
            Instruccion inst = codigo.get(i);
            if (!inst.op.equals("goto")) continue;
            
            int j = i + 1;
            while (j < codigo.size()) {
                Instruccion sig = codigo.get(j);
                if (sig.op.equals("label")) break;
                
                codigo.remove(j);
                cambio = true;
            }
        }
        return cambio;
    }

    private void eliminarEtiquetasNoUsadas() {
        java.util.Set<String> etiquetasReferenciadas = new java.util.HashSet<>();
        for (Instruccion inst : codigo) {
            if ((inst.op.equals("goto") || inst.op.equals("if_false")) && inst.res != null) {
                etiquetasReferenciadas.add(inst.res);
            }
        }
        etiquetasReferenciadas.addAll(etiquetasFuncion);
        
        int tamañoAntes = codigo.size();
        codigo.removeIf(inst -> inst.op.equals("label") && !etiquetasReferenciadas.contains(inst.res));
        
        if (codigo.size() < tamañoAntes) {
        }
    }

    private java.util.Map<String, Integer> mapearEtiquetas() {
        java.util.Map<String, Integer> mapa = new java.util.HashMap<>();
        for (int i = 0; i < codigo.size(); i++) {
            Instruccion inst = codigo.get(i);
            if (inst.op.equals("label") && inst.res != null) {
                mapa.put(inst.res, i);
            }
        }
        return mapa;
    }
    

    private boolean aliasLabels() {
        boolean cambio = false;
        for (int i = 0; i < codigo.size() - 1; i++) {
            Instruccion actual = codigo.get(i);
            Instruccion siguiente = codigo.get(i + 1);
            
            // Si una etiqueta está seguida inmediatamente de otra etiqueta
            if (actual.op.equals("label") && siguiente.op.equals("label")) {
                String l1 = actual.res;
                String l2 = siguiente.res;
                
                // Si L1 no es etiqueta de función
                if (!etiquetasFuncion.contains(l1)) {
                    // Redirigir todo lo que apunte a L1 hacia L2
                    for (Instruccion inst : codigo) {
                        if ((inst.op.equals("goto") || inst.op.equals("if_false")) && inst.res != null) {
                            if (inst.res.equals(l1)) {
                                inst.res = l2;
                                cambio = true;
                            }
                        }
                    }
                    
                    codigo.remove(i);
                    i--;     
                }
            }
        }
        return cambio;
    }
}

