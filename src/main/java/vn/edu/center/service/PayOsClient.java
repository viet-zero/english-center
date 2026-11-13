package vn.edu.center.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PayOsClient {
    private final RestClient client;
    private final String secret;
    private final String returnUrl;
    private final boolean demo;

    public PayOsClient(@Value("${payos.client-id:}") String clientId,
            @Value("${payos.api-key:}") String apiKey,
            @Value("${payos.checksum-key:}") String secret,
            @Value("${payos.return-url:}") String returnUrl,
            @Value("${payos.demo:false}") boolean demo) {
        this.secret = secret;
        this.returnUrl = returnUrl;
        this.demo = demo;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.client = RestClient.builder().requestFactory(factory)
                .baseUrl("https://api-merchant.payos.vn")
                .defaultHeader("x-client-id", clientId)
                .defaultHeader("x-api-key", apiKey).build();
    }

    public Link create(long order, int amount) {
        if (secret.isBlank() || (!demo && returnUrl.isBlank())) {
            throw new IllegalStateException("Thanh toán payOS chưa được cấu hình.");
        }
        if (demo) return new Link("demo-" + order, null);
        var body = new TreeMap<String, Object>(Map.of(
                "amount", amount, "orderCode", order, "description", "HP " + order,
                "returnUrl", returnUrl, "cancelUrl", returnUrl));
        String signature = sign(body);
        body.put("signature", signature);
        body.put("expiredAt", Instant.now().plusSeconds(1800).getEpochSecond());
        try {
            Response response = client.post().uri("/v2/payment-requests").body(body)
                    .retrieve().body(Response.class);
            if (response != null && "00".equals(response.code()) && response.data() != null) {
                Link link = response.data();
                if (link.paymentLinkId() != null && link.checkoutUrl() != null
                        && "https".equals(URI.create(link.checkoutUrl()).getScheme())) return link;
            }
        } catch (RestClientException | IllegalArgumentException failure) {
            throw new IllegalStateException("Chưa tạo được mã QR payOS. Vui lòng thử lại.", failure);
        }
        throw new IllegalStateException("Chưa tạo được mã QR payOS. Vui lòng thử lại.");
    }

    public boolean verify(Map<String, Object> data, String signature) {
        if (secret.isBlank() || data == null || signature == null || !signature.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        if (data.values().stream().anyMatch(value -> value instanceof Map || value instanceof java.util.Collection)) {
            return false;
        }
        return MessageDigest.isEqual(HexFormat.of().parseHex(sign(data)), HexFormat.of().parseHex(signature));
    }

    private String sign(Map<String, Object> fields) {
        String data = new TreeMap<>(fields).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + signatureValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Không ký được dữ liệu payOS.", error);
        }
    }

    private String signatureValue(Object value) {
        if (value == null || "null".equals(value) || "undefined".equals(value)) return "";
        return value.toString();
    }

    public record Link(String paymentLinkId, String checkoutUrl) {}
    private record Response(String code, Link data) {}
}
