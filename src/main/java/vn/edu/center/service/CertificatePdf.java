package vn.edu.center.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Map;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;

@Component
public class CertificatePdf {
    private final String baseUrl;

    public CertificatePdf(@Value("${certificates.public-base-url:http://127.0.0.1:5175}") String baseUrl) {
        var uri = java.net.URI.create(baseUrl);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid certificates.public-base-url");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String verificationUrl(String token) {
        return baseUrl + "/api/certificates/verify/" + token;
    }

    public byte[] create(String code, String token, String name, String course, String score, String attendance) {
        try (var document = new PDDocument();
                var input = new ClassPathResource("fonts/NotoSans-Regular.ttf").getInputStream();
                var output = new ByteArrayOutputStream()) {
            var page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);
            var font = PDType0Font.load(document, input, true);
            try (var stream = new PDPageContentStream(document, page)) {
                float width = page.getMediaBox().getWidth();
                stream.setStrokingColor(new Color(46,49,146));
                stream.setLineWidth(2);
                stream.addRect(26, 26, width - 52, page.getMediaBox().getHeight() - 52);
                stream.stroke();
                stream.setStrokingColor(new Color(255,69,69));
                stream.addRect(33, 33, width - 66, page.getMediaBox().getHeight() - 66);
                stream.stroke();
                center(stream, font, "ENGLISH CENTER", 21, 526, width, new Color(46,49,146));
                center(stream, font, "CHỨNG CHỈ HOÀN THÀNH KHÓA HỌC", 25, 464, width, new Color(46,49,146));
                center(stream, font, "Chứng nhận học viên", 14, 418, width, Color.DARK_GRAY);
                center(stream, font, name, 27, 370, width, new Color(46,49,146));
                center(stream, font, "Đã hoàn thành đầy đủ các học phần của khóa", 14, 332, width, Color.DARK_GRAY);
                center(stream, font, course, 22, 294, width, Color.DARK_GRAY);
                center(stream, font, "Điểm tổng kết " + score + "/10  ·  Chuyên cần " + attendance + "%", 14, 253, width, Color.DARK_GRAY);
                var matrix = new QRCodeWriter().encode(verificationUrl(token), BarcodeFormat.QR_CODE, 260, 260,
                        Map.of(EncodeHintType.CHARACTER_SET, "UTF-8", EncodeHintType.MARGIN, 4));
                var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) image.setRGB(x,y,matrix.get(x,y) ? 0x000000 : 0xffffff);
                }
                stream.drawImage(LosslessFactory.createFromImage(document, image), 74, 72, 125, 125);
                text(stream,font,"Quét QR để tra cứu chứng chỉ",11,65,57,Color.DARK_GRAY);
                text(stream,font,"Mã chứng chỉ " + code,14,460,177,Color.DARK_GRAY);
                text(stream,font,"Ngày cấp " + LocalDate.now(),13,460,149,Color.DARK_GRAY);
                text(stream,font,"Đã đáp ứng điều kiện học phí và học tập",11,460,115,Color.DARK_GRAY);
                text(stream,font,"Xác nhận điện tử bởi English Center",11,460,90,new Color(46,49,146));
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException | WriterException error) {
            throw new IllegalStateException("Không tạo được chứng chỉ PDF.", error);
        }
    }

    private void center(PDPageContentStream stream, PDType0Font font, String text, float size,
            float y, float width, Color color) throws IOException {
        text = clean(text);
        float length = font.getStringWidth(text) / 1000;
        size = Math.min(size, (width - 120) / Math.max(length, 1));
        text(stream,font,text,size,(width - length * size) / 2,y,color);
    }

    private void text(PDPageContentStream stream, PDType0Font font, String text, float size,
            float x, float y, Color color) throws IOException {
        stream.beginText();
        stream.setFont(font,size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x,y);
        stream.showText(clean(text));
        stream.endText();
    }

    private String clean(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFC).replaceAll("\\p{Cntrl}", " ");
    }
}
