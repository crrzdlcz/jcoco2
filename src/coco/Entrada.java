package coco;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;


/*-- Clase que define como agarrar la ruta del archivo a analizar. --*/
public class Entrada 
{
  public static InputStream obtenerRuta (String ruta) throws FileNotFoundException 
  {
    return new FileInputStream(ruta);
  }
}
