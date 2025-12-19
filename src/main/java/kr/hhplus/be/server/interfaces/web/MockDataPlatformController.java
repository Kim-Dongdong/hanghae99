package kr.hhplus.be.server.interfaces.web;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.domain.port.DataPlatformPort;

@RestController
@RequestMapping("/mock")
public class MockDataPlatformController {

	private static final Logger log = LoggerFactory.getLogger(MockDataPlatformController.class);

	@PostMapping("/reservations")
	public void receiveReservation(@RequestBody DataPlatformPort.ReservationConfirmedPayload payload) {
		log.info("Mock data-platform received reservation: {}", payload);
		// 여기서는 그냥 로그만 찍지만, 필요하면 메모리/DB 저장도 가능
	}
}
