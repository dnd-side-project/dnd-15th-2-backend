/**
 * Created at: 2026-09-05T03:45:38+09:00
 * Source scenario: TEST-PLAN-GH-215-STRUCTURED-REQUEST-LOGGING-INT-004 through INT-005
 */
package com.dnd.qello;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class StructuredLoggingProcessProbeApplication {

	private static final Logger LOG = LoggerFactory.getLogger("STRUCTURED_LOGGING_PROBE");

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(
				StructuredLoggingProcessProbeApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		try (ConfigurableApplicationContext ignored = application.run(args)) {
			MDC.put("requestId", "profile-probe");
			try {
				LOG.atInfo()
						.addKeyValue("status", 200)
						.log("structured_logging_probe");
			} finally {
				MDC.remove("requestId");
			}
		}
	}
}
