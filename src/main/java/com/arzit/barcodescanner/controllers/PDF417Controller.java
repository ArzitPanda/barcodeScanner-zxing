package com.arzit.barcodescanner.controllers;

import com.arzit.barcodescanner.services.PDF417BarcodeDetector;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/pdf417")
public class PDF417Controller {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }


    @Autowired
    PDF417BarcodeDetector pdf417BarcodeDetector;
    @PostMapping("/decode")
    public ResponseEntity<String> decodePDF417(@RequestParam("file") MultipartFile file) {
        try {
            // Step 1: Convert MultipartFile to OpenCV Mat

            BufferedImage image = ImageIO.read(file.getInputStream());




            Mat inputImage = convertMultipartFileToMat(file);




            if (inputImage.empty()) {
                return ResponseEntity.badRequest().body("Failed to process the image.");
            }
            else
            {
                String decoded1stIteration = decodePDF417(inputImage);
                if(decoded1stIteration!=null)
                {
                    return ResponseEntity.ok(decoded1stIteration);
                }
            }




            // Step 2: Preprocess the image (grayscale, threshold, find contours)
            List<Mat> croppedBarcode = cropImageBarcode(inputImage);

            if (croppedBarcode == null) {
                return ResponseEntity.badRequest().body("No barcode detected.");
            }
            else
            {
//                saveBarcodeToFolder(croppedBarcode,"D:\\barcode\\barcodeScanner\\src\\main\\java\\com\\arzit\\barcodescanner\\images\\", LocalDateTime.now().toString().replace('.','a').replace(':','b')+".jpg");
            }

            // Step 3: Decode the cropped barcode using Zxing

            for(Mat probable:croppedBarcode)
            {
                String decodedText = decodePDF417(probable);
                if(decodedText==null)
                {
                    Mat mat = new Mat();
                    rotate90ACW(probable,mat);
                    decodedText=decodePDF417(mat);
                }
                if(decodedText!=null &&!decodedText.isEmpty())
                {
                    return ResponseEntity.ok(decodedText);
                }
            }


                return ResponseEntity.badRequest().body("No valid PDF417 barcode found!");




        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error processing the image: " + e.getMessage());
        }
    }
    private Mat convertMultipartFileToMat(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("uploaded", ".jpg");
        file.transferTo(tempFile);
        return Imgcodecs.imread(tempFile.getAbsolutePath());
    }

    private Mat cropBarcode(Mat inputImage) {
        Mat grayImage = new Mat();
        Imgproc.cvtColor(inputImage, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Thresholding
        Mat binaryImage = new Mat(inputImage.size(), CvType.CV_8UC1);
        for (int y = 0; y < grayImage.rows(); y++) {
            for (int x = 0; x < grayImage.cols(); x++) {
                double[] pixelValue = grayImage.get(y, x);  // Get grayscale value of the pixel
                if (pixelValue != null) {
                    double grayValue = pixelValue[0];

                    // Get the pixel value from the original image (in BGR)
                    double[] originalPixel = inputImage.get(y, x);
                    double blue = originalPixel[0];
                    double green = originalPixel[1];
                    double red = originalPixel[2];

                    // Calculate the average brightness of the original pixel
                    double averageBrightness = (red + green + blue) / 3;

                    // Compare the average brightness with the gray value and apply the threshold
                    if (averageBrightness > grayValue * 1.5) {
                        // Pixel is 50% brighter than the gray value, set to black (0)
                        binaryImage.put(y, x, 0);
                    } else if (averageBrightness < grayValue * 0.5) {
                        // Pixel is 50% darker than the gray value, set to white (255)
                        binaryImage.put(y, x, 255);
                    } else {
                        // Else keep the pixel as is (could be other values in between)
                        binaryImage.put(y, x, grayValue);
                    }
                }
            }
        }

        Imgproc.threshold(grayImage, binaryImage, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binaryImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint largestContour = null;
        double maxArea = 0;


        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            Rect boundingBox = Imgproc.boundingRect(contour);

            double aspectRatio = (double) boundingBox.width / boundingBox.height;
            if (area > maxArea && aspectRatio > 2.0 && aspectRatio < 6.0) { // PDF417 Aspect Ratio
                maxArea = area;
                largestContour = contour;
            }
        }

        if (largestContour != null) {
            Rect boundingBox = Imgproc.boundingRect(largestContour);
            return new Mat(inputImage, boundingBox); // Crop the detected barcode
        }

        return null;
    }

    private String decodePDF417(Mat croppedImage) {
        try {
            // Convert OpenCV Mat to BufferedImage
            BufferedImage bufferedImage = matToBufferedImage(croppedImage);

            // Decode using Zxing
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            PDF417Reader reader = new PDF417Reader();

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); // Enable tryHarder mode
//            hints.put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.PDF_417); // Optional: scan all formats

            // Perform the barcode scan
            Result result = new MultiFormatReader().decode(bitmap);


            return result.getText();

        } catch (Exception e) {
            return null; // Failed to decode
        }
    }
    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", mat, matOfByte);
            byte[] byteArray = matOfByte.toArray();
            BufferedImage image= ImageIO.read(new ByteArrayInputStream(byteArray));
            saveBufferedImageToFile(image,"D:\\barcode\\barcodeScanner\\src\\main\\java\\com\\arzit\\barcodescanner\\images\\"+LocalDateTime.now().toString().replace('.','a').replace(':','b')+".jpg");
            return image;
        } catch (IOException e) {
            throw new RuntimeException("Error converting Mat to BufferedImage: " + e.getMessage());
        }
    }

    public void saveBarcodeToFolder(Mat barcodeImage, String folderPath, String fileName) {
        // Ensure the folder exists or create it
        File folder = new File(folderPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create directory: " + folderPath);
            }
        }

        // Construct the full file path
        String fullPath = folderPath + File.separator + fileName;

        // Save the image
        boolean success = Imgcodecs.imwrite(fullPath, barcodeImage);
        if (!success) {
            throw new RuntimeException("Failed to save barcode image to: " + fullPath);
        }

        System.out.println("Barcode image saved successfully to: " + fullPath);
    }

    public static void saveBufferedImageToFile(BufferedImage bufferedImage, String filePath) {
        try {
            // Define the file where the image will be saved
            File outputFile = new File(filePath);


            String format = "png";

            // Write the BufferedImage to file in the specified format
            ImageIO.write(bufferedImage, format, outputFile);

            System.out.println("Image saved to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to save the image.");
        }
    }



    public static List<Mat> cropImageBarcode(Mat inputImage) {
        // Step 1: Convert to grayscale
        Mat grayImage = new Mat();
        Imgproc.cvtColor(inputImage, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Step 2: Calculate gradient using Sobel operator
        Mat gradX = new Mat(), gradY = new Mat(), grad = new Mat();
        Imgproc.Sobel(grayImage, gradX, CvType.CV_8U, 1, 0, 3, 1, 0, Core.BORDER_DEFAULT);
        Imgproc.Sobel(grayImage, gradY, CvType.CV_8U, 0, 1, 3, 1, 0, Core.BORDER_DEFAULT);
        Core.addWeighted(gradX, 1.0, gradY, 1.0, 0, grad);

        // Step 3: Apply Gaussian blur (3x3 kernel)
        Mat blurredImage = new Mat();
        Imgproc.GaussianBlur(grad, blurredImage, new Size(3, 3), 0);

        // Step 4: Apply binary thresholding at 255 intensity
        Mat binaryImage = new Mat();
        Imgproc.threshold(blurredImage, binaryImage, 255, 255, Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);

        if (binaryImage.channels() != 1) {
            Imgproc.cvtColor(binaryImage, binaryImage, Imgproc.COLOR_BGR2GRAY);
        }

        // Step 5: Morphological Operations (Erosion and Dilation) with 21x7 kernel
        Mat morphKernel = Mat.ones(21, 7, CvType.CV_8UC1); // Corrected kernel type
        Mat morphImage = new Mat();

// Apply morphological close operation
        Imgproc.morphologyEx(binaryImage, morphImage, Imgproc.MORPH_CLOSE, morphKernel);

// Erode the image
        Imgproc.erode(morphImage, morphImage, morphKernel, new Point(-1, -1), 4);

// Dilate the image
        Imgproc.dilate(morphImage, morphImage, morphKernel, new Point(-1, -1), 4);

// Step 6: Find the contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(morphImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);


        // Step 7: Sort contours by area and get the largest ones
        contours.sort((contour1, contour2) -> Double.compare(Imgproc.contourArea(contour2), Imgproc.contourArea(contour1)));


        // Get the largest contour
        if (contours.isEmpty()) {
            return List.of(new Mat()); // No contour found, return empty image
        }
List<Mat> mats = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            MatOfPoint largestContour = contours.get(0);
            Rect barcodeRegion = Imgproc.boundingRect(largestContour);

            double aspectRatio = (double) barcodeRegion.width / barcodeRegion.height;

// Define expansion factor
            double expansionFactor = 5; // Increase this if you want a larger expanded area

// Calculate the expanded bounding box
            int expandedWidth = (int) (barcodeRegion.width * expansionFactor);
            int expandedHeight = (int) (barcodeRegion.height * expansionFactor);

// Calculate the new top-left corner of the expanded box (to keep it centered)
            int x = Math.max(0, barcodeRegion.x - (expandedWidth - barcodeRegion.width) / 2);
            int y = Math.max(0, barcodeRegion.y - (expandedHeight - barcodeRegion.height) / 2);



            int imageWidth = inputImage.cols(); // Assuming inputImage is your original image
            int imageHeight = inputImage.rows();

            int maxWidth = Math.min(expandedWidth, imageWidth - x); // Prevent width overflow
            int maxHeight = Math.min(expandedHeight, imageHeight - y); // Prevent height overflow

// Create the expanded bounding box, ensuring it's within image bounds
//        Rect expandedBarcodeRegion = new Rect(x, y, maxWidth, maxHeight);
// Create the expanded bounding box
            Rect expandedBarcodeRegion = new Rect(x, y, maxWidth, maxHeight);

// Crop the image to the expanded region
            Mat expandedBarcode = new Mat(grayImage, expandedBarcodeRegion);
//            Mat sharpeningKernel = new Mat(3, 3, CvType.CV_32F);
//            sharpeningKernel.put(0, 0, -1.0, -1.0, -1.0,
//                    -1.0,  9.0, -1.0,
//                    -1.0, -1.0, -1.0);
//
//// Apply the sharpening filter
//            Mat sharpenedImage = new Mat();
//            Imgproc.filter2D(expandedBarcode, sharpenedImage, expandedBarcode.depth(), sharpeningKernel);
            mats.add(expandedBarcode);
        }
        // Get the bounding box of the largest contour

        // Step 8: Crop the barcode region from the original image

        return mats;
    }

    private void rotate90ACW(Mat inputImage, Mat outputImage) {
        // Create a rotation matrix for 90 degrees counterclockwise
        Point center = new Point(inputImage.cols() / 2, inputImage.rows() / 2);

        // Create the rotation matrix for 90 degrees counterclockwise
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, -90, 1);

        // Calculate the size of the rotated image (swapping width and height)
        Size rotatedSize = new Size(inputImage.rows(), inputImage.cols());

        // Create a new matrix to hold the rotated image
        Mat rotatedImage = new Mat();

        // Perform the rotation and store the result in rotatedImage
        Imgproc.warpAffine(inputImage, rotatedImage, rotationMatrix, rotatedSize, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));
    }
}