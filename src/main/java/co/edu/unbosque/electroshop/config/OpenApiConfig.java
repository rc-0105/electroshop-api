package co.edu.unbosque.electroshop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Contrato OpenAPI. Disponible en:
 *   http://localhost:8080/swagger-ui.html   (interfaz)
 *   http://localhost:8080/v3/api-docs       (JSON)
 *   http://localhost:8080/v3/api-docs.yaml  (YAML, para anexar al informe)
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI electroShopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ElectroShop · API de pedidos")
                        .version("1.0.0")
                        .description("""
                                API de procesamiento de pedidos para ElectroShop.

                                | Endpoint | Requisito |
                                |---|---|
                                | `POST /api/pedidos/procesar` | p95 ≤ 100 ms con 1.500 pedidos/min |
                                | `GET /api/pedidos/detalle/{id}` | p95 ≤ 50 ms |
                                """)
                        .contact(new Contact().name("Equipo · Ingeniería de Software 2 · Universidad El Bosque")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local")));
    }
}
