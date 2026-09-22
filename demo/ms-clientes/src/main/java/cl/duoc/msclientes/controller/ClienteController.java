package cl.duoc.msclientes.controller;

import cl.duoc.msclientes.dto.ClienteResponse;
import cl.duoc.msclientes.service.ClienteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService clienteService;

	public ClienteController(ClienteService clienteService) {
		this.clienteService = clienteService;
	}

	@GetMapping("/{id}")
	public ClienteResponse buscarPorId(@PathVariable Long id) {
		return clienteService.buscarPorId(id);
	}
}
