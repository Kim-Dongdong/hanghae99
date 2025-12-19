package kr.hhplus.be.server.infrastructure.dataplatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import kr.hhplus.be.server.domain.port.DataPlatformPort;

@Component
public class HttpDataPlatformAdapter implements DataPlatformPort {

	private static final Logger log = LoggerFactory.getLogger(HttpDataPlatformAdapter.class);

	private final RestTemplate restTemplate;

	// 과제용 mock API 엔드포인트
	private final String endpointUrl = "http://localhost:8080/mock/reservations";

	public HttpDataPlatformAdapter(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	@Override
	public void sendReservationConfirmed(ReservationConfirmedPayload payload) {
		try {
			restTemplate.postForEntity(endpointUrl, payload, Void.class);
			log.info("Sent reservation to data-platform. payload={}", payload);
		} catch (Exception e) {
			// 예약 트랜잭션에 영향을 주면 안 되므로 여기서는 로깅만
			log.error("Failed to send reservation to data-platform", e);
		}
	}
}
