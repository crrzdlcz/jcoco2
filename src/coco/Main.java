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
    
    String nombreBase = obtenerNombreBase(rutaArchivo);
    
    // Para limpiar las extenciones de los archivos //
    String rutaArbol   = nombreBase + " - ARBOL.txt";
    String rutaPila    = nombreBase + " - PILA.txt";
    String rutaTabla   = nombreBase + " - SIMBOLOS.txt";
    String rutaTAC     = nombreBase + " - TAC.txt";
    String rutaErrores = nombreBase + " - ERRORES.txt";
    //String rutaASM     = nombreBase + ".asm";
    String rutaCPP     = nombreBase + ".cpp";
    
    GramaticaCoco parser = null;
    Arbol arbol = null;
    
    
    // Para el contador:
    Contador tiempoTotal = new Contador();
    tiempoTotal.iniciar();

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
                
                guardarErrores(errores, rutaErrores);
            }

            guardarTabla(Tabla.getTabla(), rutaTabla);
            guardarPila(parser.getPilaSemantica(), rutaPila);
            
            if (arbol != null) {
                guardarArbol(arbol, rutaArbol);
                guardarTAC(arbol, rutaTAC);
                
                // Para el NASM OLD
                /*if (errores.isEmpty())
                {
                	String rutaASM = rutaArchivo + ".asm";
                	guardarNASM(rutaArchivo +" - TAC.txt", rutaASM);
                }/*/
                
                if (errores.isEmpty()) {
                	// == ENERACION DE CODIGO C++ == //
                	guardarCPP(rutaTAC, rutaCPP);
                }
            }
        }
        
        tiempoTotal.detener();
        //msj.adv("TIEMPO DE EJECUCIÓN: " + tiempoTotal.getTiempoFormateado());
        System.out.println( Colores.AZUL_NEGRITA + "[DEBUG] TIEMPO DE EJECUCIÓN: " + tiempoTotal.getTiempoFormateado());
    }
  }
  
  
//  private static void guardarNASM(String rutaArchivoTAC, String rutaSalidaASM)
//  {
//      Mensaje msj = new Mensaje();
//      
//      try 
//      {
//          GeneradorNASM gen = new GeneradorNASM();
//          
//          gen.generarASM(rutaArchivoTAC, rutaSalidaASM);
//          
//          msj.ok("CÓDIGO NASM GENERADO EN: \n  " + rutaSalidaASM + "\n");
//      }
//      catch (Exception e)
//      {
//          msj.error("Error al generar código ensamblador: " + e.getMessage());
//      }
//  }
  
  // Auxiliar para generar el c++.
  private static void guardarCPP(String rutaArchivoTAC, String rutaSalidaCPP) 
  {
      Mensaje msj = new Mensaje();
      
      try 
      {
          GeneradorCPP gen = new GeneradorCPP();
          gen.generarCPP(rutaArchivoTAC, rutaSalidaCPP);
          
          msj.ok("CÓDIGO C++ GENERADO EN: \n  " + rutaSalidaCPP + "\n");
      } 
      catch (Exception e) 
      {
          msj.error("Error al generar código C++: " + e.getMessage());
      }
  }
  
  private static void guardarTAC(Arbol arbol, String rutaSalida) 
  {
    Mensaje msj = new Mensaje();
    if (arbol == null) return;

    try (PrintStream archivoTAC = new PrintStream(rutaSalida)) 
    {
        GeneradorTAC gen = new GeneradorTAC();
        gen.generar(arbol); 
       
        
        // == == == LLAMADAS A LAS OPTIMIZACIONES == == == == //
        gen.optimizarCodigoMuerto();
        msj.ok("CÓDIGO MUERTO ELIMINADO" + "\n");
        gen.optimizarSaltos(); 
               
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
  
  private static String obtenerNombreBase(String ruta) {
	    // Obtener solo el nombre del archivo (sin la ruta)
	    String nombre = ruta;
	    int lastSep = Math.max(ruta.lastIndexOf('/'), ruta.lastIndexOf('\\'));
	    if (lastSep >= 0) {
	        nombre = ruta.substring(lastSep + 1);
	    }
	    
	    // Quitar la extensión (.txt, .coco, etc.)
	    int punto = nombre.lastIndexOf('.');
	    if (punto > 0) {
	        nombre = nombre.substring(0, punto);
	    }
	    
	    return nombre;
	}
  
}