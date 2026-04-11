package coco;

import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.List;
import javacc.GramaticaCoco;
import javacc.ParseException;
import javacc.TokenMgrError;

public class Main {

  public static void main(String[] args) {
    Mensaje msj = new Mensaje();
    if (args.length == 0) {
        msj.adv("Se requiere la ruta del código fuente");
        System.exit(1);
    }
    
    String rutaArchivo = args[0];
    GramaticaCoco parser = null;
    Arbol arbol = null;

    try {
        InputStream is = Entrada.obtenerRuta(rutaArchivo);
        msj.msjBienvenida("0");
        parser = new GramaticaCoco(is);
        
        try {
            arbol = parser.Coco();
        } catch (TokenMgrError | ParseException e) {
            GramaticaCoco.registrarError("Error Crítico (Léxico/Sintáctico): " + e.getMessage());
        }

    } catch (FileNotFoundException e) {
        msj.error("Archivo no encontrado: " + e.getMessage());
        return; 
    } catch (Exception e) {
        msj.error("Error inesperado: " + e.getMessage());
    } finally {
        if (parser != null) {
            List<String> errores = parser.getListaErrores();
            
            if (errores.isEmpty()) {
            	System.out.println("");
                msj.ok("ANÁLISIS COMPLETADO EXITOSAMENTE.\n");
            } else {
            	System.out.println("");
                msj.error( Colores.UNDERLINE + "ANÁLISIS FINALIZADO: Se encontraron errores (#" + 
                		errores.size() + ").\n" + Colores.RESET );
                
                guardarErrores(errores, rutaArchivo + " - ERRORES.txt");
            }

            guardarTabla(Tabla.getTabla(), rutaArchivo + " - SIMBOLOS.txt");
            guardarPila(parser.getPilaSemantica(), rutaArchivo + " - PILA.txt");
            
            if (arbol != null) {
                guardarArbol(arbol, rutaArchivo + " - ARBOL.txt");
                guardarTAC(arbol, rutaArchivo + " - TAC.txt");
                
                if (errores.isEmpty())
                {
                	String rutaASM = rutaArchivo + ".asm";
                	guardarNASM(rutaArchivo +" - TAC.txt", rutaASM);
                }
            }
            
            
            
            
        }
    }
  }
  
  
  private static void guardarNASM(String rutaArchivoTAC, String rutaSalidaASM)
  {
      Mensaje msj = new Mensaje();
      
      try 
      {
          GeneradorNASM gen = new GeneradorNASM();
          
          gen.generarASM(rutaArchivoTAC, rutaSalidaASM);
          
          msj.ok("CÓDIGO NASM GENERADO EN: \n  " + rutaSalidaASM + "\n");
      }
      catch (Exception e)
      {
          msj.error("Error al generar código ensamblador: " + e.getMessage());
      }
  }
  
  private static void guardarTAC(Arbol arbol, String rutaSalida) 
  {
    Mensaje msj = new Mensaje();
    if (arbol == null) return;

    try (PrintStream archivoTAC = new PrintStream(rutaSalida)) 
    {
        GeneradorTAC gen = new GeneradorTAC();
        gen.generar(arbol); // Recorre el árbol y llena la lista de instrucciones
        
        archivoTAC.println("--- CODIGO INTERMEDIO (TAC) ---");
        
        for (Instruccion inst : gen.getCodigo()) {
            archivoTAC.println(inst.toString());
        }
        
        msj.ok("CÓDIGO INTERMEDIO GENERADO EN: \n  " + rutaSalida + "\n");
    } 
    catch (Exception e) 
    {
        msj.error("Error al generar código intermedio: " + e.getMessage());
    }
  }
  
  private static void guardarPila(coco.Pila pila, String rutaSalida) 
  {
      Mensaje msj = new Mensaje();
      try (PrintStream archivoPila = new PrintStream(rutaSalida)) 
      {
          pila.imprimirHistorial(archivoPila);
          msj.ok("HISTORIAL DE PILA GENERADA EN: \n  " + rutaSalida + "\n");
      } 
      catch (Exception e) 
      {
          msj.error("Error al generar archivo de pila: " + e.getMessage());
      }
  }

  private static void guardarErrores(List<String> errores, String rutaSalida) 
  {
      Mensaje msj = new Mensaje();
      try (PrintStream archivo = new PrintStream(rutaSalida)) {
          archivo.println("--- DETALLE DE ERRORES ENCONTRADOS ---");
          for (String error : errores) {
              archivo.println("- " + error);
          }
          msj.ok("DETALLE DE ERRORES GENERADO EN: \n  " + rutaSalida + "\n");
      } catch (FileNotFoundException e) {
          msj.error("No se pudo crear el archivo de errores: " + e.getMessage());
      }
  }
  
  private static void guardarTabla(coco.Tabla tabla, String rutaSalida) 
  {
      Mensaje msj = new Mensaje();
      try (PrintStream archivoSalida = new PrintStream(rutaSalida)) 
      {
          tabla.imprimirTabla(archivoSalida);
          msj.ok("TABLA DE SIMBOLOS GENERADA EN: \n  " + rutaSalida + "\n");
      } 
      catch (Exception e) 
      {
          msj.error("Error al generar tabla de símbolos: " + e.getMessage());
      }
  }
  
  private static void guardarArbol(Arbol arbol, String rutaSalida) 
  {
    Mensaje msj = new Mensaje();
    if (arbol == null) return;
    PrintStream consolaOriginal = System.out;
    
    try (PrintStream archivoArbol = new PrintStream(rutaSalida)) 
    {
      System.setOut(archivoArbol);
      arbol.imprimirArbol(0);
      System.setOut(consolaOriginal);
      msj.ok("ARBOL GENERADO EN: \n  " + rutaSalida + "\n");
    } 
    catch (Exception e) 
    {
      System.setOut(consolaOriginal);
      msj.error("Error al generar el árbol: " + e.getMessage());
	}
  }
}