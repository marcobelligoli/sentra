package io.github.marcobelligoli.sentra.notification;

import java.util.List;
import java.util.Map;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import io.github.marcobelligoli.sentra.instagram.InstagramUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends notifications through the Telegram Bot API.
 */
class TelegramNotifier implements Notifier {

	private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

	// Telegram rejects messages longer than 4096 characters
	private static final int MAX_MESSAGE_LENGTH = 4000;

	private final RestClient client;
	private final String chatId;

	TelegramNotifier(SentraProperties.Telegram telegram, RestClient.Builder builder) {
		this.client = builder.baseUrl("https://api.telegram.org/bot" + telegram.botToken()).build();
		this.chatId = telegram.chatId();
	}

	@Override
	public void notifyUnfollowers(String account, List<InstagramUser> unfollowers) {
		for (String text : NotificationMessages.unfollowers(account, unfollowers, MAX_MESSAGE_LENGTH)) {
			send(text);
		}
	}

	@Override
	public void notifySyncFailure(String account, String reason) {
		send(NotificationMessages.syncFailure(account, reason, MAX_MESSAGE_LENGTH));
	}

	private void send(String text) {
		// Exception messages contain the request URL, which contains the bot token: never log them
		try {
			client.post()
				.uri("/sendMessage")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("chat_id", chatId, "text", text))
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			throw new NotificationException(
					"Telegram rejected the message: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString());
		}
		catch (RestClientException ex) {
			log.debug("Telegram request failed", ex.getCause());
			throw new NotificationException("Telegram request failed: " + ex.getClass().getSimpleName());
		}
	}

}
