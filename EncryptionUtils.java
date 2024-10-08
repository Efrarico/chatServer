import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final byte[] keyValue = "MySuperSecretKey".getBytes();  // Usa una clave de 16 bytes

    // Método para encriptar
    public static String encrypt(String data) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encVal);
    }

    // Método para desencriptar
    public static String decrypt(String encryptedData) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyValue, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decodedValue = Base64.getDecoder().decode(encryptedData);
        byte[] decValue = cipher.doFinal(decodedValue);
        return new String(decValue);
    }
}
