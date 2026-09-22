package cl.duoc.api.service;

import cl.duoc.api.dto.ClienteResponse;
import cl.duoc.api.repository.ClienteRepository;
import org.springframework.stereotype.Service;

@Service
public class DataService {

	private final ClienteRepository clienteRepository;

	public DataService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	public ClienteResponse obtenerData() {
		return clienteRepository.buscarPorId(1L);
	}
}
