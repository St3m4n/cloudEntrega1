package cl.duoc.api.controller;

import cl.duoc.api.dto.ClienteResponse;
import cl.duoc.api.service.DataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DataController {

	private final DataService dataService;

	public DataController(DataService dataService) {
		this.dataService = dataService;
	}

	@GetMapping("/data")
	public ClienteResponse data() {
		return dataService.obtenerData();
	}
}
