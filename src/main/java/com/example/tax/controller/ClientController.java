package com.example.tax.controller;

import com.core.lib.dto.onboarding.ClientDto;
import com.example.tax.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/client")
@Tag(name = "Client APIs", description = "Endpoints for client")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    @Operation(summary = "Create a new client", description = "Creates a new client with contacts and country details")
    public ResponseEntity<ClientDto> create(@RequestBody ClientDto dto) {
        return ResponseEntity.ok(clientService.save(dto));
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Get client by ID", description = "Retrieve client details by client ID")
    public ResponseEntity<ClientDto> getById(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientService.getByClientId(clientId));
    }

    @PutMapping("/{clientId}")
    @Operation(summary = "Update existing client", description = "Update client details")
    public ResponseEntity<ClientDto> update(@PathVariable Long clientId, @RequestBody ClientDto dto) {
        return ResponseEntity.ok(clientService.updateClient(clientId, dto));
    }

    @DeleteMapping("/{clientId}")
    @Operation(summary = "Delete client by ID", description = "Deletes a client by client ID")
    public ResponseEntity<String> delete(@PathVariable Long clientId) {
        clientService.delete(clientId);
        return ResponseEntity.ok("Client deleted successfully");
    }

}
