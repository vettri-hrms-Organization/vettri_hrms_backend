package com.haodaone.esign;

import com.haodaone.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Signature photos: paper (white or grey, with a shadow) becomes transparent; the ink is kept and cropped. */
class SignatureCleanTest {

    @Test
    void dimGreyPaperPhotoBecomesTransparentAroundTheInk() throws Exception {
        BufferedImage clean = clean(photo(new Color(150, 152, 155), true, true));
        assertTrue(clean.getWidth() < 500, "cropped to the signature, was " + clean.getWidth());
        assertEquals(0, clean.getRGB(0, 0) >>> 24, "grey paper corner is transparent");
        assertEquals(0, clean.getRGB(clean.getWidth() - 1, clean.getHeight() - 1) >>> 24, "shadowed corner is transparent");
        assertTrue(inkPixels(clean) > 200, "the strokes are kept");
    }

    @Test
    void whitePaperPhotoStillWorks() throws Exception {
        BufferedImage clean = clean(photo(new Color(246, 244, 240), false, true));
        assertEquals(0, clean.getRGB(0, 0) >>> 24);
        assertTrue(inkPixels(clean) > 200);
    }

    @Test
    void blankPaperIsRejected() {
        assertThrows(BadRequestException.class, () -> EsignService.cleanSignature(photo(new Color(150, 152, 155), true, false)));
    }

    private static BufferedImage clean(byte[] in) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(EsignService.cleanSignature(in)));
    }

    private static int inkPixels(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) >>> 24) > 128) n++;
        return n;
    }

    /** An 800x400 photo of paper, optionally with a darker shadow band at the bottom-right, optionally signed. */
    private static byte[] photo(Color paper, boolean shadow, boolean ink) throws Exception {
        BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(paper);
        g.fillRect(0, 0, 800, 400);
        if (shadow) {
            g.setColor(new Color(Math.max(0, paper.getRed() - 22), Math.max(0, paper.getGreen() - 22), Math.max(0, paper.getBlue() - 22)));
            g.fillRect(560, 300, 240, 100);
        }
        if (ink) {
            g.setColor(new Color(40, 50, 110));
            g.setStroke(new BasicStroke(6));
            g.drawPolyline(new int[]{250, 300, 340, 390, 450, 520}, new int[]{220, 160, 230, 170, 240, 190}, 6);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
