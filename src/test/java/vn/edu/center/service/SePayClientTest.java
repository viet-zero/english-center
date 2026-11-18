package vn.edu.center.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SePayClientTest {
    @Test
    void qrContainsOnlyTransferDetailsAndWebhookUsesItsOwnKey() {
        var client=new SePayClient("unit-webhook-secret","0123456789","MBBank",false);
        var link=client.create(123456,10000);
        assertTrue(link.checkoutUrl().startsWith("https://vietqr.app/img?"));
        assertTrue(link.checkoutUrl().contains("acc=0123456789"));
        assertTrue(link.checkoutUrl().contains("amount=10000"));
        assertTrue(link.checkoutUrl().contains("des=EC123456"));
        assertFalse(link.checkoutUrl().contains("unit-webhook-secret"));
        assertTrue(client.authorize("Apikey unit-webhook-secret"));
        assertFalse(client.authorize(null));
        assertFalse(client.authorize("Apikey wrong"));
        assertFalse(new SePayClient("","","",true).authorize("Apikey "));
    }

    @Test
    void demoHasNoTransferQrAndLiveRequiresConfiguration() {
        var demo=new SePayClient("","","",true);
        assertNull(demo.create(123,10000).checkoutUrl());
        assertEquals("sepay-demo-123",demo.create(123,10000).paymentLinkId());
        assertThrows(IllegalStateException.class,()->new SePayClient("","","",false).create(123,10000));
    }
}
