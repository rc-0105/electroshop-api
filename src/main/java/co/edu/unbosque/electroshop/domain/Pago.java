package co.edu.unbosque.electroshop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false, length = 40)
    private String referencia;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Pago() {
    }

    public Pago(Pedido pedido, BigDecimal monto, MetodoPago metodo, String estado, String referencia) {
        this.pedido = pedido;
        this.monto = monto;
        this.metodo = metodo;
        this.estado = estado;
        this.referencia = referencia;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public BigDecimal getMonto() { return monto; }
    public MetodoPago getMetodo() { return metodo; }
    public String getEstado() { return estado; }
    public String getReferencia() { return referencia; }
    public LocalDateTime getFecha() { return fecha; }
}
