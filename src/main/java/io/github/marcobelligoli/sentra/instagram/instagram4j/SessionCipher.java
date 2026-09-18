package io.github.marcobelligoli.sentra.instagram.instagram4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.github.marcobelligoli.sentra.config.SentraProperties;
import org.springframework.stereotype.Component;

/**
 * Encrypts the stored Instagram session with AES-256-GCM. Each value is bound to a context (account and field) as
 * associated data, so encrypted values cannot be swapped between rows or columns.
 */
@Component
class SessionCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final int KEY_LENGTH = 32;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	SessionCipher(SentraProperties properties) {
		this.key = new SecretKeySpec(decodeKey(properties.sessionKey()), "AES");
	}

	String encrypt(String plaintext, String context) {
		byte[] iv = new byte[IV_LENGTH];
		random.nextBytes(iv);
		try {
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, context);
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder()
				.encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Session encryption failed", ex);
		}
	}

	/**
	 * @return the plaintext, or empty if the value was not encrypted with the current key and context
	 */
	Optional<String> decrypt(String encrypted, String context) {
		byte[] data;
		try {
			data = Base64.getDecoder().decode(encrypted);
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
		if (data.length < IV_LENGTH + TAG_LENGTH_BITS / 8) {
			return Optional.empty();
		}
		try {
			Cipher cipher = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(data, IV_LENGTH), context);
			byte[] plaintext = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
			return Optional.of(new String(plaintext, StandardCharsets.UTF_8));
		}
		catch (AEADBadTagException ex) {
			return Optional.empty();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Session decryption failed", ex);
		}
	}

	private Cipher cipher(int mode, byte[] iv, String context) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
		cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
		return cipher;
	}

	private static byte[] decodeKey(String encoded) {
		String help = "SENTRA_SESSION_KEY must be a Base64 encoded 32-byte key, generate one with "
				+ "'openssl rand -base64 32'";
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(encoded.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException(help, ex);
		}
		if (bytes.length != KEY_LENGTH) {
			throw new IllegalStateException(help);
		}
		return bytes;
	}

}
