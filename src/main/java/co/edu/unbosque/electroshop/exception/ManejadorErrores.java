package co.edu.unbosque.electroshop.exception;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduce las excepciones a respuestas RFC 7807 (application/problem+json).
 * Los códigos importan para JMeter: solo 201/200 cuentan como éxito.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(PedidoInvalidoException.class)
    public ProblemDetail pedidoInvalido(PedidoInvalidoException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Pedido inválido", ex.getMessage());
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ProblemDetail stockInsuficiente(StockInsuficienteException ex) {
        return problema(HttpStatus.CONFLICT, "Stock insuficiente", ex.getMessage());
    }

    @ExceptionHandler(PagoRechazadoException.class)
    public ProblemDetail pagoRechazado(PagoRechazadoException ex) {
        return problema(HttpStatus.PAYMENT_REQUIRED, "Pago rechazado", ex.getMessage());
    }

    /** Espera de bloqueo agotada o interbloqueo detectado por MySQL. */
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockingFailureException.class})
    public ProblemDetail bloqueo(RuntimeException ex) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Inventario ocupado",
                "No fue posible reservar el inventario a tiempo; reintente.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", detalle);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setTitle(titulo);
        return pd;
    }
}
