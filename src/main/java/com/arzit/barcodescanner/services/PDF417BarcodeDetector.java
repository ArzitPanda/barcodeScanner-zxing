package com.arzit.barcodescanner.services;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

@Service
public class PDF417BarcodeDetector {

    public static BufferedImage detectPDF417Region(BufferedImage image) {
        // Step 1: Convert to Grayscale
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grayImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Step 2: Apply Thresholding (Adaptive thresholding, if possible)
//        BufferedImage binaryImage = applyAdaptiveThreshold(grayImage);

        // Step 3: Morphological Transformations to emphasize the barcode region
        BufferedImage morphImage = applyMorphologicalOperations(image);

        // Step 4: Horizontal and Vertical Projections to find rectangular regions
        Rectangle barcodeRect = findBarcodeRegionByProjection(morphImage);

        // Step 5: Crop the barcode region from the original image
        if (barcodeRect != null) {
            return grayImage.getSubimage(barcodeRect.x, barcodeRect.y, barcodeRect.width, barcodeRect.height);
        }
        return null; // No barcode detected
    }

    private static BufferedImage applyAdaptiveThreshold(BufferedImage image) {
        BufferedImage binaryImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = new Color(image.getRGB(x, y)).getRed();
                int threshold = 128; // Adaptive threshold logic can be complex in core Java
                int binaryColor = color < threshold ? 0 : 255;
                binaryImage.setRGB(x, y, new Color(binaryColor, binaryColor, binaryColor).getRGB());
            }
        }
        return binaryImage;
    }

    private static BufferedImage applyMorphologicalOperations(BufferedImage binaryImage) {
        // Simple dilation followed by erosion to strengthen rectangular structures
        int dilationRadius = 2; // Example, tune based on the image size and barcode size
        BufferedImage morphImage = new BufferedImage(binaryImage.getWidth(), binaryImage.getHeight(), binaryImage.getType());

        // Dilation operation: expand white regions
        for (int y = dilationRadius; y < binaryImage.getHeight() - dilationRadius; y++) {
            for (int x = dilationRadius; x < binaryImage.getWidth() - dilationRadius; x++) {
                boolean isWhite = false;
                for (int dy = -dilationRadius; dy <= dilationRadius; dy++) {
                    for (int dx = -dilationRadius; dx <= dilationRadius; dx++) {
                        if (new Color(binaryImage.getRGB(x + dx, y + dy)).getRed() == 255) {
                            isWhite = true;
                            break;
                        }
                    }
                    if (isWhite) break;
                }
                morphImage.setRGB(x, y, isWhite ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
            }
        }

        return morphImage;
    }

    private static Rectangle findBarcodeRegionByProjection(BufferedImage morphImage) {
        int width = morphImage.getWidth();
        int height = morphImage.getHeight();

        // Horizontal and Vertical projection arrays
        int[] horizontalProjection = new int[height];
        int[] verticalProjection = new int[width];

        // Calculate horizontal projection (sum of white pixels in each row)
        for (int y = 0; y < height; y++) {
            int sum = 0;
            for (int x = 0; x < width; x++) {
                if (new Color(morphImage.getRGB(x, y)).getRed() == 255) {
                    sum++;
                }
            }
            horizontalProjection[y] = sum;
        }

        // Calculate vertical projection (sum of white pixels in each column)
        for (int x = 0; x < width; x++) {
            int sum = 0;
            for (int y = 0; y < height; y++) {
                if (new Color(morphImage.getRGB(x, y)).getRed() == 255) {
                    sum++;
                }
            }
            verticalProjection[x] = sum;
        }

        // Identify regions with high projection values (likely barcode region)
        int minY = findPeakRegion(horizontalProjection);
        int maxY = findPeakRegion(horizontalProjection, true);
        int minX = findPeakRegion(verticalProjection);
        int maxX = findPeakRegion(verticalProjection, true);

        if (minX < maxX && minY < maxY) {
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
        return null;
    }

    private static int findPeakRegion(int[] projection) {
        return findPeakRegion(projection, false);
    }

    private static int findPeakRegion(int[] projection, boolean reverse) {
        int maxSum = 0;
        int peakIndex = reverse ? projection.length - 1 : 0;
        int start = reverse ? projection.length - 1 : 0;
        int end = reverse ? -1 : projection.length;
        int step = reverse ? -1 : 1;

        for (int i = start; i != end; i += step) {
            if (projection[i] > maxSum) {
                maxSum = projection[i];
                peakIndex = i;
            }
        }
        return peakIndex;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File("path/to/pdf417-barcode-image.png"));
        BufferedImage barcodeRegion = detectPDF417Region(image);
        if (barcodeRegion != null) {
            ImageIO.write(barcodeRegion, "png", new File("path/to/output-barcode-region.png"));
            System.out.println("PDF417 Barcode region detected and saved.");
        } else {
            System.out.println("No PDF417 barcode detected.");
        }
    }
}
