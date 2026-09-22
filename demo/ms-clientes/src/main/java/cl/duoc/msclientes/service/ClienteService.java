package cl.duoc.msclientes.service;

import cl.duoc.msclientes.dto.ClienteResponse;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

	public ClienteResponse buscarPorId(Long id) {
		return new ClienteResponse(
			id,
			"Wacoldo Soto",
			"waco.soto@duocuc.cl"
		);
	}
}
