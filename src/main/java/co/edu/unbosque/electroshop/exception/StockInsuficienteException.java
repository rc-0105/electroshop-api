package co.edu.unbosque.electroshop.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String sku, int disponible, int solicitado) {
        super("Stock insuficiente para " + sku + ": disponible " + disponible + ", solicitado " + solicitado);
    }
}
