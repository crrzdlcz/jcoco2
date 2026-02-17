package coco;

import java.io.InputStream;
import java.io.FileNotFoundException;
import static java.lang.System.err;
import static java.lang.System.out;
import javacc.GramaticaCoco;
import javacc.ParseException;
import javacc.TokenMgrError;

public class Main {

  public static void main(String[] args) {
    Mensaje msj = new Mensaje();
    
    if (args.length == 0) 
    {
    	msj.adv("Se requiere la ruta del código fuente");
    	System.exit(1);
    }
    
    String rutaArchivo = args[0];
    try 
    {
    	InputStream is = Entrada.obtenerRuta(rutaArchivo);
    	msj.msjBienvenida("0");
    	GramaticaCoco parser = new GramaticaCoco(is);
    	Arbol arbol = parser.Coco();
    	
        /*-- TABLA DE SIMBOLOS --*/
    	coco.Tabla tablaSimbolos = Tabla.getTabla();
    	String salidaTabla = rutaArchivo + " - SIMBOLOS.txt";
    	guardarTabla(tablaSimbolos, salidaTabla);
    	
        /*-- ARBOL --*/
    	if (arbol != null)
    	{
    		String salidaArbol = rutaArchivo + " - ARBOL.txt";
    		guardarArbol(arbol, salidaArbol);
    	}
    	msj.ok("ANALISIS TERMINADO EXITOSAMENTE");
    } 
    catch (FileNotFoundException e)
    {
    	msj.error(e.getMessage());
    	System.exit(1);
    }
    catch (TokenMgrError e) // Manejo de errores lexicos.
    {
		String emensaje = e.getMessage();
		String[] lineas = emensaje.split("\n");
		msj.error("*-- ERROR LEXICO --*");
		for (int i = 0; i < lineas.length; i++) 
	    {
			err.println(" " + lineas[i].trim());
	    }
		err.println("  *-- ANALISIS TERMINADO CON ERRORES --*");
		System.exit(1);
	}
    catch (ParseException e) // Manejo de errores sintacticos
    {
    	String emensaje = e.getMessage();
    	String[] lineas = emensaje.split("\n");
    	msj.adv("*-- ERROR SINTACTICO --*");
        for (int i = 0; i < lineas.length; i++) 
        {
            err.println(" " + lineas[i].trim());
        }
        err.println("  *-- ANALISIS TERMINADO CON ERRORES --*");
    	System.exit(1);
	}
    catch (Exception e) 
    {
		out.println(Colores.AZUL + Colores.UNDERLINE + "Error inesperado " + e.getMessage());
	}
  }
  
  
  private static void guardarTabla(coco.Tabla tabla, String rutaSalida) 
  {
	  Mensaje msj = new Mensaje();
	  java.io.PrintStream consolaOriginal = System.out;
      try 
      {
    	  java.io.PrintStream archivoSalida = new java.io.PrintStream(rutaSalida);
          tabla.imprimirTabla(archivoSalida);
          archivoSalida.close();
          System.setOut(consolaOriginal);
          msj.ok("TABLA DE SIMBOLOS GENERADA EN: \n  " + rutaSalida + "\n");
      } 
      catch (FileNotFoundException e) 
      {
          System.setOut(consolaOriginal); 
          msj.error("NO SE GENERO LA TABLA DE SIMBOLOS: \n  " + e.getMessage() + "\n");
          System.exit(1);
      } 
      catch (Exception e) 
      {
          System.setOut(consolaOriginal); 
          throw e;
      }
  }
  
  private static void guardarArbol(Arbol arbol, String rutaSalida) 
  {
    Mensaje msj = new Mensaje();
    if (arbol == null) return;        
    java.io.PrintStream consolaOriginal = System.out;
    try 
    {
      System.setOut(new java.io.PrintStream(rutaSalida));
      arbol.imprimirArbol(0);
      System.out.flush();
      System.setOut(consolaOriginal);
      msj.ok("ARBOL GENERADO EN: \n  " + rutaSalida + "\n");
    } 
    catch (FileNotFoundException e) 
    {
      System.setOut(consolaOriginal);
      msj.error("NO SE GENERO LA TABLA DE SIMBOLOS: \n  " + e.getMessage() + "\n");

    } 
    catch (Exception e) 
    {
            System.setOut(consolaOriginal);
            throw e;
    }
  }
}

