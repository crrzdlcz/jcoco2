package coco;
import static java.lang.System.out;
import static coco.Colores.*;

public class Mensaje 
{
  public Mensaje() 
  {}	
  
  public void msjBienvenida(String msj)
  {
	out.print(Limpiar_Pantalla);
    out.println(VERDE_NEGRITA + "\n\t     jCoCo");
    out.println("  ===========================");
  }
  
  public void ok(String msj) 
  {
    out.println(VERDE_NEGRITA + "󰗠 " +  msj);
  }
  
  public void error(String msj) 
  {
    out.println(ROJO_NEGRITA + "  " +  msj);
  }
  
  public void adv(String msj) 
  {
    out.println(MORADO + " " +  msj);
  }
  
  /*public static void main(String[] args) {
	Mensaje msj = new Mensaje();
	msj.msjBienvenida("o");
	msj.ok("Analisis terminado exitosamente");
  }*/
}
