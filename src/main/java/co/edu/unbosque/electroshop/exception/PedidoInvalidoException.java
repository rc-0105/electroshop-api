package co.edu.unbosque.electroshop.exception;

/** El pedido es sintácticamente válido pero no se puede procesar (cliente o producto inexistente, etc.). */
public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
