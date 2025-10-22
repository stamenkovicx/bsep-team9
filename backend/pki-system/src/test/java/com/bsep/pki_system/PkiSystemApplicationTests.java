package com.bsep.pki_system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.h2.console.enabled=true",

		// Disable features that require real services
		"spring.mail.host=localhost",
		"spring.mail.port=2525",
		"jwt.secret=test-secret-key-for-testing-only",
		"keystore.master.key=test-master-key",
		"recaptcha.secret.key=test-recaptcha-key",
		"recaptcha.site.key=test-site-key"
})
class PkiSystemApplicationTests {

	@Test
	void contextLoads() {
		// Test will pass if Spring context loads successfully
		// This just verifies that all beans can be created
	}
}