package com.studio.rain.entity.dw;

import java.util.Random;

public class SimplexNoise {
    private final Random random;

    public SimplexNoise(Random random) {
        this.random = random;
    }

    public double noise(double x, double z) {
        double result = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxValue = 0;
        for (int i = 0; i < 8; i++) {
            result += noise2D(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return result / maxValue;
    }

    private double noise2D(double x, double y) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        double xf = x - xi;
        double yf = y - yi;
        double n00 = dotGradient(xi, yi, xf, yf);
        double n10 = dotGradient(xi + 1, yi, xf - 1, yf);
        double n01 = dotGradient(xi, yi + 1, xf, yf - 1);
        double n11 = dotGradient(xi + 1, yi + 1, xf - 1, yf - 1);
        double u = fade(xf);
        double v = fade(yf);
        return lerp(lerp(n00, n10, u), lerp(n01, n11, u), v);
    }

    private double dotGradient(int ix, int iy, double x, double y) {
        random.setSeed(ix * 1235 + iy * 5713);
        double gx = random.nextDouble() * 2 - 1;
        double gy = random.nextDouble() * 2 - 1;
        return gx * x + gy * y;
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}