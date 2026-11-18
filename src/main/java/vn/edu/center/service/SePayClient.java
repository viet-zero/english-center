package vn.edu.center.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SePayClient {
    private final String webhookKey;
    private final String accountNumber;
    private final String bank;
    private final boolean demo;

    public SePayClient(@Value("${sepay.webhook-api-key:}") String webhookKey,
            @Value("${sepay.account-number:}") String accountNumber,
            @Value("${sepay.bank:}") String bank,
            @Value("${sepay.demo:false}") boolean demo) {
        this.webhookKey=webhookKey;
        this.accountNumber=accountNumber;
        this.bank=bank;
        this.demo=demo;
    }

    public boolean isDemo() { return demo; }

    public String accountNumber() {
        return demo && accountNumber.isBlank() ? "0000000000" : accountNumber;
    }

    public String linkId(long code) {
        return (demo ? "sepay-demo-" : "sepay-")+code;
    }

    public String reference(long transactionId) {
        return (demo ? "SEPAY-DEMO-" : "SEPAY-")+transactionId;
    }

    public boolean authorize(String authorization) {
        return !webhookKey.isBlank() && authorization!=null
                && MessageDigest.isEqual(("Apikey "+webhookKey).getBytes(StandardCharsets.UTF_8),
                        authorization.getBytes(StandardCharsets.UTF_8));
    }

    public Link create(long code,int amount) {
        if (demo) return new Link(linkId(code),null,accountNumber());
        if (webhookKey.isBlank() || !accountNumber.matches("[A-Za-z0-9]{1,50}") || bank.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình SePay. Điền API Key webhook, ngân hàng và tài khoản nhận.");
        }
        String url=UriComponentsBuilder.fromUriString("https://vietqr.app/img")
                .queryParam("acc",accountNumber).queryParam("bank",bank).queryParam("amount",amount)
                .queryParam("des","EC"+code).queryParam("template","compact").queryParam("showinfo",true)
                .build().encode().toUriString();
        return new Link(linkId(code),url,accountNumber);
    }

    public record Link(String paymentLinkId,String checkoutUrl,String accountNumber) {}
}
