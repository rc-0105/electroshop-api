package co.edu.unbosque.electroshop.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "direccion_envio", nullable = false, length = 200)
    private String direccionEnvio;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePedido> detalles = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(Cliente cliente, String direccionEnvio) {
        this.cliente = cliente;
        this.direccionEnvio = direccionEnvio;
        this.fecha = LocalDateTime.now();
        this.estado = EstadoPedido.PENDIENTE_PAGO;
    }

    public void agregarItem(Producto producto, int cantidad) {
        DetallePedido detalle = new DetallePedido(this, producto, cantidad);
        detalles.add(detalle);
        total = total.add(detalle.getSubtotal());
    }

    public void confirmar() {
        this.estado = EstadoPedido.CONFIRMADO;
    }

    public void cancelar() {
        this.estado = EstadoPedido.CANCELADO;
    }

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public LocalDateTime getFecha() { return fecha; }
    public EstadoPedido getEstado() { return estado; }
    public BigDecimal getTotal() { return total; }
    public String getDireccionEnvio() { return direccionEnvio; }
    public List<DetallePedido> getDetalles() { return detalles; }
}
