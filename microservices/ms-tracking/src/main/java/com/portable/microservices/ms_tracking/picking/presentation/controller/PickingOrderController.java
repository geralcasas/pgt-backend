package com.portable.microservices.ms_tracking.picking.presentation.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portable.microservices.ms_tracking.picking.domain.model.RutaPick;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.AsignarOrdenPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.CompletarOrdenPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.CrearOrdenPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.ListarOrdenesPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.ObtenerOrdenPickPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.in.OptimizarRutaPortIn;
import com.portable.microservices.ms_tracking.picking.domain.ports.out.OrdenPickPersistencePortOut;
import com.portable.microservices.ms_tracking.picking.domain.ports.out.RutaPickPersistencePortOut;
import com.portable.microservices.ms_tracking.picking.infrastructure.client.InventoryFeignClient;
import com.portable.microservices.ms_tracking.picking.infrastructure.client.dto.LocationResponse;
import com.portable.microservices.ms_tracking.picking.infrastructure.client.dto.SalidaBatchFeignRequest;
import com.portable.microservices.ms_tracking.picking.presentation.dto.CrearOrdenDesdeSalidaRequest;
import com.portable.microservices.ms_tracking.picking.presentation.dto.CrearOrdenPickRequest;
import com.portable.microservices.ms_tracking.picking.domain.model.DetallePick;
import com.portable.microservices.ms_tracking.picking.presentation.dto.DetallePickResponse;
import com.portable.microservices.ms_tracking.picking.presentation.dto.OrdenPickResponse;
import com.portable.microservices.ms_tracking.picking.presentation.dto.RutaNodeResponse;
import com.portable.microservices.ms_tracking.picking.presentation.dto.RutaResponse;
import com.portable.microservices.ms_tracking.picking.presentation.mapper.OrdenPickWebMapper;
import com.portable.microservices.ms_tracking.shared.infrastructure.presentation.PagedResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/picking/orders")
@RequiredArgsConstructor
public class PickingOrderController {
    private final CrearOrdenPickPortIn crearOrdenPick;
    private final AsignarOrdenPickPortIn asignarOrdenPick;
    private final CompletarOrdenPickPortIn completarOrdenPick;
    private final OptimizarRutaPortIn optimizarRuta;
    private final ObtenerOrdenPickPortIn obtenerOrdenPick;
    private final ListarOrdenesPickPortIn listarOrdenesPick;
    private final OrdenPickWebMapper mapper;
    private final RutaPickPersistencePortOut rutaPickPersistence;
    private final OrdenPickPersistencePortOut ordenPickPersistence;
    private final InventoryFeignClient inventoryFeignClient;
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearOrdenPickRequest request) {
        var domain = mapper.toDomain(request.getItems());
        var orden = crearOrdenPick.execute(request.getUsuarioCreador(), domain, request.getTipoSalida(), request.getDocRef());
        var response = mapper.toResponse(orden);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orden de picking creada exitosamente",
                "data", response
        ));
    }

    @PostMapping("/from-salida")
    public ResponseEntity<Map<String, Object>> crearDesdeSalida(@Valid @RequestBody CrearOrdenDesdeSalidaRequest request) {
        var feignRequest = new SalidaBatchFeignRequest();
        feignRequest.setMotivo(request.getMotivo());
        feignRequest.setDocumentoRef(request.getDocRef());
        feignRequest.setItems(request.getItems().stream()
                .map(i -> {
                    var item = new SalidaBatchFeignRequest.SalidaBatchItem();
                    item.setIdLote(i.getIdLote());
                    item.setCantidad(i.getCantidad());
                    return item;
                })
                .toList());

        inventoryFeignClient.registrarSalidaBatch(feignRequest);

        var detalles = request.getItems().stream()
                .map(i -> DetallePick.builder()
                        .productoId(i.getProductoId())
                        .locacionId(i.getLocacionId())
                        .cantRequerida(i.getCantidad())
                        .cantSeleccion(0)
                        .estado("PENDIENTE")
                        .build())
                .toList();

        var orden = crearOrdenPick.execute(request.getUsuarioCreador(), detalles, "SALIDA", request.getDocRef());
        var response = mapper.toResponse(orden);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orden de picking creada desde salida",
                "data", response));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanioPagina) {
        PagedResponse<OrdenPickResponse> result;
        if (estado != null) {
            var page = listarOrdenesPick.executeByEstado(estado, pagina, tamanioPagina);
            result = new PagedResponse<>(
                    page.items().stream().map(mapper::toResponse).toList(),
                    page.total(), page.page(), page.pageSize());
        } else {
            var page = listarOrdenesPick.execute(pagina, tamanioPagina);
            result = new PagedResponse<>(
                    page.items().stream().map(mapper::toResponse).toList(),
                    page.total(), page.page(), page.pageSize());
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lista de órdenes obtenida",
                "data", result
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable UUID id) {
        var orden = obtenerOrdenPick.execute(id);
        var response = mapper.toResponse(orden);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orden encontrada",
                "data", response
        ));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<Map<String, Object>> optimizar(@PathVariable UUID id) {
        var ruta = optimizarRuta.execute(id);
        var response = buildRutaResponse(id, ruta);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ruta optimizada calculada",
                "data", response
        ));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<Map<String, Object>> asignar(
            @PathVariable UUID id,
            @RequestBody Map<String, Long> body) {
        var orden = asignarOrdenPick.execute(id, body.get("usuarioPickingId"));
        var response = mapper.toResponse(orden);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Operario asignado exitosamente",
                "data", response
        ));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completar(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        Long usuarioId = Long.valueOf(body.get("usuarioId").toString());
        String ipOrigen = (String) body.getOrDefault("ipOrigen", "0.0.0.0");
        var orden = completarOrdenPick.execute(id, usuarioId, ipOrigen);
        var response = mapper.toResponse(orden);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orden completada exitosamente",
                "data", response
        ));
    }

    @GetMapping("/{id}/route")
    public ResponseEntity<Map<String, Object>> obtenerRuta(@PathVariable UUID id) {
        var rutaOpt = rutaPickPersistence.findByIdOrden(id);
        if (rutaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var ruta = rutaOpt.get();
        var response = buildRutaResponse(id, ruta);
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Ruta obtenida");
        body.put("data", response);
        return ResponseEntity.ok(body);
    }

    private RutaResponse buildRutaResponse(UUID idOrden, RutaPick ruta) {
        Map<UUID, LocationResponse> locationMap = fetchLocationMap();
        var detalles = ordenPickPersistence.findDetallesByIdOrden(idOrden)
                .stream()
                .map(d -> DetallePickResponse.builder()
                        .idDetalle(d.idDetalle())
                        .productoId(d.productoId())
                        .locacionId(d.locacionId())
                        .cantRequerida(d.cantRequerida())
                        .cantSeleccion(d.cantSeleccion())
                        .estado(d.estado())
                        .build())
                .toList();

        List<UUID> pickingStops = detalles.stream()
                .map(DetallePickResponse::getLocacionId)
                .distinct()
                .toList();

        Map<UUID, RutaNodeResponse> nodes = new LinkedHashMap<>();
        List<UUID> pathSeq = ruta.pathSeq();
        for (int i = 0; i < pathSeq.size(); i++) {
            UUID id = pathSeq.get(i);
            if (!nodes.containsKey(id)) {
                nodes.put(id, buildNode(id, locationMap.get(id), i, pathSeq.size()));
            }
        }

        return new RutaResponse(pathSeq, List.copyOf(nodes.values()), pickingStops, ruta.distanciaEstimada(), detalles);
    }

    private Map<UUID, LocationResponse> fetchLocationMap() {
        try {
            var response = inventoryFeignClient.getAllLocations();
            if (response == null || response.getData() == null) {
                return Map.of();
            }
            return response.getData().stream()
                    .collect(Collectors.toMap(LocationResponse::getIdLocacion, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private RutaNodeResponse buildNode(UUID id, LocationResponse location, int index, int totalNodes) {
        if (index == 0) {
            return new RutaNodeResponse(id, "ENTRADA", "Entrada", null, null, null, 5, 50);
        }
        if (index == totalNodes - 1) {
            return new RutaNodeResponse(id, "SALIDA", "Salida", null, null, null, 95, 50);
        }

        if (location == null) {
            return new RutaNodeResponse(
                    id,
                    "RACK",
                    id.toString().substring(0, 8),
                    null,
                    null,
                    null,
                    fallbackX(index),
                    fallbackY(index));
        }

        return new RutaNodeResponse(
                id,
                "RACK",
                buildLocationLabel(location),
                location.getZona(),
                location.getPasillo(),
                location.getEstante(),
                location.getPosX() != null ? location.getPosX() : fallbackX(index),
                location.getPosY() != null ? location.getPosY() : fallbackY(index));
    }

    private String buildLocationLabel(LocationResponse location) {
        if (location.getCodBarras() != null && !location.getCodBarras().isBlank()) {
            return location.getCodBarras();
        }
        return String.join("-",
                List.of(
                        valueOrDefault(location.getZona(), "Z"),
                        valueOrDefault(location.getPasillo(), "P"),
                        valueOrDefault(location.getEstante(), "E")));
    }

    private String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private int fallbackX(int index) {
        return 18 + ((index - 1) % 4) * 20;
    }

    private int fallbackY(int index) {
        return 22 + ((index - 1) / 4) * 18;
    }
}
