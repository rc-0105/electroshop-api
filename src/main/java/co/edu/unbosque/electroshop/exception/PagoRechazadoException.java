package co.edu.unbosque.electroshop.exception;

public class PagoRechazadoException extends RuntimeException {

    public PagoRechazadoException(String motivo) {
        super("Pago rechazado: " + motivo);
    }
}
