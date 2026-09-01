package com.zonaacme.sica.shared.seguridad;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Derivacion de contrasenas con PBKDF2-HMAC-SHA256.
 *
 * Se eligio PBKDF2 y no un hash simple (MD5, SHA-256 a secas) porque un hash
 * rapido es justamente lo que un atacante necesita para probar millones de
 * combinaciones por segundo. PBKDF2 es lento a proposito.
 *
 * Se eligio PBKDF2 y no BCrypt porque viene en el JDK (javax.crypto) y el
 * proyecto no puede sumar dependencias externas.
 *
 * Formato almacenado:  iteraciones:saltBase64:hashBase64
 */
public final class HasheadorPassword {

    private static final int ITERACIONES = 120_000;
    private static final int BYTES_SALT  = 16;
    private static final int BITS_CLAVE  = 256;
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private HasheadorPassword() { }

    public static String hashear(char[] password) {
        byte[] salt = new byte[BYTES_SALT];
        ALEATORIO.nextBytes(salt);
        byte[] hash = derivar(password, salt, ITERACIONES);
        return ITERACIONES + ":"
             + Base64.getEncoder().encodeToString(salt) + ":"
             + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(char[] password, String almacenado) {
        try {
            String[] partes = almacenado.split(":");
            if (partes.length != 3) return false;

            int iteraciones = Integer.parseInt(partes[0]);
            byte[] salt     = Base64.getDecoder().decode(partes[1]);
            byte[] esperado = Base64.getDecoder().decode(partes[2]);

            byte[] calculado = derivar(password, salt, iteraciones);
            return comparacionEnTiempoConstante(esperado, calculado);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derivar(char[] password, byte[] salt, int iteraciones) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iteraciones, BITS_CLAVE);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo derivar la contrasena", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Compara los dos arreglos completos siempre, sin cortar en la primera
     * diferencia. Un equals normal termina antes cuando los bytes difieren, y
     * ese tiempo distinto es una filtracion de informacion medible.
     */
    private static boolean comparacionEnTiempoConstante(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diferencia = 0;
        for (int i = 0; i < a.length; i++) {
            diferencia |= a[i] ^ b[i];
        }
        return diferencia == 0;
    }
}
