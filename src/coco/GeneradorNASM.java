package coco;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneradorNASM {

    // Estructuras para guardar las secciones del código ensamblador
    private StringBuilder dataSection = new StringBuilder();
    private StringBuilder bssSection = new StringBuilder();
    private StringBuilder textSection = new StringBuilder();
    private StringBuilder functionsSection = new StringBuilder(); // Para guardar las funciones antes de _start

    // Control de variables y cadenas para no duplicarlas
    private Set<String> variablesDeclaradas = new HashSet<>();
    private Map<String, String> cadenasDeclaradas = new LinkedHashMap<>();
    private int contadorCadenas = 0;
    
    // Para saber si estamos escribiendo dentro de una función
    private boolean insideFunction = false;
    private boolean requierePrintInt = false;
    private boolean requierePrintFloat = false;
    private boolean requiereScanInt = false;

    // Patrones RegEx (Sin cambios)
    private final Pattern PAT_ASSIGN_ARR = Pattern.compile("^(\\w+)\\[(.+)\\]\\s*=\\s*(.+)$");
    private final Pattern PAT_READ_ARR = Pattern.compile("^(\\w+)\\s*=\\s*(\\w+)\\[(.+)\\]$");
    private final Pattern PAT_OP_BIN = Pattern.compile("^(\\w+)\\s*=\\s*(.+)\\s+(f\\+|f\\-|f\\*|f\\/|%|==|!=|>=|<=|<|>|&&|\\|\\||\\+|\\-|\\*|\\/)\\s+(.+)$");
    private final Pattern PAT_ASSIGN_SIMPLE = Pattern.compile("^(\\w+)\\s*=\\s*(.+)$");
    private final Pattern PAT_PRINT = Pattern.compile("^print\\s+(.+)$");
    private final Pattern PAT_PARAM = Pattern.compile("^param\\s+(.+)$");
    private final Pattern PAT_CALL = Pattern.compile("^(\\w+)\\s*=\\s*call\\s+(\\w+)\\s*,\\s*(\\d+)$");
    private final Pattern PAT_CALL_VOID = Pattern.compile("^call\\s+(\\w+)\\s*,\\s*(\\d+)$");
    private final Pattern PAT_IF_FALSE = Pattern.compile("^if_false\\s+(\\w+)\\s+goto\\s+(\\w+)$");
    private final Pattern PAT_GOTO = Pattern.compile("^goto\\s+(\\w+)$");
    private final Pattern PAT_RETURN = Pattern.compile("^return\\s*(.*)$");
    private final Pattern PAT_SCAN_INT = Pattern.compile("^scan_int\\s+(.+)$");

    public void generarASM(String rutaTAC, String rutaSalidaASM) {
        try {
            // Leemos todo el archivo a una lista para poder "mirar hacia adelante"
            List<String> lineasTAC = Files.readAllLines(Paths.get(rutaTAC), StandardCharsets.UTF_8);

            // 1. Inicializar secciones (OJO: Aquí ya NO ponemos _start)
            dataSection.append("section .data\n");
            bssSection.append("section .bss\n");
            // textSection solo guardará el código principal, se le agregará _start al final

            for (int i = 0; i < lineasTAC.size(); i++) {
                String linea = lineasTAC.get(i).trim();
                if (linea.isEmpty() || linea.startsWith("---")) continue;

                // --- LÓGICA DE ETIQUETAS ---
                if (linea.endsWith(":")) {
                    // Si es una etiqueta de salto (L1, L2, etc.), mantiene su estado actual
                    if (linea.matches("^L\\d+:$")) {
                        emit(linea + "\n");
                    } 
                    // Si es una etiqueta con nombre (suma, resta), ES UNA FUNCIÓN
                    else {
                        insideFunction = true;
                        emit(linea + "\n");
                    }
                    continue;
                }

                // Procesar la línea normal
                procesarLineaTAC(linea);

                // --- LÓGICA PARA DETECTAR EL FIN DE LA FUNCIÓN ---
                // Si estamos en una función, y la línea actual es un 'return'
                if (insideFunction && linea.startsWith("return")) {
                    // Miramos la siguiente línea útil para ver si sigue siendo parte de la función (ej. un L1 de un if interno)
                    boolean funcionTermina = true;
                    for (int j = i + 1; j < lineasTAC.size(); j++) {
                        String sigLinea = lineasTAC.get(j).trim();
                        if (sigLinea.isEmpty() || sigLinea.startsWith("---")) continue;
                        
                        // Si lo que viene después es una etiqueta de salto, la función NO termina todavía
                        if (sigLinea.matches("^L\\d+:$")) {
                            funcionTermina = false;
                        }
                        break; // Ya revisamos la siguiente línea útil, salimos del for
                    }
                    
                    if (funcionTermina) {
                        insideFunction = false; // La función terminó, el resto es código principal
                    }
                }
            }
            
            // --- PREPARACIÓN FINAL DE SECCIONES ---
            
            // Si usamos floats, inyectamos sus variables de apoyo en .data ANTES de escribir
            if (requierePrintFloat) {
                requierePrintInt = true; // Un float siempre necesita la rutina de enteros
                dataSection.append("    dot_char db '.'\n");
                dataSection.append("    neg_one dq -1.0\n"); 
                dataSection.append("    diez dq 10.0\n");
            }
            
            // Inyectamos variables de la rutina de enteros en .data también
            if (requierePrintInt) {
                dataSection.append("    char_minus db '-'\n");
                dataSection.append("    new_line db 10\n");
            }

            // 2. Construir el archivo final ensamblando las piezas en el orden CORRECTO
            try (FileWriter fw = new FileWriter(rutaSalidaASM)) {
                
                // AHORA SÍ escribimos el .data que ya está completo
                fw.write(dataSection.toString() + "\n");
                fw.write(bssSection.toString() + "\n");
                
                fw.write("section .text\n    default rel\n");
                fw.write("    global _start\n\n");
                
                // PRIMERO: Las funciones de Coco
                if (functionsSection.length() > 0) {
                    fw.write(functionsSection.toString() + "\n");
                }
                
                // SEGUNDO: El punto de entrada principal
                fw.write("_start:\n");
                fw.write(textSection.toString());

                // FIN DEL PROGRAMA
                fw.write("\n; --- FIN DEL PROGRAMA ---\n");
                fw.write("    mov rax, 60\n    xor rdi, rdi\n    syscall\n");
                
                // SUBRUTINA PARA IMPRIMIR ENTEROS
                if (requierePrintInt) {
                    fw.write("\n; --- SUBRUTINA PARA IMPRIMIR ENTEROS ---\n");
                    fw.write("print_integer:\n");
                    fw.write("    push r12\n");
                    fw.write("    push r13\n");
                    fw.write("    sub rsp, 32\n"); // Creamos un buffer de 32 bytes en la pila
                    
                    fw.write("    mov rax, [rdi]\n"); // Cargamos el valor de la variable
                    fw.write("    mov r13, rsp\n");   // r13 apuntará al final de nuestro buffer
                    fw.write("    add r13, 31\n");    // Nos posicionamos en el último byte (índice 31)
                    fw.write("    mov byte [r13], 10\n"); // Ponemos un salto de línea al final
                    fw.write("    mov r12, 1\n");     // Inicializamos el contador de longitud en 1 (el salto de línea)
                    
                    // Revisamos si es negativo
                    fw.write("    cmp rax, 0\n");
                    fw.write("    jge .start_div\n");
                    fw.write("    neg rax\n");        // Lo convertimos a positivo
                    fw.write("    dec r13\n");        // Retrocedemos el puntero
                    fw.write("    mov byte [r13], '-'\n"); // Escribimos el signo menos
                    fw.write("    inc r12\n");        // Aumentamos la longitud
                    
                    fw.write(".start_div:\n");
                    fw.write("    mov rbx, 10\n");
                    fw.write(".loop_div:\n");
                    fw.write("    xor rdx, rdx\n");
                    fw.write("    div rbx\n");        // Dividimos rax entre 10
                    fw.write("    add rdx, '0'\n");   // Convertimos el residuo a ASCII
                    fw.write("    dec r13\n");        // Retrocedemos el puntero del buffer
                    fw.write("    mov [r13], dl\n");  // Guardamos el CARÁCTER en el buffer de memoria
                    fw.write("    inc r12\n");        // Aumentamos el tamaño total de la cadena
                    fw.write("    cmp rax, 0\n");
                    fw.write("    jnz .loop_div\n");
                    
                    // --- IMPRIMIR TODO DE UNA SOLA VEZ ---
                    // r13 ahora apunta al inicio del número
                    // r12 tiene la longitud total
                    fw.write("    mov rax, 1\n");
                    fw.write("    mov rdi, 1\n");
                    fw.write("    mov rsi, r13\n");   // PASAMOS EL PUNTERO AL INICIO DEL BUFFER
                    fw.write("    mov rdx, r12\n");   // PASAMOS LA LONGITUD TOTAL
                    fw.write("    syscall\n");
                    
                    fw.write("    add rsp, 32\n");    // Limpiamos el buffer
                    fw.write("    pop r13\n");
                    fw.write("    pop r12\n");
                    fw.write("    ret\n\n");
                }
                
                
                // SUBRUTINA PARA LEER ENTEROS DESDE TECLADO
                if (requiereScanInt) {
                    fw.write("\n; --- SUBRUTINA PARA SCAN INT ---\n");
                    fw.write("read_integer:\n");
                    fw.write("    push r12\n");
                    fw.write("    push r13\n");
                    fw.write("    push rbx\n");          // Guardamos rbx en la pila
                    fw.write("    mov rbx, rdi\n");      // rbx guarda la dirección de 'x'
                    fw.write("    sub rsp, 32\n");       // Buffer de 32 bytes en la pila
                    
                    // 1. Llamar al sistema operativo para leer del teclado
                    fw.write("    mov rax, 0\n");        // sys_read
                    fw.write("    mov rdi, 0\n");        // stdin (Aquí ya podemos sobreescribir rdi sin miedo)
                    fw.write("    mov rsi, rsp\n");      // Guardar en nuestro buffer
                    fw.write("    mov rdx, 32\n");       // Leer máximo 32 bytes
                    fw.write("    syscall\n");
                    
                    // 2. Asegurar que el string termine en 0 (por si acaso)
                    fw.write("    mov byte [rsp + rax], 0\n"); 
                    
                    // 3. Inicializar variables
                    fw.write("    xor rax, rax\n");      // rax = total final
                    fw.write("    xor r13, r13\n");      // r13 = bandera de negativo
                    fw.write("    mov r12, 0\n");        // r12 = índice para recorrer
                    
                    fw.write(".check_sign:\n");
                    fw.write("    movzx rcx, byte [rsp + r12]\n"); 
                    fw.write("    cmp cl, '-'\n");
                    fw.write("    jne .start_loop\n");
                    fw.write("    mov r13, 1\n");        // Es negativo
                    fw.write("    inc r12\n");
                    fw.write("    jmp .check_sign\n");
                    
                    // Bucle principal
                    fw.write(".start_loop:\n");
                    fw.write("    movzx rcx, byte [rsp + r12]\n"); 
                    
                    // Si es fin de string (0) O salto de línea (10 = \n), terminamos
                    fw.write("    cmp rcx, 0\n");
                    fw.write("    je .done_read\n");
                    fw.write("    cmp rcx, 10\n"); 
                    fw.write("    je .done_read\n");
                    
                    // Si NO es un dígito (menor que '0' o mayor que '9'), lo ignoramos
                    fw.write("    cmp cl, '0'\n");
                    fw.write("    jb .skip_char\n");
                    fw.write("    cmp cl, '9'\n");
                    fw.write("    ja .skip_char\n");
                    
                    // Convertir ASCII a número (ej: '5' - '0' = 5)
                    fw.write("    sub rcx, '0'\n");
                    
                    // Multiplicar total por 10 y sumarle el dígito
                    fw.write("    imul rax, 10\n");
                    fw.write("    add rax, rcx\n");
                    
                    fw.write(".skip_char:\n");
                    fw.write("    inc r12\n");
                    fw.write("    jmp .start_loop\n");
                    
                    // Si era negativo, multiplicamos por -1
                    fw.write(".done_read:\n");
                    fw.write("    cmp r13, 1\n");
                    fw.write("    jne .save_value\n");
                    fw.write("    imul rax, -1\n");
                    
                    fw.write(".save_value:\n");
                    // Guardar resultado usando rbx (que tiene la dirección real de 'x')
                    fw.write("    mov [rbx], rax\n");
                    
                    // Restaurar pila y salir
                    fw.write("    add rsp, 32\n");
                    fw.write("    pop rbx\n");           // Restauramos rbx
                    fw.write("    pop r13\n");
                    fw.write("    pop r12\n");
                    fw.write("    ret\n\n");
                }
                
                // SUBRUTINA PARA IMPRIMIR FLOTANTES 
                if (requierePrintFloat) {
                    fw.write("\n; --- SUBRUTINA PARA IMPRIMIR FLOTANTES (2 decimales) ---\n");
                    fw.write("print_float:\n");
                    fw.write("    push r12\n");      
                    fw.write("    sub rsp, 48\n"); 
                    fw.write("    movq xmm0, [rdi]\n"); 
                    
                    // Revisar signo
                    fw.write("    movq rax, xmm0\n");
                    fw.write("    mov rbx, 0x8000000000000000\n"); 
                    fw.write("    and rax, rbx\n");
                    fw.write("    cmp rax, 0\n");
                    fw.write("    je .float_positive\n");
                    
                    fw.write("    push rax\n    mov rax, 1\n    mov rdi, 1\n    lea rsi, [rel char_minus]\n    mov rdx, 1\n    syscall\n");
                    fw.write("    pop rax\n");
                    fw.write("    mulsd xmm0, [rel neg_one]\n"); 
                    
                    fw.write(".float_positive:\n");
                    fw.write("    movq xmm1, xmm0\n");
                    fw.write("    cvttsd2si r8, xmm0\n"); // Extraer parte entera
                    fw.write("    mov [rsp+32], r8\n");    // Guardar entero en espacio temporal seguro
                    
                    // --- PARTE ENTERA: Rellenar buffer de derecha a izquierda (Sin usar push) ---
                    fw.write("    mov rax, [rsp+32]\n");
                    fw.write("    cmp rax, 0\n    jge .fp_ent_pos\n");
                    fw.write("    push rax\n    mov rax, 1\n    mov rdi, 1\n    lea rsi, [rel char_minus]\n    mov rdx, 1\n    syscall\n");
                    fw.write("    pop rax\n    neg rax\n");
                    
                    fw.write(".fp_ent_pos:\n");
                    fw.write("    lea r12, [rsp+31]\n"); // Apuntador al final del buffer de enteros
                    fw.write("    mov rbx, 10\n");
                    fw.write(".fp_bucle_ent:\n");
                    fw.write("    xor rdx, rdx\n    div rbx\n");
                    fw.write("    add rdx, '0'\n");
                    fw.write("    mov [r12], dl\n");   // Guardar dígito directamente en memoria
                    fw.write("    dec r12\n");
                    fw.write("    cmp rax, 0\n    jnz .fp_bucle_ent\n");
                    fw.write("    inc r12\n");          // Ajustar al primer dígito válido
                    
                    // Imprimir parte entera usando el buffer
                    fw.write("    lea rdx, [rsp+32]\n");
                    fw.write("    sub rdx, r12\n");     // Calcular longitud
                    fw.write("    mov rax, 1\n    mov rdi, 1\n    mov rsi, r12\n    mov rdx, rdx\n    syscall\n");
                    
                    // Imprimir punto decimal
                    fw.write("    mov rax, 1\n    mov rdi, 1\n    lea rsi, [rel dot_char]\n    mov rdx, 1\n    syscall\n");
                    
                    // --- PARTE DECIMAL: Rellenar buffer de izquierda a derecha ---
                    fw.write("    mov r8, [rsp+32]\n");
                    fw.write("    cvtsi2sd xmm2, r8\n"); 
                    fw.write("    subsd xmm1, xmm2\n"); // xmm1 = solo decimales
                    fw.write("    lea r12, [rsp]\n");   // Buffer para decimales
                    
                    fw.write("    mov rcx, 2\n"); 
                    fw.write(".fp_bucle_dec:\n");
                    fw.write("    mulsd xmm1, [rel diez]\n"); 
                    fw.write("    cvttsd2si r8, xmm1\n"); 
                    
                    // Restar el valor numérico ANTES de convertirlo a ASCII
                    fw.write("    cvtsi2sd xmm2, r8\n");
                    fw.write("    subsd xmm1, xmm2\n");
                    
                    fw.write("    add r8, '0'\n"); 
                    fw.write("    mov [r12], r8b\n");  // Guardar decimal
                    fw.write("    inc r12\n");
                    fw.write("    dec rcx\n    cmp rcx, 0\n    jnz .fp_bucle_dec\n");
                    
                    // Imprimir decimales (siempre 2)
                    fw.write("    mov rax, 1\n    mov rdi, 1\n    mov rsi, rsp\n    mov rdx, 2\n    syscall\n");
                    
                    // Imprimir salto de línea
                    fw.write("    mov rax, 1\n    mov rdi, 1\n    lea rsi, [rel new_line]\n    mov rdx, 1\n    syscall\n");
                    
                    fw.write("    add rsp, 48\n");
                    fw.write("    pop r12\n");       
                    fw.write("    ret\n\n");
                }
                
            } // Fin del FileWriter


        } catch (IOException e) {
            System.err.println("❌ Error al leer/escribir archivos: " + e.getMessage());
        }
    }

    // Método auxiliar que decide si el código va a la sección de funciones o al principal
    private void emit(String texto) {
        if (insideFunction) {
            functionsSection.append(texto);
        } else {
            textSection.append(texto);
        }
    }

    private void procesarLineaTAC(String linea) {
        Matcher m;

        // ASIGNACIÓN A VECTOR ( v[i] = x )
        m = PAT_ASSIGN_ARR.matcher(linea);
        if (m.matches()) {
            String vector = m.group(1);
            String indice = m.group(2);
            String valor = m.group(3);
            agregarVariableBSS(vector, true);

            emit("    ; " + linea + "\n");
            emit("    mov rbx, " + (esNumero(indice) ? indice : "[" + indice + "]") + "\n");
            emit("    shl rbx, 3\n");
            emit("    mov qword [" + vector + " + rbx], " + (esNumero(valor) ? valor : "[" + valor + "]") + "\n\n");
            return;
        }

        // LECTURA DE VECTOR ( x = v[i] )
        m = PAT_READ_ARR.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String vector = m.group(2);
            String indice = m.group(3);
            agregarVariableBSS(dest, false);
            agregarVariableBSS(vector, true);

            emit("    ; " + linea + "\n");
            emit("    mov rbx, " + (esNumero(indice) ? indice : "[" + indice + "]") + "\n");
            emit("    shl rbx, 3\n");
            emit("    mov rax, [" + vector + " + rbx]\n");
            emit("    mov [" + dest + "], rax\n\n");
            return;
        }

        // OPERACIONES BINARIAS
        m = PAT_OP_BIN.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String op1 = m.group(2);
            String operador = m.group(3);
            String op2 = m.group(4);
            agregarVariableBSS(dest, false);
            agregarVariableBSS(op1, false);
            agregarVariableBSS(op2, false);

            emit("    ; " + linea + "\n");
            emit("    mov rax, " + (esNumero(op1) ? op1 : "[" + op1 + "]") + "\n");
            
            
            switch (operador) {
            case "+": emit("    add rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
            case "-": emit("    sub rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
            case "*": emit("    imul rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
            case "/": 
                emit("    cqo\n");
                emit("    idiv qword " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
            case "%":
                emit("    cqo\n"); // Extender el signo de rax hacia rdx
                if (esNumero(op2)) {
                    // Si es un número (ej: 2), lo movemos a rbx porque idiv no acepta constantes
                    emit("    mov rbx, " + op2 + "\n");
                    emit("    idiv rbx\n");
                } else {
                    // Si es una variable, dividimos normalmente
                    emit("    idiv qword [" + op2 + "]\n");
                }
                emit("    mov rax, rdx\n"); // El residuo queda en rdx, lo pasamos a rax
                break;
                
            // --- OPERACIONES PARA FLOTANTES ---
            case "f+": 
                emit("    movq xmm0, " + (esNumero(op1) ? "__float64__("+op1+")" : "[" + op1 + "]") + "\n");
                emit("    movq xmm1, " + (esNumero(op2) ? "__float64__("+op2+")" : "[" + op2 + "]") + "\n");
                emit("    addsd xmm0, xmm1\n"); // Suma flotante
                emit("    movq rax, xmm0\n");   // Pasamos el resultado a rax para guardarlo normal
                break;
            case "f-": 
                emit("    movq xmm0, " + (esNumero(op1) ? "__float64__("+op1+")" : "[" + op1 + "]") + "\n");
                emit("    movq xmm1, " + (esNumero(op2) ? "__float64__("+op2+")" : "[" + op2 + "]") + "\n");
                emit("    subsd xmm0, xmm1\n"); // Resta flotante
                emit("    movq rax, xmm0\n");
                break;
            case "f*": 
                emit("    movq xmm0, " + (esNumero(op1) ? "__float64__("+op1+")" : "[" + op1 + "]") + "\n");
                emit("    movq xmm1, " + (esNumero(op2) ? "__float64__("+op2+")" : "[" + op2 + "]") + "\n");
                emit("    mulsd xmm0, xmm1\n"); // Multiplicación flotante
                emit("    movq rax, xmm0\n");
                break;
            case "f/": 
                emit("    movq xmm0, " + (esNumero(op1) ? "__float64__("+op1+")" : "[" + op1 + "]") + "\n");
                emit("    movq xmm1, " + (esNumero(op2) ? "__float64__("+op2+")" : "[" + op2 + "]") + "\n");
                emit("    divsd xmm0, xmm1\n"); // División flotante
                emit("    movq rax, xmm0\n");
                break;
            // ----------------------------------------

            case "==": emitirComparacion("sete", op2); break;
            case "!=": emitirComparacion("setne", op2); break;
            case "<":  emitirComparacion("setl", op2); break;
            case ">":  emitirComparacion("setg", op2); break;
            case ">=": emitirComparacion("setge", op2); break;   // Para mayor igual
            case "<=": emitirComparacion("setle", op2); break;
            case "&&": emit("    and rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
            case "||": emit("    or rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n"); break;
        }
            
            emit("    mov [" + dest + "], rax\n\n");
            return;
        }

        // PARÁMETROS
        m = PAT_PARAM.matcher(linea);
        if (m.matches()) {
            String val = m.group(1).replace("\"", "");
            emit("    push qword " + (esNumero(val) ? val : val) + "\n");
            return;
        }

        // LLAMADA A FUNCIÓN CON RETORNO
        m = PAT_CALL.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String func = m.group(2);
            int nArgs = Integer.parseInt(m.group(3));
            agregarVariableBSS(dest, false);
            emit("    call " + func + "\n");
            emit("    add rsp, " + (nArgs * 8) + "\n");
            emit("    mov [" + dest + "], rax\n\n");
            return;
        }

        // LLAMADA A FUNCIÓN SIN RETORNO
        m = PAT_CALL_VOID.matcher(linea);
        if (m.matches()) {
            String func = m.group(1);
            int nArgs = Integer.parseInt(m.group(2));
            emit("    call " + func + "\n");
            emit("    add rsp, " + (nArgs * 8) + "\n\n");
            return;
        }

        // IF FALSE
        m = PAT_IF_FALSE.matcher(linea);
        if (m.matches()) {
            String cond = m.group(1);
            String label = m.group(2);
            emit("    cmp qword [" + cond + "], 0\n");
            emit("    je " + label + "\n\n");
            return;
        }

        // GOTO
        m = PAT_GOTO.matcher(linea);
        if (m.matches()) {
            emit("    jmp " + m.group(1) + "\n\n");
            return;
        }

        // RETURN
        m = PAT_RETURN.matcher(linea);
        if (m.matches()) {
            String val = m.group(1).trim();
            if (!val.isEmpty()) {
                emit("    mov rax, " + (esNumero(val) ? val : "[" + val + "]") + "\n");
            }
            emit("    ret\n\n");
            return;
        }

        // PRINT
        // MATCH PARA PRINT_INT O PRINT_FLOAT
        Pattern PAT_PRINT_TIPO = Pattern.compile("^print_(int|float)\\s+(.+)$");
        m = PAT_PRINT_TIPO.matcher(linea);
        if (m.matches()) {
            String tipo = m.group(1);
            String val = m.group(2).trim();
            
            if (tipo.equals("int")) {
                requierePrintInt = true;
                agregarVariableBSS(val, false);
                emit("    ; print int " + val + "\n");
                emit("    mov rdi, " + val + "\n"); 
                emit("    call print_integer\n\n");
            } 
            else if (tipo.equals("float")) {
                requierePrintFloat = true;
                agregarVariableBSS(val, false);
                emit("    ; print float " + val + "\n");
                emit("    mov rdi, " + val + "\n"); 
                emit("    call print_float\n\n");
            }
            return;
        }
        
        // MATCH PARA PRINT NORMAL (Strings comunes)
        m = PAT_PRINT.matcher(linea);
        if (m.matches()) {
            String val = m.group(1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                String textoLimpio = val.substring(1, val.length() - 1);
                String etiqueta = agregarCadenaData(textoLimpio);
                emit("    ; print " + val + "\n");
                emit("    mov rax, 1\n    mov rdi, 1\n");
                emit("    mov rsi, " + etiqueta + "\n");
                emit("    mov rdx, len_" + etiqueta + "\n");
                emit("    syscall\n\n");
            }
            return;
        }
        
        // LECTURA DE TECLADO (SOLO INT)
        m = PAT_SCAN_INT.matcher(linea);
        if (m.matches()) {
            String val = m.group(1).trim();
            agregarVariableBSS(val, false);
            requiereScanInt = true; 
            
            emit("    ; " + linea + "\n");
            emit("    lea rdi, [rel " + val + "]\n"); // Pasamos la direccion a la memoria.
            emit("    call read_integer\n\n");
            return;
        }

        // ASIGNACIÓN SIMPLE
        m = PAT_ASSIGN_SIMPLE.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String val = m.group(2).trim();
            agregarVariableBSS(dest, false);

            if (val.startsWith("\"") && val.endsWith("\"")) {
                String textoLimpio = val.substring(1, val.length() - 1);
                String etiqueta = agregarCadenaData(textoLimpio);
                emit("    mov rax, " + etiqueta + "\n");
                emit("    mov [" + dest + "], rax\n\n");
            } else {
                // Si el valor tiene un punto, es flotante y usamos __float64__
                if (val.contains(".")) {
                    emit("    mov rax, __float64__(" + val + ")\n");
                } else {
                    emit("    mov rax, " + (esNumero(val) ? val : "[" + val + "]") + "\n");
                }
                emit("    mov [" + dest + "], rax\n\n");
            }
            return;
        }
    }

    private void emitirComparacion(String instruccionSet, String op2) {
        emit("    cmp rax, " + (esNumero(op2) ? op2 : "[" + op2 + "]") + "\n");
        emit("    " + instruccionSet + " al\n");
        emit("    movzx rax, al\n");
    }

    private void agregarVariableBSS(String nombre, boolean esVector) {
        if (variablesDeclaradas.contains(nombre) || esNumero(nombre)) return;
        variablesDeclaradas.add(nombre);
        if (esVector) {
            bssSection.append("    ").append(nombre).append(" resq 256\n");
        } else {
            bssSection.append("    ").append(nombre).append(" resq 1\n");
        }
    }

    private String agregarCadenaData(String texto) {
        if (cadenasDeclaradas.containsKey(texto)) {
            return cadenasDeclaradas.get(texto);
        }
        String etiqueta = "str_" + (contadorCadenas++);
        cadenasDeclaradas.put(texto, etiqueta);
        dataSection.append("    ").append(etiqueta).append(" db \"").append(texto).append("\", 10\n");
        dataSection.append("    len_").append(etiqueta).append(" equ $ - ").append(etiqueta).append("\n");
        return etiqueta;
    }
    
    private boolean esNumero(String str) {
        if (str == null || str.isEmpty()) return false;
        // Evitar que confunda variables con números si tienen caracteres extraños
        if (!Character.isDigit(str.charAt(0)) && str.charAt(0) != '-') return false;
        try { 
            Double.parseDouble(str); 
            return true; 
        } catch (NumberFormatException e) { 
            return false; 
        }
    }
}