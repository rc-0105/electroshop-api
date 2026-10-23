package co.edu.unbosque.electroshop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "orden_preparacion")
public class OrdenPreparacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false, length = 30)
    private String bodega;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false, length = 10)
    private String prioridad;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    protected OrdenPreparacion() {
    }

    public OrdenPreparacion(Pedido pedido, String bodega, String prioridad) {
        this.pedido = pedido;
        this.bodega = bodega;
        this.prioridad = prioridad;
        this.estado = "PENDIENTE";
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Pedido getPedido() { return pedido; }
    public String getBodega() { return bodega; }
    public String getEstado() { return estado; }
    public String getPrioridad() { return prioridad; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
