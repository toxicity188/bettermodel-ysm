package io.github.toxicity188.bettermodelysm;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.zip.DeflaterOutputStream;

/** Creates an original, encrypted YSM V1 fixture without redistributing anyone's model. */
final class EncryptedFixture {
    static byte[] create() throws Exception {
        var resources = new LinkedHashMap<String, byte[]>();
        resources.put("main.json", """
            {"format_version":"1.12.0","minecraft:geometry":[{
              "description":{"identifier":"geometry.fixture","texture_width":16,"texture_height":16},
              "bones":[{"name":"root","pivot":[0,0,0],
                "cubes":[{"origin":[-1,0,-1],"size":[2,2,2],"uv":[0,0]}]}]}]}
            """.getBytes(StandardCharsets.UTF_8));
        var png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "PNG", png);
        resources.put("main.png", png.toByteArray());
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeBytes("YSGP");
            output.writeInt(1);
            output.write(new byte[16]);
            for (var resource : resources.entrySet()) {
                var compressed = new ByteArrayOutputStream();
                try (var deflater = new DeflaterOutputStream(compressed)) {
                    deflater.write(resource.getValue());
                }
                var key = new byte[16];
                var iv = new byte[16];
                var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
                var encrypted = cipher.doFinal(compressed.toByteArray());
                var name = resource.getKey().getBytes(StandardCharsets.UTF_8);
                output.writeInt(name.length);
                output.write(name);
                output.writeInt(encrypted.length);
                output.write(key);
                output.write(iv);
                output.write(encrypted);
            }
        }
        return bytes.toByteArray();
    }
}
