package com.dudububu.store;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ProfilePictures {
    private static final int SIZE = 256;
    private static final int MAX_UPLOAD_BYTES = 768 * 1024;

    private ProfilePictures() {}

    static byte[] thumbnail(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw invalid("Choose a picture under 768 KB.");
        }
        try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("Choose a valid JPEG or PNG picture.");
            }
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
                    throw invalid("Choose a JPEG or PNG picture.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > 4096 || height > 4096 || (long) width * height > 12_000_000) {
                    throw invalid("Choose a picture no larger than 4,096 pixels per side and 12 megapixels.");
                }
                return squareJpeg(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw invalid("This picture could not be read. Try another JPEG or PNG.");
        }
    }

    private static byte[] squareJpeg(BufferedImage source) throws IOException {
        int side = Math.min(source.getWidth(), source.getHeight());
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        var thumbnail = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var graphics = thumbnail.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIZE, SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, SIZE, SIZE, left, top, left + side, top + side, null);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpeg", output);
        return output.toByteArray();
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
