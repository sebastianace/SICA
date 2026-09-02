import com.zonaacme.sica.arranque.ContextoAplicacion;
public class PruebaManipulacion {
    public static void main(String[] a) {
        var r = new ContextoAplicacion().bitacora().verificarIntegridad();
        System.out.println((r.intacta() ? "INTACTA  " : "ROTA     ") + r.mensaje());
        System.exit(r.intacta() ? 0 : 1);
    }
}
