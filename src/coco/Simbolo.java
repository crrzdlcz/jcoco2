package coco;

public class Simbolo {
	public String nombre;
	public String tipo;
	public String ambito;
	public int lineaDeclaracion;
	public String tipoRetorno;
	public java.util.List<String> parametros;
	public String tipoElemento;
	
	// Método constructor:
	public Simbolo(String nombre, String tipo, String ambito, int linea) 
	{
		this.nombre = nombre;
		this.tipo = tipo;
		this.ambito = ambito;
		this.lineaDeclaracion = linea;
		this.parametros = new java.util.ArrayList<>();
		this.tipoElemento = null;
	}
	
	/*@Override
	public String toString() 
	{
        return "Simbolo{" +
               "nombre='" + nombre + '\'' +
               ", tipo='" + tipo + '\'' +
               ", ambito='" + ambito + '\'' +
               ", linea=" + lineaDeclaracion +
               '}';
    }*/
	
	
	@Override
	public String toString() 
	{
	    String tipoAImprimir = this.tipo;
	    
	    if ("funcion".equals(this.tipo) && this.tipoRetorno != null) {
	        tipoAImprimir = "funcion:" + this.tipoRetorno;
	    } 
	    
	    return "Simbolo{" +
	           "nombre='" + nombre + '\'' +
	           ", tipo='" + tipoAImprimir + '\'' +
	           ", ambito='" + ambito + '\'' +
	           ", linea=" + lineaDeclaracion +
	           '}';		
	
	}
}
