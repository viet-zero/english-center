package vn.edu.center.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class CertificatePdfTest {
    @Test
    void certificateHasVietnameseTextAndScannableVerificationQr() throws Exception {
        var pdf=new CertificatePdf("http://127.0.0.1:5175");
        String token="7b952b6f-0a66-4edb-a56a-fc373da01cec";
        byte[] bytes=pdf.create("CC123",token,"Nguyễn Thị Ánh","Tiếng Anh giao tiếp cơ bản","8.50","90.00");
        try (var document=Loader.loadPDF(bytes)) {
            assertEquals(1,document.getNumberOfPages());
            String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("Nguyễn Thị Ánh"));
            assertTrue(text.contains("Tiếng Anh giao tiếp cơ bản"));
            assertTrue(text.contains("CC123"));
            var image=new PDFRenderer(document).renderImageWithDPI(0,144);
            int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
            var source=new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels);
            var result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),
                    Map.of(DecodeHintType.TRY_HARDER,true));
            assertEquals(pdf.verificationUrl(token),result.getText());
            Files.write(Path.of("target/certificate-preview.pdf"),bytes);
            ImageIO.write(image,"PNG",Path.of("target/certificate-preview.png").toFile());
        }
    }
}
