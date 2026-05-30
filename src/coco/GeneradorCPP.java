package coco;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneradorCPP {

    private StringBuilder codigoCPP = new StringBuilder();
    private Set<String> variablesInt = new LinkedHashSet<>();
    private Set<String> variablesFloat = new LinkedHashSet<>();
    private Set<String> variablesString = new LinkedHashSet<>();
    private Set<String> variablesVector = new LinkedHashSet<>();
    private Set<String> funcionesConRetorno = new HashSet<>(); 
    
    private boolean requiereIostream = false;
    private boolean requiereString = false;
    private boolean insideFunction = false;
    private boolean mainCodeStarted = false;
    private String currentFunctionName = "";

    private final Pattern PAT_ASSIGN_ARR = Pattern.compile("^(\\w+)\\[(.+)\\]\\s*=\\s*(.+)$");
    private final Pattern PAT_READ_ARR = Pattern.compile("^(\\w+)\\s*=\\s*(\\w+)\\[(.+)\\]$");
    private final Pattern PAT_OP_BIN = Pattern.compile("^(\\w+)\\s*=\\s*(.+)\\s+(f\\+|f\\-|f\\*|f\\/|%|==|!=|>=|<=|<|>|&&|\\|\\||\\+|\\-|\\*|\\/)\\s+(.+)$");
    private final Pattern PAT_ASSIGN_SIMPLE = Pattern.compile("^(\\w+)\\s*=\\s*(.+)$");
    private final Pattern PAT_PRINT = Pattern.compile("^print\\s+(.+)$");
    private final Pattern PAT_PRINT_TIPO = Pattern.compile("^print_(int|float)\\s+(.+)$");
    private final Pattern PAT_PARAM = Pattern.compile("^param\\s+(.+)$");
    private final Pattern PAT_CALL = Pattern.compile("^(\\w+)\\s*=\\s*call\\s+(\\w+)\\s*,\\s*(\\d+)$");
    private final Pattern PAT_CALL_VOID = Pattern.compile("^call\\s+(\\w+)\\s*,\\s*(\\d+)$");
    private final Pattern PAT_IF_FALSE = Pattern.compile("^if_false\\s+(\\w+)\\s+goto\\s+(\\w+)$");
    private final Pattern PAT_GOTO = Pattern.compile("^goto\\s+(\\w+)$");
    private final Pattern PAT_RETURN = Pattern.compile("^return\\s*(.*)$");
    private final Pattern PAT_SCAN_INT = Pattern.compile("^scan_int\\s+(.+)$");
    private final Pattern PAT_SCAN_STRING = Pattern.compile("^scan_string\\s+(.+)$");
    private final Pattern PAT_LABEL = Pattern.compile("^(\\w+):$");

    private final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "cout", "cin", "endl", "string", "int", "float", "bool", "if", "else",
        "while", "for", "return", "goto", "true", "false", "null", "fixed", "precision"
    ));

    public void generarCPP(String rutaTAC, String rutaSalidaCPP) {
        try {
            List<String> lineasTAC = Files.readAllLines(Paths.get(rutaTAC), StandardCharsets.UTF_8);

            // PRIMER PASO: Detectar variables
            for (String linea : lineasTAC) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("---")) continue;
                detectarVariables(linea);
            }

            // SEGUNDO PASO: Generar código
            for (String linea : lineasTAC) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("---")) continue;
                procesarLineaTAC(linea);
            }

            if (mainCodeStarted) {
                codigoCPP.append("}\n");
            }

            try (FileWriter fw = new FileWriter(rutaSalidaCPP)) {
                fw.write("#include <iostream>\n");
                if (requiereString) fw.write("#include <string>\n");
                fw.write("using namespace std;\n\n");

                if (!variablesInt.isEmpty() || !variablesFloat.isEmpty() || 
                    !variablesString.isEmpty() || !variablesVector.isEmpty()) {
                    fw.write("// Variables globales\n");
                    for (String v : variablesInt) fw.write("long long " + v + " = 0;\n");
                    for (String v : variablesFloat) fw.write("double " + v + " = 0.0;\n");
                    for (String v : variablesString) fw.write("string " + v + " = \"\";\n");
                    for (String v : variablesVector) fw.write("long long " + v + "[256] = {0};\n");
                    fw.write("\n");
                }

                fw.write(codigoCPP.toString());

                fw.write("\nint main() {\n");
                if (mainCodeStarted) fw.write("    _start();\n");
                fw.write("    return 0;\n");
                fw.write("}\n");
            }

        } catch (IOException e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private void iniciarMainSiEsNecesario() {
        if (!insideFunction && !mainCodeStarted) {
            codigoCPP.append("void _start() {\n");
            mainCodeStarted = true;
            currentFunctionName = "_start";
        }
    }

    private void procesarLineaTAC(String linea) {
        Matcher m;

        m = PAT_LABEL.matcher(linea);
        if (m.matches()) {
            String label = m.group(1);
            if (label.matches("^L\\d+$")) {
                iniciarMainSiEsNecesario();
                codigoCPP.append(label).append(": ;\n");
            } else {
                insideFunction = true;
                currentFunctionName = label;
                if (funcionesConRetorno.contains(label)) {
                    codigoCPP.append("long long ").append(label).append("() {\n");
                } else {
                    codigoCPP.append("void ").append(label).append("() {\n");
                }
            }
            return;
        }

        m = PAT_ASSIGN_ARR.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            codigoCPP.append("    ").append(m.group(1)).append("[").append(formatearValor(m.group(2))).append("] = ").append(formatearValor(m.group(3))).append(";\n");
            return;
        }

        m = PAT_READ_ARR.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            codigoCPP.append("    ").append(m.group(1)).append(" = ").append(m.group(2)).append("[").append(formatearValor(m.group(3))).append("];\n");
            return;
        }

        m = PAT_OP_BIN.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            String opCpp = m.group(3).replace("f", "");
            codigoCPP.append("    ").append(m.group(1)).append(" = ").append(formatearValor(m.group(2))).append(" ").append(opCpp).append(" ").append(formatearValor(m.group(4))).append(";\n");
            return;
        }

        if (PAT_PARAM.matcher(linea).matches()) return;

        // ==========================================
        // MECANISMO DE EMERGENCIA (AUTO-FIX)
        // ==========================================
        m = PAT_CALL.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            String funcName = m.group(2);
            
            // Si la función ya se declaró como void pero ahora vemos que devuelve algo, la corregimos en el StringBuilder:
            if (!funcionesConRetorno.contains(funcName)) {
                funcionesConRetorno.add(funcName);
                String target = "void " + funcName + "() {";
                String replacement = "long long " + funcName + "() {";
                int pos = codigoCPP.indexOf(target);
                if (pos != -1) {
                    codigoCPP.replace(pos, pos + target.length(), replacement);
                }
            }
            
            codigoCPP.append("    ").append(m.group(1)).append(" = ").append(funcName).append("();\n");
            return;
        }

        m = PAT_CALL_VOID.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            codigoCPP.append("    ").append(m.group(1)).append("();\n");
            return;
        }

        m = PAT_IF_FALSE.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            codigoCPP.append("    if (!").append(m.group(1)).append(") goto ").append(m.group(2)).append(";\n");
            return;
        }

        m = PAT_GOTO.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            codigoCPP.append("    goto ").append(m.group(1)).append(";\n");
            return;
        }
        
        // Return
        m = PAT_RETURN.matcher(linea);
        if (m.matches()) {
            String val = m.group(1).trim();
            
            // Si el TAC devuelve un valor explícito, la función DEBE ser long long
            if (!val.isEmpty()) {
                // 1. Forzar registro como función con retorno
                if (!funcionesConRetorno.contains(currentFunctionName)) {
                    funcionesConRetorno.add(currentFunctionName);
                    // 2. Auto-fix: cambiar "void" por "long long" si ya se escribió arriba
                    String target = "void " + currentFunctionName + "() {";
                    String replacement = "long long " + currentFunctionName + "() {";
                    int pos = codigoCPP.indexOf(target);
                    if (pos != -1) {
                        codigoCPP.replace(pos, pos + target.length(), replacement);
                    }
                }
                codigoCPP.append("    return ").append(formatearValor(val)).append(";\n");
            } else {
                // Si es un return vacío, revisar si ya fue forzada a ser long long
                if (funcionesConRetorno.contains(currentFunctionName)) {
                    codigoCPP.append("    return 0;\n");
                } else {
                    codigoCPP.append("    return;\n");
                }
            }
            
            if (insideFunction) {
                codigoCPP.append("}\n");
                insideFunction = false;
                currentFunctionName = "";
            }
            return;
        }

        m = PAT_PRINT_TIPO.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            requiereIostream = true;
            if (m.group(1).equals("int")) {
                codigoCPP.append("    cout << ").append(formatearValor(m.group(2).trim())).append(" << endl;\n");
            } else {
                codigoCPP.append("    cout << fixed; cout.precision(2); cout << ").append(formatearValor(m.group(2).trim())).append(" << endl;\n");
            }
            return;
        }

        m = PAT_PRINT.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            requiereIostream = true;
            requiereString = true;
            codigoCPP.append("    cout << ").append(formatearValor(m.group(1).trim())).append(" << endl;\n");
            return;
        }

        m = PAT_SCAN_INT.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            requiereIostream = true;
            codigoCPP.append("    cin >> ").append(m.group(1).trim()).append(";\n");
            return;
        }
        
        m = PAT_SCAN_STRING.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            requiereIostream = true;
            requiereString = true;
            codigoCPP.append("    getline(cin, ").append(m.group(1).trim()).append(");\n");
            return;
        }

        m = PAT_ASSIGN_SIMPLE.matcher(linea);
        if (m.matches()) {
            iniciarMainSiEsNecesario();
            String val = m.group(2).trim();
            if (val.startsWith("\"")) requiereString = true;
            codigoCPP.append("    ").append(m.group(1)).append(" = ").append(formatearValor(val)).append(";\n");
            return;
        }
    }

    private void detectarVariables(String linea) {
        Matcher m;

        m = PAT_ASSIGN_ARR.matcher(linea);
        if (m.matches()) {
            if (!KEYWORDS.contains(m.group(1))) variablesVector.add(m.group(1));
            detectarEnExpresion(m.group(2));
            detectarEnExpresion(m.group(3));
            return;
        }

        m = PAT_READ_ARR.matcher(linea);
        if (m.matches()) {
            if (!KEYWORDS.contains(m.group(1))) variablesInt.add(m.group(1));
            if (!KEYWORDS.contains(m.group(2))) variablesVector.add(m.group(2));
            detectarEnExpresion(m.group(3));
            return;
        }

        m = PAT_OP_BIN.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            if (m.group(3).startsWith("f")) {
                marcarComoFloat(dest); 
                marcarComoFloat(m.group(2)); 
                marcarComoFloat(m.group(4));
            } else {
                detectarEnExpresion(dest); 
                detectarEnExpresion(m.group(2));
                detectarEnExpresion(m.group(4));
            }
            return;
        }

        m = PAT_ASSIGN_SIMPLE.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String val = m.group(2).trim();
            if (!KEYWORDS.contains(dest)) {
                if (val.startsWith("\"")) variablesString.add(dest);
                else if (val.contains(".")) variablesFloat.add(dest);
                else variablesInt.add(dest);
            }
            detectarEnExpresion(val);
            return;
        }

        m = PAT_SCAN_INT.matcher(linea);
        if (m.matches()) {
            if (!KEYWORDS.contains(m.group(1).trim())) variablesInt.add(m.group(1).trim());
            return;
        }
        
        m = PAT_SCAN_STRING.matcher(linea);
        if (m.matches()) {
            String var = m.group(1).trim();
            if (!KEYWORDS.contains(var)) {
                variablesInt.remove(var);
                variablesString.add(var);
            }
            return;
        }

        m = PAT_PRINT_TIPO.matcher(linea);
        if (m.matches()) {
            if (m.group(1).equals("float")) marcarComoFloat(m.group(2).trim());
            else detectarEnExpresion(m.group(2).trim());
            return;
        }

        m = PAT_CALL.matcher(linea);
        if (m.matches()) {
            String dest = m.group(1);
            String funcName = m.group(2);
            if (!KEYWORDS.contains(dest)) variablesInt.add(dest);
            funcionesConRetorno.add(funcName); 
            return;
        }
    }

    private void marcarComoFloat(String var) {
        var = var.trim();
        if (KEYWORDS.contains(var)) return;
        variablesInt.remove(var);
        variablesFloat.add(var);
    }

    private void detectarEnExpresion(String expr) {
        if (expr == null) return;
        expr = expr.trim();
        if (expr.matches("-?\\d+\\.?\\d*") || expr.startsWith("\"")) return;
        if (expr.contains(".")) { marcarComoFloat(expr); return; }
        if (expr.matches("\\w+") && !KEYWORDS.contains(expr)) {
            variablesInt.add(expr);
        }
    }

    private String formatearValor(String val) {
        if (val.contains(".") && !val.startsWith("\"")) return val;
        if (val.startsWith("\"") && val.endsWith("\"")) return "string(" + val + ")";
        return val;
    }
}