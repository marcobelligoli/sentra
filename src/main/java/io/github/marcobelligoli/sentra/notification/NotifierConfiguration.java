package io.github.marcobelligoli.sentra.notification;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class NotifierConfiguration {

	@Bean
	Notifier notifier(SentraProperties properties) {
		if (properties.telegram().enabled()) {
			return new TelegramNotifier(properties.telegram(), RestClient.builder());
		}
		LoggerFactory.getLogger(NotifierConfiguration.class)
			.warn("Telegram is not configured (SENTRA_TELEGRAM_BOT_TOKEN / SENTRA_TELEGRAM_CHAT_ID): "
					+ "notifications will only be logged");
		return new LoggingNotifier();
	}

}
