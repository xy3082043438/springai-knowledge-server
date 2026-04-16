package com.lamb.springaiknowledgeserver.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

@Service
public class PointClickCaptchaService {

    private static final int TARGET_WIDTH = 400;
    private static final int TARGET_HEIGHT = 200;
    private static final int CHAR_COUNT = 4; // Total characters to show
    private static final int CHECK_COUNT = 3; // Characters user must click
    private static final Random RANDOM = new Random();

    // Dictionaries for random characters
    private static final String CHAR_SOURCE = "人工智能知识增强企业检索归纳智慧平台引擎数据分析安全管理核心架构核心开发智能交互";

    @Data
    @AllArgsConstructor
    public static class PointClickCaptchaInfo {
        private String captchaImg;
        private List<String> checkWords;
        private List<Point> targets;
    }

    @Data
    @AllArgsConstructor
    public static class Point {
        private int x;
        private int y;
    }

    public PointClickCaptchaInfo generate() throws IOException {
        // 1. Pick background
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:captcha/bg/*.png");
        Resource resource = resources[RANDOM.nextInt(resources.length)];
        BufferedImage originImage = ImageIO.read(resource.getInputStream());

        BufferedImage bgImage = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = bgImage.createGraphics();
        g2d.drawImage(originImage, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
        
        // 2. Add noise
        addNoise(g2d);

        // 3. Render random characters
        List<String> selectedChars = new ArrayList<>();
        List<Point> targetPoints = new ArrayList<>();

        for (int i = 0; i < CHAR_COUNT; i++) {
            String c = String.valueOf(CHAR_SOURCE.charAt(RANDOM.nextInt(CHAR_SOURCE.length())));
            selectedChars.add(c);
            
            // Random position (avoiding edges)
            int x = RANDOM.nextInt(TARGET_WIDTH - 60) + 30;
            int y = RANDOM.nextInt(TARGET_HEIGHT - 60) + 30;
            targetPoints.add(new Point(x, y));

            drawRandomChar(g2d, c, x, y);
        }
        g2d.dispose();

        // 4. Select check sequence (e.g. first 3)
        List<String> checkWords = selectedChars.subList(0, CHECK_COUNT);
        List<Point> targets = targetPoints.subList(0, CHECK_COUNT);

        return new PointClickCaptchaInfo(
            toBase64(bgImage, "png"),
            checkWords,
            targets
        );
    }

    private void drawRandomChar(Graphics2D g2d, String c, int x, int y) {
        Font font = new Font("Microsoft YaHei", Font.BOLD, 28 + RANDOM.nextInt(10));
        g2d.setFont(font);
        
        // Shadow
        g2d.setColor(new Color(0, 0, 0, 80));
        g2d.drawString(c, x + 2, y + 2);

        // Main char with random color / rotation
        g2d.setColor(new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
        
        AffineTransform old = g2d.getTransform();
        g2d.rotate(Math.toRadians(RANDOM.nextInt(60) - 30), x, y);
        g2d.drawString(c, x, y);
        g2d.setTransform(old);
    }

    private void addNoise(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(1));
        for (int i = 0; i < 5; i++) {
            g2d.setColor(new Color(RANDOM.nextInt(255), RANDOM.nextInt(255), RANDOM.nextInt(255), 100));
            g2d.drawLine(RANDOM.nextInt(TARGET_WIDTH), RANDOM.nextInt(TARGET_HEIGHT),
                         RANDOM.nextInt(TARGET_WIDTH), RANDOM.nextInt(TARGET_HEIGHT));
        }
    }

    private String toBase64(BufferedImage image, String type) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, type, outputStream);
        return "data:image/" + type + ";base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
